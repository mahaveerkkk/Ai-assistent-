// File: app/src/main/java/com/myai/assistant/service/AssistantForegroundService.kt
// Foreground Service — Background AI assistant + Wake word detection coordination

package com.myai.assistant.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.myai.assistant.MyAIApp
import com.myai.assistant.features.voice.GeminiTTSManager
import com.myai.assistant.features.voice.VoiceManager
import com.myai.assistant.features.voice.WakeWordDetector
import com.myai.assistant.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Assistant Foreground Service
 *
 * Runs continuously in the background to handle:
 * - Low-power wake word detection ("Friday", "Suno", "Hey Assistant")
 * - Seamless handoff: Wake Word → Welcome Prompt ("Yes boss, listening") → Voice Input (STT)
 * - Safe notification state transitions
 * - Crash-free microphone permission and AudioTrack coordination
 */
@AndroidEntryPoint
class AssistantForegroundService : Service() {

    companion object {
        private const val TAG = "AssistantService"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AssistantForegroundService::class.java))
        }
    }

    @Inject lateinit var voiceManager: VoiceManager
    @Inject lateinit var geminiTts: GeminiTTSManager
    @Inject lateinit var wakeWordDetector: WakeWordDetector
    @Inject lateinit var assistantBrain: com.myai.assistant.ai.AssistantBrain

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeTurnJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅ Assistant Service STARTED")

        startForegroundSafely()

        // Initialize voice engines
        voiceManager.initialize(this)
        geminiTts.initialize(this)

        // Setup handlers and wake word loop
        setupVoiceHandlers()
        setupWakeWord()
    }

    private fun startForegroundSafely() {
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasRecordAudio = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasRecordAudio) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground error: ${e.message}, falling back to default", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    /**
     * Wire up VoiceManager callbacks once to prevent duplicate listeners or leaks
     */
    private fun setupVoiceHandlers() {
        voiceManager.onResult = { transcript ->
            Log.d(TAG, "🎤 Voice input received: $transcript")
            updateNotification("🤖 Friday: Soch raha hoon...")

            serviceScope.launch {
                try {
                    assistantBrain.processRequest(transcript, isVoiceOutputEnabled = true)
                } catch (e: Exception) {
                    Log.e(TAG, "AssistantBrain processRequest failed: ${e.message}")
                } finally {
                    // Turn finished — resume wake word detection loop
                    wakeWordDetector.resume()
                    updateNotification("🤖 Friday Active — Say \"Hey Friday\"")
                }
            }
        }

        voiceManager.onError = { error ->
            Log.w(TAG, "VoiceManager error in service: $error")
            wakeWordDetector.resume()
            updateNotification("🤖 Friday Active — Say \"Hey Friday\"")
        }

        voiceManager.onTimeout = {
            Log.d(TAG, "VoiceManager timeout in service, resuming wake word")
            wakeWordDetector.resume()
            updateNotification("🤖 Friday Active — Say \"Hey Friday\"")
        }
    }

    /**
     * Wake Word Detection → Welcome Prompt → Voice Input Coordination
     */
    private fun setupWakeWord() {
        wakeWordDetector.onWakeWordDetected = { detectedWord ->
            Log.d(TAG, "🎯 Wake word detected: \"$detectedWord\"")

            // Cancel any pending turn to prevent overlapping audio
            activeTurnJob?.cancel()

            activeTurnJob = serviceScope.launch {
                try {
                    // 1. Update Notification
                    updateNotification("🎤 Friday: Sun raha hoon...")

                    // 2. Bring MainActivity to foreground
                    bringAppToForeground()

                    // 3. Welcome prompt: suspend until TTS has fully finished speaking
                    geminiTts.speak("Yes boss, listening")

                    // 4. Brief 150ms delay to clear hardware DAC acoustic tail
                    delay(150)

                    // 5. Open microphone for command input
                    updateNotification("🎤 Friday: Bol rahe ho...")
                    voiceManager.startListening()

                } catch (e: CancellationException) {
                    Log.d(TAG, "Wake word sequence cancelled")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in wake word coordination: ${e.message}", e)
                    wakeWordDetector.resume()
                    updateNotification("🤖 Friday Active — Say \"Hey Friday\"")
                }
            }
        }

        wakeWordDetector.onError = { error ->
            Log.w(TAG, "Wake word engine error: $error")
        }

        // Start continuous wake word loop
        wakeWordDetector.start(this)
        Log.d(TAG, "🎤 Wake word detection active")
    }

    private fun bringAppToForeground() {
        try {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("launch_voice", true)
            }
            startActivity(launchIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bring MainActivity to foreground: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY  // Restart automatically if system kills service
    }

    override fun onDestroy() {
        super.onDestroy()
        activeTurnJob?.cancel()
        serviceScope.cancel()
        wakeWordDetector.destroy()
        voiceManager.destroy()
        geminiTts.destroy()
        Log.d(TAG, "❌ Assistant Service STOPPED cleanly")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return buildNotification("🤖 Friday Active — Say \"Hey Friday\"")
    }

    private fun updateNotification(text: String) {
        try {
            val notification = buildNotification(text)
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MyAIApp.CHANNEL_SERVICE)
            .setContentTitle("🤖 Friday Voice Assistant")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
