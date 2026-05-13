// File: app/src/main/java/com/myai/assistant/service/MyNotificationListener.kt
// Notification Listener — Sabhi notifications padhho

package com.myai.assistant.service

import android.app.Notification
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
 * Notification Listener — Har notification capture karo
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
