// File: app/src/main/java/com/myai/assistant/service/MyNotificationListener.kt
// Notification Listener — Sabhi notifications padhho + reply karo

package com.myai.assistant.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationInfo(
    val packageName: String,
    val appName: String = "",
    val title: String,
    val text: String,
    val subText: String = "",
    val timestamp: Long,
    val key: String
)

/**
 * Notification Listener — Har notification capture karo + reply karo
 * WhatsApp, Instagram, Telegram sab ki notifications padhh sakta hai
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

        // Store active StatusBarNotifications for reply capability
        private val activeStatusBarNotifications = mutableMapOf<String, StatusBarNotification>()
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
        Log.d(TAG, "❌ Notification Listener DISCONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return

        val info = NotificationInfo(
            packageName = sbn.packageName,
            appName = getAppName(sbn.packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: "",
            timestamp = sbn.postTime,
            key = sbn.key
        )

        if (info.title.isBlank() && info.text.isBlank()) return

        // System notifications filter karo
        if (sbn.packageName == "com.android.systemui") return
        if (sbn.packageName == packageName) return  // Apni app ki notifications skip

        Log.d(TAG, "🔔 [${info.appName}] ${info.title}: ${info.text}")

        _lastNotification.value = info

        // Store SBN for reply
        activeStatusBarNotifications[sbn.key] = sbn

        // List update karo
        val current = _notifications.value.toMutableList()
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
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                // Store for reply capability
                activeStatusBarNotifications[sbn.key] = sbn
                NotificationInfo(
                    packageName = sbn.packageName,
                    appName = getAppName(sbn.packageName),
                    title = title, text = text,
                    timestamp = sbn.postTime, key = sbn.key
                )
            }
            _notifications.value = list
        } catch (e: Exception) {
            Log.w(TAG, "Load notifications failed: ${e.message}")
        }
    }

    /**
     * 💬 Notification se reply karo (WhatsApp, Telegram, etc.)
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
            val notification = sbn.notification
            val actions = notification.actions ?: run {
                Log.w(TAG, "No actions found on notification")
                return false
            }

            // Find action with RemoteInput (reply action)
            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                if (remoteInputs.isNotEmpty()) {
                    // Build reply intent
                    val intent = Intent()
                    val bundle = Bundle()
                    for (remoteInput in remoteInputs) {
                        bundle.putCharSequence(remoteInput.resultKey, replyText)
                    }
                    RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

                    // Send the reply
                    action.actionIntent.send(applicationContext, 0, intent)
                    Log.d(TAG, "💬 Reply sent via notification: $replyText")
                    return true
                }
            }

            Log.w(TAG, "No reply action found in notification")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Reply to notification failed: ${e.message}")
            false
        }
    }

    /**
     * 💬 Reply to the last notification from a specific app
     * @param appPackage Package name (e.g., "com.whatsapp")
     * @param replyText Reply text
     */
    fun replyToApp(appPackage: String, replyText: String): Boolean {
        val matchingKey = activeStatusBarNotifications.entries
            .filter { it.value.packageName.contains(appPackage, ignoreCase = true) }
            .maxByOrNull { it.value.postTime }
            ?.key

        return if (matchingKey != null) {
            replyToNotification(matchingKey, replyText)
        } else {
            Log.w(TAG, "No notification from app: $appPackage")
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
        } catch (_: Exception) { packageName.substringAfterLast(".") }
    }
}
