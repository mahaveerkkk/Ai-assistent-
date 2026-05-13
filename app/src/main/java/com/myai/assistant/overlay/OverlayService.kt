// File: app/src/main/java/com/myai/assistant/overlay/OverlayService.kt
// Overlay Service — Foreground service for floating bubble

package com.myai.assistant.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.myai.assistant.MyAIApp
import com.myai.assistant.ui.MainActivity

/**
 * Overlay Service — Floating bubble ko foreground service se chalao
 * Taaki system isko kill na kare
 */
class OverlayService : Service() {

    private var overlayManager: OverlayManager? = null

    companion object {
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())

        overlayManager = OverlayManager(this).apply {
            onBubbleClick = {
                // App kholo jab bubble pe tap karo
                val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            }
            showBubble()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager?.hideBubble()
        overlayManager = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MyAIApp.CHANNEL_SERVICE)
            .setContentTitle("AI Assistant")
            .setContentText("Floating bubble active hai")
            .setSmallIcon(android.R.drawable.ic_menu_help)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
