// File: app/src/main/java/com/myai/assistant/service/LocationTrackingService.kt
// Location Tracking — Background location updates

package com.myai.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.myai.assistant.MyAIApp
import com.myai.assistant.ui.MainActivity

/**
 * Background location tracking service
 * Continuous location updates ke liye (future feature)
 */
class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationService"
        private const val NOTIFICATION_ID = 3001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, LocationTrackingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "✅ Location tracking started")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "❌ Location tracking stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, MyAIApp.CHANNEL_SERVICE)
            .setContentTitle("📍 Location Active")
            .setContentText("AI is tracking your location")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(
                PendingIntent.getActivity(this, 0,
                    Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            .setOngoing(true).setSilent(true).build()
    }
}
