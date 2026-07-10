// File: app/src/main/java/com/myai/assistant/MyAIApp.kt
// Hilt Application Class — App ka entry point

package com.myai.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.myai.assistant.service.BackupWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MyAIApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // Notification channels create karo (Android 8.0+ ke liye zaroori)
        createNotificationChannels()

        // Schedule periodic backup/cleanup work
        scheduleBackupWork()
    }

    private fun scheduleBackupWork() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "BackupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Sabhi notification channels yahan banao
     * - Assistant Service channel (foreground service ke liye)
     * - Alert channel (important notifications ke liye)
     */
    private fun createNotificationChannels() {
        // Main Service Channel — Background service notification
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "AI Assistant Service",
            NotificationManager.IMPORTANCE_LOW  // Low = silent notification
        ).apply {
            description = "Background mein AI Assistant chalta rehta hai"
            setShowBadge(false)
        }

        // Alert Channel — Important alerts (SMS, calls, etc.)
        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "AI Assistant Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important notifications aur alerts"
            enableVibration(true)
        }

        // Voice Channel — Voice listening indicator
        val voiceChannel = NotificationChannel(
            CHANNEL_VOICE,
            "Voice Listening",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Jab assistant awaaz sun raha hota hai"
            setShowBadge(false)
        }

        // Channels register karo
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(serviceChannel, alertChannel, voiceChannel)
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "ai_service_channel"
        const val CHANNEL_ALERTS = "ai_alerts_channel"
        const val CHANNEL_VOICE = "ai_voice_channel"
    }
}
