// File: app/src/main/java/com/myai/assistant/features/voice/GeminiTTSManager.kt
// Gemini TTS Manager — Natural voice via Gemini Live API (WebSocket)
// Model: gemini-2.5-flash-native-audio — Real-time native audio output
// Fallback: Android built-in TextToSpeech

package com.myai.assistant.features.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import com.myai.assistant.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini TTS Manager — Natural AI Voice via Live API
 *
 * Primary: Gemini Live API WebSocket (gemini-2.5-flash-native-audio)
 *   → Real-time native audio — most natural voice
 *   → PCM 24kHz → AudioTrack se play
 *
 * Fallback: Android TextToSpeech (offline)
 *   → Jab Gemini fail ho (no internet, quota exceed)
 */
@Singleton
class GeminiTTSManager @Inject constructor() {

    companion object {
        private const val TAG = "GeminiTTS"

        // Gemini Live API WebSocket endpoint
        private const val LIVE_API_MODEL = "gemini-2.5-flash-native-audio"
        private const val WS_BASE_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        // Audio config (Gemini Live API output)
        private const val SAMPLE_RATE = 24000       // 24kHz output
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Voice options
        const val VOICE_KORE = "Kore"       // Female voice
        const val VOICE_CHARON = "Charon"    // Male voice
        const val VOICE_PUCK = "Puck"        // Male (soft)
        const val VOICE_AOEDE = "Aoede"      // Female (warm)
        const val VOICE_FENRIR = "Fenrir"    // Male (deep)
    }

    // ═══════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Config
    var voiceName: String = VOICE_KORE
    var apiKey: String = BuildConfig.GEMINI_API_KEY

    // WebSocket connection
    private var webSocket: WebSocket? = null
    private var isSetupDone = false

    // Audio buffer — accumulate chunks until turnComplete
    private val audioChunks = mutableListOf<ByteArray>()
    private var pendingText: String? = null

    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Fallback TTS
    private var fallbackTts: TextToSpeech? = null
    private var isFallbackReady = false

    // AudioTrack for playback
    private var audioTrack: AudioTrack? = null

