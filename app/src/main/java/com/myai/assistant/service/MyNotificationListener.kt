// File: app/src/main/java/com/myai/assistant/service/MyNotificationListener.kt
// Notification Listener — Sabhi notifications padhho + reply karo
// Optimized: Quick reply action extraction (WhatsApp, Telegram, SMS), clean unread query API, thread safety

package com.myai.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class NotificationInfo(
    val packageName: String,
    val appName: String = "",
    val title: String,
    val text: String,
    val subText: String = "",
    val timestamp: Long,
    val key: String,
    val isRead: Boolean = false,
    val canReply: Boolean = false
)

/**
 * Quick reply container linking PendingIntent and RemoteInputs
 */
data class QuickReplyAction(
    val pendingIntent: PendingIntent,
    val remoteInputs: Array<RemoteInput>
)

/**
 * Notification Listener — Har notification capture karo + reply karo
 * WhatsApp, Instagram, Telegram, SMS sab ki notifications padhh sakta hai aur quick reply bhej sakta hai
 */
class MyNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifListener"

        private var _instance: MyNotificationListener? = null
        val instance: MyNotificationListener? get() = _instance

        fun isRunning(): Boolean = _instance != null

        // Recent notifications
        private val _notifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
        val notifications: StateFlow<List<NotificationInfo>> = _notifications.asStateFlow()

        // Last notification (for real-time)
        private val _lastNotification = MutableStateFlow<NotificationInfo?>(null)
        val lastNotification: StateFlow<NotificationInfo?> = _lastNotification.asStateFlow()

        // Thread-safe store of active StatusBarNotifications for reply capability
        private val activeStatusBarNotifications = ConcurrentHashMap<String, StatusBarNotification>()

        /**
         * Query recent unread notifications across all apps or for a specific app/package
         *
         * @param appNameOrPackage Optional app name (e.g. "WhatsApp", "Telegram", "SMS") or package name
         * @return List of matching unread notifications
         */
        fun getUnreadNotifications(appNameOrPackage: String? = null): List<NotificationInfo> {
            val list = _notifications.value.filter { !it.isRead }
            return if (appNameOrPackage.isNullOrBlank()) {
                list
            } else {
                list.filter { matchesApp(it, appNameOrPackage) }
            }
        }

        /**
         * Query notifications by app name or package with optional unread filter
         */
        fun getNotificationsByApp(appNameOrPackage: String, unreadOnly: Boolean = false): List<NotificationInfo> {
            if (appNameOrPackage.isBlank()) return emptyList()
            return _notifications.value.filter { info ->
                matchesApp(info, appNameOrPackage) && (!unreadOnly || !info.isRead)
            }
        }

        /**
         * Get the most recent notification for an app
         */
        fun getLastNotification(appNameOrPackage: String? = null): NotificationInfo? {
            return if (appNameOrPackage.isNullOrBlank()) {
                _notifications.value.firstOrNull()
            } else {
                _notifications.value.firstOrNull { matchesApp(it, appNameOrPackage) }
            }
        }

        /**
         * Mark a notification as read by its unique key
         */
        fun markAsRead(key: String): Boolean {
            val current = _notifications.value.toMutableList()
            val index = current.indexOfFirst { it.key == key }
            return if (index != -1) {
                current[index] = current[index].copy(isRead = true)
                _notifications.value = current
                true
            } else {
                false
            }
        }

        /**
         * Mark all notifications (or all notifications for a specific app) as read
         */
        fun markAllAsRead(appNameOrPackage: String? = null) {
            val current = _notifications.value.toMutableList()
            val updated = current.map { info ->
                if (appNameOrPackage.isNullOrBlank() || matchesApp(info, appNameOrPackage)) {
                    info.copy(isRead = true)
                } else {
                    info
                }
            }
            _notifications.value = updated
        }

        /**
         * Helper to match app name or package name with smart aliases
         */
        private fun matchesApp(info: NotificationInfo, query: String): Boolean {
            val q = query.trim().lowercase()
            if (q.isBlank()) return true

            if (info.appName.lowercase().contains(q) || info.packageName.lowercase().contains(q)) {
                return true
            }

            return when (q) {
                "whatsapp" -> info.packageName.contains("whatsapp", ignoreCase = true)
                "telegram" -> info.packageName.contains("telegram", ignoreCase = true)
                "sms", "message", "messages" -> {
                    val p = info.packageName.lowercase()
                    p.contains("messaging") || p.contains("mms") || p.contains("sms") || p.contains("message")
                }
                "instagram" -> info.packageName.contains("instagram", ignoreCase = true)
                "gmail", "email" -> {
                    val p = info.packageName.lowercase()
                    p.contains("android.gm") || p.contains("email") || p.contains("mail")
                }
                else -> false
            }
        }
    }

    // Callback
    var onNewNotification: ((NotificationInfo) -> Unit)? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        _instance = this
        Log.d(TAG, "✅ Notification Listener CONNECTED")

        // Existing notifications load karo
        loadActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _instance = null
        activeStatusBarNotifications.clear()
        Log.d(TAG, "❌ Notification Listener DISCONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return

        // System notifications filter karo
        if (sbn.packageName == "com.android.systemui") return
        if (sbn.packageName == packageName) return // Skip own app notifications

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""

        if (title.isBlank() && text.isBlank()) return

        // Store active SBN for reply capability
        activeStatusBarNotifications[sbn.key] = sbn

        val canReply = extractQuickReplyAction(sbn.notification) != null

        val info = NotificationInfo(
            packageName = sbn.packageName,
            appName = getAppName(sbn.packageName),
            title = title,
            text = text,
            subText = subText,
            timestamp = sbn.postTime,
            key = sbn.key,
            isRead = false,
            canReply = canReply
        )

        Log.d(TAG, "🔔 [${info.appName}] ${info.title}: ${info.text} (canReply=$canReply)")

        _lastNotification.value = info

        // List update karo (replace existing if key already present, keep newest at top)
        val current = _notifications.value.toMutableList()
        current.removeAll { it.key == sbn.key }
        current.add(0, info)
        if (current.size > 50) current.removeAt(current.lastIndex)
        _notifications.value = current

        onNewNotification?.invoke(info)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val current = _notifications.value.toMutableList()
        current.removeAll { it.key == sbn.key }
        _notifications.value = current
        activeStatusBarNotifications.remove(sbn.key)
    }

    /**
     * Active notifications load karo
     */
    private fun loadActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            val list = active.mapNotNull { sbn ->
                val extras = sbn.notification.extras ?: return@mapNotNull null
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
                if (title.isBlank() && text.isBlank()) return@mapNotNull null

                activeStatusBarNotifications[sbn.key] = sbn
                val canReply = extractQuickReplyAction(sbn.notification) != null

                NotificationInfo(
                    packageName = sbn.packageName,
                    appName = getAppName(sbn.packageName),
                    title = title,
                    text = text,
                    subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: "",
                    timestamp = sbn.postTime,
                    key = sbn.key,
                    isRead = false,
                    canReply = canReply
                )
            }
            _notifications.value = list
        } catch (e: Exception) {
            Log.w(TAG, "Load notifications failed: ${e.message}")
        }
    }

    /**
     * Extract PendingIntent and RemoteInput actions for WhatsApp, Telegram, SMS, and other messaging apps.
     * Checks standard actions, Semantic actions, WearableExtender, and CarExtender.
     */
    private fun extractQuickReplyAction(notification: Notification): QuickReplyAction? {
        // Strategy 1: Standard notification actions
        val actions = notification.actions
        if (!actions.isNullOrEmpty()) {
            // Prioritize semantic reply action or action labeled "reply"
            val prioritizedAction = actions.firstOrNull { action ->
                val hasRemoteInputs = !action.remoteInputs.isNullOrEmpty()
                val isSemanticReply = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY
                } else false
                val titleReply = action.title?.toString()?.contains("reply", ignoreCase = true) == true
                hasRemoteInputs && (isSemanticReply || titleReply)
            } ?: actions.firstOrNull { !it.remoteInputs.isNullOrEmpty() }

            if (prioritizedAction?.actionIntent != null && !prioritizedAction.remoteInputs.isNullOrEmpty()) {
                return QuickReplyAction(prioritizedAction.actionIntent, prioritizedAction.remoteInputs)
            }
        }

        // Strategy 2: Check WearableExtender actions (WhatsApp / Telegram companion mode)
        try {
            val wearableExtender = Notification.WearableExtender(notification)
            val wearActions = wearableExtender.actions
            if (!wearActions.isNullOrEmpty()) {
                val wearReplyAction = wearActions.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
                if (wearReplyAction?.actionIntent != null && !wearReplyAction.remoteInputs.isNullOrEmpty()) {
                    return QuickReplyAction(wearReplyAction.actionIntent, wearReplyAction.remoteInputs)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking wearable extender: ${e.message}")
        }

        // Strategy 3: Check CarExtender (Android Messages / SMS / Auto unread conversations)
        try {
            val carExtender = Notification.CarExtender(notification)
            val unreadConv = carExtender.unreadConversation
            if (unreadConv != null && unreadConv.replyPendingIntent != null && unreadConv.remoteInput != null) {
                return QuickReplyAction(
                    unreadConv.replyPendingIntent,
                    arrayOf(unreadConv.remoteInput)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking car extender: ${e.message}")
        }

        return null
    }

    /**
     * 💬 Notification se reply karo (WhatsApp, Telegram, SMS, etc.)
     * @param key Notification key
     * @param replyText Reply message
     * @return true if reply sent successfully
     */
    fun replyToNotification(key: String, replyText: String): Boolean {
        val sbn = activeStatusBarNotifications[key] ?: run {
            Log.w(TAG, "No notification found with key: $key")
            return false
        }

        return try {
            val quickReply = extractQuickReplyAction(sbn.notification) ?: run {
                Log.w(TAG, "No quick reply action available for notification: ${sbn.packageName}")
                return false
            }

            // Build reply intent with RemoteInput bundles
            val intent = Intent()
            val bundle = Bundle()
            for (remoteInput in quickReply.remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(quickReply.remoteInputs, intent, bundle)

            // Send the reply via PendingIntent
            quickReply.pendingIntent.send(applicationContext, 0, intent)
            Log.d(TAG, "💬 Reply sent via notification to [${sbn.packageName}]: $replyText")

            // Automatically mark as read once replied
            markAsRead(key)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Reply to notification failed: ${e.message}", e)
            false
        }
    }

    /**
     * 💬 Reply to the last notification from a specific app
     * @param appPackage Package or app name (e.g., "com.whatsapp", "WhatsApp", "Telegram")
     * @param replyText Reply text
     */
    fun replyToApp(appPackage: String, replyText: String): Boolean {
        val matchingKey = activeStatusBarNotifications.entries
            .filter { matchesApp(NotificationInfo(it.value.packageName, getAppName(it.value.packageName), "", "", "", it.value.postTime, it.key), appPackage) }
            .maxByOrNull { it.value.postTime }
            ?.key

        return if (matchingKey != null) {
            replyToNotification(matchingKey, replyText)
        } else {
            Log.w(TAG, "No active notification found matching app: $appPackage")
            false
        }
    }

    /**
     * Notification dismiss karo
     */
    fun dismissNotification(key: String) {
        try { cancelNotification(key) } catch (_: Exception) {}
    }

    /**
     * Sabhi notifications dismiss karo
     */
    fun dismissAll() {
        try { cancelAllNotifications() } catch (_: Exception) {}
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast(".")
        }
    }
}
