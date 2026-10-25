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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import com.myai.assistant.ai.AssistantBrain
import com.myai.assistant.features.voice.VoiceManager
import com.myai.assistant.features.voice.GeminiTTSManager

/**
 * Overlay Service — Floating bubble ko foreground service se chalao
 * Single-tap: Instant voice command over any app
 * Long-press: Open full FRIDAY app
 * Dynamically reacts to Voice, AI Thinking, and Speaking states.
 */
@AndroidEntryPoint
class OverlayService : Service() {

    private var overlayManager: OverlayManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Inject lateinit var assistantBrain: AssistantBrain
    @Inject lateinit var voiceManager: VoiceManager
    @Inject lateinit var geminiTts: GeminiTTSManager

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

        voiceManager.initialize(this)
        geminiTts.initialize(this)

        overlayManager = OverlayManager(this).apply {
            onBubbleClick = {
                // Single Tap: Voice listen toggle on overlay
                if (voiceManager.isListening.value) {
                    voiceManager.stopListening()
                    setBubbleState(OverlayManager.BubbleState.IDLE)
                } else {
                    if (geminiTts.isSpeaking.value) {
                        geminiTts.stop()
                    }
                    setBubbleState(OverlayManager.BubbleState.LISTENING)
                    voiceManager.startListening()
                }
            }

            onBubbleLongClick = {
                // Long press: Open Full App
                val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            }

            showBubble()
        }

        // Real-time synchronization of AssistantBrain thinking state
        serviceScope.launch {
            assistantBrain.isAiThinking.collect { thinking ->
                if (thinking) {
                    overlayManager?.setBubbleState(OverlayManager.BubbleState.THINKING)
                }
            }
        }

        // Real-time synchronization of Gemini TTS speaking state
        serviceScope.launch {
            geminiTts.isSpeaking.collect { speaking ->
                if (speaking) {
                    overlayManager?.setBubbleState(OverlayManager.BubbleState.SPEAKING)
                } else if (!voiceManager.isListening.value && !assistantBrain.isAiThinking.value) {
                    overlayManager?.setBubbleState(OverlayManager.BubbleState.IDLE)
                }
            }
        }

        // Voice callbacks
        voiceManager.onResult = { transcript ->
            overlayManager?.setBubbleState(OverlayManager.BubbleState.THINKING)
            serviceScope.launch {
                try {
                    assistantBrain.processRequest(transcript, isVoiceOutputEnabled = true)
                    overlayManager?.setBubbleState(OverlayManager.BubbleState.SUCCESS)
                    delay(1200)
                } catch (e: Exception) {
                    overlayManager?.setBubbleState(OverlayManager.BubbleState.ERROR)
                    delay(1500)
                } finally {
                    if (!geminiTts.isSpeaking.value) {
                        overlayManager?.setBubbleState(OverlayManager.BubbleState.IDLE)
                    }
                }
            }
        }

        voiceManager.onError = {
            overlayManager?.setBubbleState(OverlayManager.BubbleState.ERROR)
            serviceScope.launch {
                delay(1500)
                if (!geminiTts.isSpeaking.value) {
                    overlayManager?.setBubbleState(OverlayManager.BubbleState.IDLE)
                }
            }
        }

        voiceManager.onTimeout = {
            if (!geminiTts.isSpeaking.value && !assistantBrain.isAiThinking.value) {
                overlayManager?.setBubbleState(OverlayManager.BubbleState.IDLE)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
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