    // HTTP client for WebSocket
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // WebSocket stays open
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS) // Keep alive
        .build()

    // ═══════════════════════════════════════
    // INITIALIZE
    // ═══════════════════════════════════════
    fun initialize(context: Context) {
        // Android TTS as fallback (offline support)
        fallbackTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = fallbackTts?.setLanguage(Locale("hi", "IN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    fallbackTts?.setLanguage(Locale.ENGLISH)
                }
                isFallbackReady = true
                Log.d(TAG, "✅ Fallback TTS ready")

                fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
                    override fun onDone(utteranceId: String?) { _isSpeaking.value = false }
                    override fun onError(utteranceId: String?) { _isSpeaking.value = false }
                })
            } else {
                Log.e(TAG, "❌ Fallback TTS init failed")
            }
        }
    }

    // ═══════════════════════════════════════
    // SPEAK — Main function
    // ═══════════════════════════════════════

    /**
     * Text bolo — Gemini Live API (native audio) try karo, fail ho toh Android TTS
     */
    suspend fun speak(text: String) {
        if (text.isBlank()) return

        // Clean text — emojis hatao
        val cleanText = text.replace(Regex("[\\p{So}\\p{Cn}]"), "").trim()
        if (cleanText.isBlank()) return

        Log.d(TAG, "🔊 Speaking: ${cleanText.take(80)}...")
        _isSpeaking.value = true

        // Gemini Live API try karo
        if (apiKey.isNotBlank()) {
            try {
                val success = speakWithLiveApi(cleanText)
                if (success) return
            } catch (e: Exception) {
                Log.w(TAG, "Gemini Live API failed: ${e.message}")
            }
        }

        // Fallback: Android TTS
        Log.d(TAG, "↩️ Falling back to Android TTS")
        speakWithFallback(cleanText)
    }

    // ═══════════════════════════════════════
    // GEMINI LIVE API — Native Audio Voice
    // ═══════════════════════════════════════

    /**
     * Gemini Live API WebSocket se native audio generate karo
     * Flow: Connect → Setup → Send Text → Receive Audio Chunks → Play
     */
    private suspend fun speakWithLiveApi(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Audio buffer clear karo
            audioChunks.clear()
            pendingText = text

            val completionDeferred = CompletableDeferred<Boolean>()

            // WebSocket URL with API key
            val wsUrl = "$WS_BASE_URL?key=$apiKey"
            Log.d(TAG, "📡 Connecting to Gemini Live API...")

            val request = Request.Builder()
                .url(wsUrl)
                .build()

            // Close existing connection
            closeWebSocket()

            isSetupDone = false

            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.d(TAG, "✅ WebSocket connected!")
                    // Send setup message immediately
                    sendSetupMessage(ws)
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleServerMessage(text, completionDeferred)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "❌ WebSocket failure: ${t.message}")
                    if (!completionDeferred.isCompleted) {
                        completionDeferred.complete(false)
                    }
                    _isSpeaking.value = false
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code $reason")
                    ws.close(1000, null)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code $reason")
                }
            })

            // Wait for audio completion (max 30 seconds)
            val result = withTimeoutOrNull(30_000L) {
                completionDeferred.await()
            } ?: false

            if (!result) {
                Log.w(TAG, "Live API timed out or failed")
                closeWebSocket()
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "Live API error: ${e.message}", e)
            closeWebSocket()
            false
        }
    }

    /**
     * Setup message bhejo — model, voice, config
     */
    private fun sendSetupMessage(ws: WebSocket) {
        val setupJson = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/$LIVE_API_MODEL")

                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                    })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName)
                            })
                        })
                    })
                })

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Tum ek helpful Hindi/Hinglish assistant ho. " +
                                    "Natural tarike se bolo. Jo text mile woh bolo, apni taraf se extra mat add karo. " +
                                    "Bas given text naturally padhke sunao.")
                        })
                    })
                })
            })
        }

        val sent = ws.send(setupJson.toString())
        Log.d(TAG, "📤 Setup message sent: $sent")
    }

    /**
     * Text content bhejo (after setup is done)
     */
    private fun sendTextContent(ws: WebSocket, text: String) {
        val contentJson = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", text)
                            })
                        })
                    })
                })
                put("turnComplete", true)
            })
        }

        val sent = ws.send(contentJson.toString())
        Log.d(TAG, "📤 Text content sent: $sent — '${text.take(50)}'")
    }

    /**
     * Server se aaye message handle karo
     * - setupComplete → text bhejo
     * - serverContent with audio → buffer mein daalo
     * - turnComplete → play audio
     */
    private fun handleServerMessage(messageText: String, completionDeferred: CompletableDeferred<Boolean>) {
        try {
            val json = JSONObject(messageText)

            // Setup complete → now send text
            if (json.has("setupComplete")) {
                Log.d(TAG, "✅ Setup complete! Sending text...")
                isSetupDone = true
                pendingText?.let { text ->
                    webSocket?.let { ws ->
                        sendTextContent(ws, text)
                    }
                    pendingText = null
                }
                return
            }

            // Server content → audio data
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                // Check for modelTurn with audio parts
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val base64Audio = inlineData.getString("data")
                                val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                                synchronized(audioChunks) {
                                    audioChunks.add(audioBytes)
                                }
                                Log.d(TAG, "🎵 Audio chunk: ${audioBytes.size} bytes (total chunks: ${audioChunks.size})")
                            }
                        }
                    }
                }

                // Turn complete → play all accumulated audio
                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d(TAG, "✅ Turn complete! Playing audio...")

                    // Combine all audio chunks
                    val allAudio = synchronized(audioChunks) {
                        val totalSize = audioChunks.sumOf { it.size }
                        val combined = ByteArray(totalSize)
                        var offset = 0
                        for (chunk in audioChunks) {
                            System.arraycopy(chunk, 0, combined, offset, chunk.size)
                            offset += chunk.size
                        }
                        audioChunks.clear()
                        combined
                    }

                    if (allAudio.isNotEmpty()) {
                        Log.d(TAG, "🔊 Playing ${allAudio.size} bytes of native audio")
                        playPcmAudio(allAudio)
                        completionDeferred.complete(true)
                    } else {
                        Log.w(TAG, "No audio data received")
                        completionDeferred.complete(false)
                    }

                    // Close WebSocket after playing
                    closeWebSocket()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Message parse error: ${e.message}", e)
            if (!completionDeferred.isCompleted) {
                completionDeferred.complete(false)
            }
        }
    }

    // ═══════════════════════════════════════
    // AUDIO PLAYBACK — AudioTrack (PCM)
    // ═══════════════════════════════════════

    /**
     * Raw PCM audio bytes play karo AudioTrack se
     * Sample rate: 24000 Hz, Mono, 16-bit PCM
     */
    private fun playPcmAudio(audioBytes: ByteArray) {
        try {
            // Purana AudioTrack band karo
            stopAudioTrack()

            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
            ).coerceAtLeast(audioBytes.size)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack?.let { track ->
                track.write(audioBytes, 0, audioBytes.size)
                track.setNotificationMarkerPosition(audioBytes.size / 2) // 16-bit = 2 bytes per sample
                track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack?) {
                        _isSpeaking.value = false
                        Log.d(TAG, "🔊 Playback complete")
                    }
                    override fun onPeriodicNotification(track: AudioTrack?) {}
                })
                track.play()
                Log.d(TAG, "🔊 AudioTrack playing ${audioBytes.size} bytes...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack error: ${e.message}", e)
            _isSpeaking.value = false
        }
    }

    private fun stopAudioTrack() {
        try {
            audioTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.stop()
                    it.flush()
                }
                it.release()
            }
            audioTrack = null
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════
    // WEBSOCKET MANAGEMENT
    // ═══════════════════════════════════════

    private fun closeWebSocket() {
        try {
            webSocket?.close(1000, "Done")
            webSocket = null
            isSetupDone = false
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════
    // FALLBACK TTS — Android built-in
    // ═══════════════════════════════════════

    private fun speakWithFallback(text: String) {
        if (!isFallbackReady) {
            _isSpeaking.value = false
            return
        }
        fallbackTts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "utterance_${System.currentTimeMillis()}"
        )
    }

    // ═══════════════════════════════════════
    // CONTROLS
    // ═══════════════════════════════════════

    fun stop() {
        // Stop audio playback
        stopAudioTrack()
        // Close WebSocket
        closeWebSocket()
        // Stop fallback TTS
        fallbackTts?.stop()
        // Clear buffers
        synchronized(audioChunks) { audioChunks.clear() }
        _isSpeaking.value = false
    }

    fun setSpeechRate(rate: Float) {
        fallbackTts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun setPitch(pitch: Float) {
        fallbackTts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    fun updateApiKey(key: String) {
        apiKey = key
    }

    fun updateVoice(voice: String) {
        voiceName = voice
    }

    // ═══════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════

    fun destroy() {
        stop()
        scope.cancel()
        fallbackTts?.shutdown()
        fallbackTts = null
        Log.d(TAG, "GeminiTTS destroyed")
    }
}
