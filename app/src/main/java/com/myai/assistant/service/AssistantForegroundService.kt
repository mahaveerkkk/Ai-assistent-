// File: app/src/main/java/com/myai/assistant/service/AssistantForegroundService.kt
// Foreground Service — Background mein AI assistant chalta rahe + Wakeword detection

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
import com.myai.assistant.features.voice.GeminiTTSManager
import com.myai.assistant.features.voice.VoiceManager
import com.myai.assistant.features.voice.WakeWordDetector
import com.myai.assistant.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/**
 * Assistant Foreground Service
 * Background mein chalta rahe:
 * - Wakeword detection ("Hey Assistant" / "Suno")
 * - Voice ready (STT initialized)
 * - TTS ready (Gemini + fallback)
 */
@AndroidEntryPoint
class AssistantForegroundService : Service() {

    companion object {
        private const val TAG = "AssistantService"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AssistantForegroundService::class.java))
        }
    }

    @Inject lateinit var voiceManager: VoiceManager
    @Inject lateinit var geminiTts: GeminiTTSManager
    @Inject lateinit var wakeWordDetector: WakeWordDetector
    @Inject lateinit var assistantBrain: com.myai.assistant.ai.AssistantBrain

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅ Assistant Service STARTED")

        startForeground(NOTIFICATION_ID, createNotification())

        // Voice manager initialize karo
        voiceManager.initialize(this)

        // Gemini TTS initialize karo
        geminiTts.initialize(this)

        // Wakeword detection start karo
        setupWakeWord()
    }

    /**
     * Wakeword detection setup —
     * "Hey Assistant" / "Suno" detect hone pe full listening mode ON
     */
    private fun setupWakeWord() {
        wakeWordDetector.onWakeWordDetected = { detectedWord ->
            Log.d(TAG, "🎯 Wakeword detected: $detectedWord")

            // Notification update karo
            updateNotification("🎤 Friday: Sun raha hoon...")

            // Launch MainActivity to bring the UI to the foreground automatically
            try {
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("launch_voice", true)
                }
                startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch MainActivity on wake word: ${e.message}")
            }

            // Setup results handler
            voiceManager.onResult = { transcript ->
                Log.d(TAG, "🎤 Voice input: $transcript")
                serviceScope.launch {
                    assistantBrain.processRequest(transcript, isVoiceOutputEnabled = true)
                }

                // Wakeword resume karo
                wakeWordDetector.resume()
                updateNotification("🤖 Friday Active — Say \"Hey Friday\"")
            }

            voiceManager.onError = { error ->
                Log.w(TAG, "Voice error in service: $error")
                wakeWordDetector.resume()
                updateNotification("🤖 Friday Active — Say \"Hey Friday\"")
            }

            serviceScope.launch {
                // Speak holographic response
                geminiTts.speak("Yes boss, listening")
                kotlinx.coroutines.delay(1200) // Delay to let TTS finish speaking
                voiceManager.startListening()
            }
        }

        wakeWordDetector.onError = { error ->
            Log.e(TAG, "Wakeword error: $error")
        }

        // Start wakeword loop
        wakeWordDetector.start(this)
        Log.d(TAG, "🎤 Wakeword detection active")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY  // System kill kare toh restart ho
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        wakeWordDetector.destroy()
        voiceManager.destroy()
        geminiTts.destroy()
        Log.d(TAG, "❌ Assistant Service STOPPED")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return buildNotification("🤖 AI Assistant Active — \"Suno\" bolo")
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
            .setContentTitle("🤖 AI Assistant Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_help)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
