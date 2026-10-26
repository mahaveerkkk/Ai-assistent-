// File: app/src/main/java/com/myai/assistant/ai/AIClient.kt
// AI Client Manager — Decision Chain: LiteRT -> Groq (ultra-fast) -> Gemini (cloud) -> Offline Regex Fallback

package com.myai.assistant.ai

import android.util.Log
import com.myai.assistant.ai.models.AiParsedResponse
import com.myai.assistant.ai.models.AiResponseParser
import com.myai.assistant.ai.models.AiSource
import com.myai.assistant.data.model.ChatMessage
import com.myai.assistant.data.model.MessageSender
import com.myai.assistant.data.repository.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Client — Main router & brain manager
 *
 * Decision Chain Order:
 * 1. Local LiteRT (if enabled) — On-device, 100% offline, private
 * 2. Groq (Llama 3.3 70B) — Ultra-fast (~150ms cloud inference)
 * 3. Gemini 2.0 Flash — Multi-modal cloud fallback with key rotation
 * 4. Offline Regex Fallback — Instant local pattern matcher when offline
 */
@Singleton
class AIClient @Inject constructor(
    private val localInferenceClient: LocalInferenceClient,
    private val ollamaClient: OllamaClient,
    private val geminiClient: GeminiClient,
    private val groqClient: GroqClient,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "AIClient"
    }

    init {
        // Initialize client configurations from saved settings
        ollamaClient.baseUrl = settingsRepository.ollamaUrl
        ollamaClient.model = settingsRepository.ollamaModel
        val allKeys = settingsRepository.allGeminiApiKeys
        if (allKeys.contains(",")) {
            geminiClient.setApiKeys(allKeys)
        } else {
            geminiClient.updateApiKey(allKeys)
        }
        val savedGroqKey = settingsRepository.groqApiKey
        if (savedGroqKey.isNotBlank()) {
            groqClient.updateApiKey(savedGroqKey)
        }
    }

    // ═══════════════════════════════════════
    // CONFIGURATION FLAGS
    // ═══════════════════════════════════════
    var useGroq: Boolean
        get() = settingsRepository.useGroq
        set(value) { settingsRepository.useGroq = value }

    var useLocalOllama: Boolean
        get() = settingsRepository.useOllama
        set(value) { settingsRepository.useOllama = value }

    var useGeminiFallback: Boolean
        get() = settingsRepository.useGemini
        set(value) { settingsRepository.useGemini = value }

    var useLiteRt: Boolean
        get() = settingsRepository.useLiteRt
        set(value) { settingsRepository.useLiteRt = value }

    // Track if Ollama server is unreachable
    private var ollamaFailed = false

    // Ollama config
    fun setOllamaUrl(url: String) {
        settingsRepository.ollamaUrl = url
        ollamaClient.baseUrl = url
    }

    fun setOllamaModel(model: String) {
        settingsRepository.ollamaModel = model
        ollamaClient.model = model
    }

    // Groq config
    fun setGroqApiKey(key: String) {
        val sanitized = key.trim().removeSurrounding("\"").removeSurrounding("'")
        settingsRepository.groqApiKey = sanitized
        groqClient.updateApiKey(sanitized)
    }

    // Gemini config — supports multiple keys comma-separated for rotation
    fun setGeminiApiKey(key: String) {
        val sanitized = key.trim().removeSurrounding("\"").removeSurrounding("'")
        settingsRepository.geminiApiKey = sanitized
        if (sanitized.contains(",")) {
            geminiClient.setApiKeys(sanitized)
        } else {
            geminiClient.updateApiKey(sanitized)
        }
    }

    /**
     * Main Chat Router
     * Follows the strict priority chain:
     * LiteRT (if enabled) -> Groq (~150ms) -> Gemini (cloud) -> Offline Regex Fallback
     */
    suspend fun sendMessage(
        userMessage: String,
        chatHistory: List<ChatMessage> = emptyList()
    ): AiParsedResponse {
        // Chat history conversion
        val historyPairs = chatHistory
            .filter { !it.isLoading && !it.isError }
            .takeLast(8)
            .map { msg ->
                val role = when (msg.sender) {
                    MessageSender.USER -> "user"
                    MessageSender.AI -> "assistant"
                    MessageSender.SYSTEM -> "system"
                }
                Pair(role, msg.content)
            }

        // 1. Capture screen context concisely (capped to prevent token overflow)
        val activeService = com.myai.assistant.accessibility.MyAccessibilityService.instance
        val screenContext = if (activeService != null) {
            try {
                val elements = activeService.getScreenSnapshot()
                val activeApp = com.myai.assistant.accessibility.MyAccessibilityService.currentApp.value.orEmpty()
                val elementsList = elements
                    .filter { it.isClickable || it.isEditable || it.text.isNotBlank() || it.contentDescription.isNotBlank() }
                    .take(15) // Top 15 interactive elements
                    .joinToString("\n") { element ->
                        val type = element.className.substringAfterLast(".").take(20)
                        val textInfo = if (element.text.isNotBlank()) "text=\"${element.text.take(50)}\"" else ""
                        val descInfo = if (element.contentDescription.isNotBlank()) "desc=\"${element.contentDescription.take(50)}\"" else ""
                        val clickMarker = if (element.isClickable) "[Click]" else ""
                        val editMarker = if (element.isEditable) "[Edit]" else ""
                        "- $type $clickMarker $editMarker $textInfo $descInfo".trim()
                    }

                if (elementsList.isNotBlank()) {
                    "App: ${activeApp.take(40)}\nElements:\n$elementsList"
                } else if (activeApp.isNotBlank()) {
                    "App: ${activeApp.take(40)}"
                } else ""
            } catch (e: Exception) {
                Log.w(TAG, "Screen snapshot error: ${e.message}")
                ""
            }
        } else ""

        // 2. Capture recent notifications concisely (top 3, truncated)
        val notifContext = try {
            val notifs = com.myai.assistant.service.MyNotificationListener.notifications.value
            if (!notifs.isNullOrEmpty()) {
                val notifList = notifs.take(3).joinToString("\n") { n ->
                    val app = n.appName.take(20)
                    val title = n.title.take(30)
                    val text = n.text.take(60)
                    "- [$app] $title: $text"
                }
                "Notifications:\n$notifList"
            } else ""
        } catch (_: Exception) { "" }

        // 3. Compact message payload
        val enrichedMessage = buildString {
            append("User: $userMessage")
            if (screenContext.isNotBlank()) {
                append("\n\n[SCREEN CONTEXT]\n$screenContext")
            }
            if (notifContext.isNotBlank()) {
                append("\n\n[NOTIFICATIONS]\n$notifContext")
            }
        }

        // ═══════════════════════════════════════════════════════
        // STRATEGY 1: Local LiteRT (On-Device inference if enabled)
        // ═══════════════════════════════════════════════════════
        if (useLiteRt) {
            Log.d(TAG, "🧠 [1/4] Trying On-Device LiteRT...")
            try {
                val response = localInferenceClient.chat(enrichedMessage)
                if (response.isSuccess && response.action != "ERROR") {
                    Log.d(TAG, "✅ LiteRT success: ${response.action}")
                    return response
                }
            } catch (e: Exception) {
                Log.w(TAG, "❌ LiteRT failed: ${e.message}")
            }
        }

        var lastCloudError: String? = null

        // ═══════════════════════════════════════════════════════
        // STRATEGY 2: Groq (Ultra-fast Llama 3.3 70B ~150ms)
        // ═══════════════════════════════════════════════════════
        if (useGroq && groqClient.isAvailable()) {
            Log.d(TAG, "⚡ [2/4] Trying Groq (Llama 3.3 70B)...")
            try {
                val groqResponse = groqClient.chat(enrichedMessage, historyPairs)
                if (groqResponse.isSuccess && groqResponse.action != "ERROR") {
                    Log.d(TAG, "✅ Groq success: ${groqResponse.action}")
                    return groqResponse
                }
                Log.w(TAG, "❌ Groq unsuccessful: ${groqResponse.error}")
                lastCloudError = groqResponse.error
            } catch (e: Exception) {
                Log.w(TAG, "❌ Groq exception: ${e.message}")
                lastCloudError = e.message
            }
        }

        // ═══════════════════════════════════════════════════════
        // OPTIONAL: Ollama (if configured on local network)
        // ═══════════════════════════════════════════════════════
        if (useLocalOllama && !ollamaFailed) {
            Log.d(TAG, "🦙 Trying Ollama...")
            try {
                val ollamaResponse = ollamaClient.chat(enrichedMessage, historyPairs)
                if (ollamaResponse.isSuccess) {
                    Log.d(TAG, "✅ Ollama success: ${ollamaResponse.action}")
                    return ollamaResponse
                }
                Log.w(TAG, "❌ Ollama failed: ${ollamaResponse.error}")
                ollamaFailed = true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ollama exception: ${e.message}")
                ollamaFailed = true
            }
        }

        // ═══════════════════════════════════════════════════════
        // STRATEGY 3: Gemini 2.0 Flash (Cloud fallback with key rotation)
        // ═══════════════════════════════════════════════════════
        if (useGeminiFallback && geminiClient.isAvailable()) {
            Log.d(TAG, "♊ [3/4] Trying Gemini...")
            try {
                val geminiResponse = geminiClient.chat(enrichedMessage, historyPairs)
                if (geminiResponse.isSuccess) {
                    Log.d(TAG, "✅ Gemini success: ${geminiResponse.action}")
                    return geminiResponse
                }
                Log.w(TAG, "❌ Gemini unsuccessful: ${geminiResponse.error}")
                lastCloudError = geminiResponse.error
            } catch (e: Exception) {
                Log.e(TAG, "❌ Gemini exception: ${e.message}", e)
                lastCloudError = e.message
            }
        } else {
            Log.w(TAG, "⚠️ Gemini not available (key blank: ${!geminiClient.isAvailable()})")
        }

        // ═══════════════════════════════════════════════════════
        // STRATEGY 4: Offline Regex Fallback (Device control without AI)
        // ═══════════════════════════════════════════════════════
        Log.w(TAG, "⚠️ [4/4] Cloud/Local AI engines unavailable, executing Offline Regex Fallback")
        return offlineFallback(userMessage, lastCloudError)
    }

    /**
     * Agent loop runner — direct prompt execution without double enrichment
     */
    suspend fun sendAgentMessage(agentPrompt: String): AiParsedResponse {
        // 1. LiteRT
        if (useLiteRt) {
            try {
                val response = localInferenceClient.chat(agentPrompt)
                if (response.isSuccess && response.action != "ERROR") return response
            } catch (_: Exception) {}
        }

        // 2. Groq (150ms agent loop)
        if (useGroq && groqClient.isAvailable()) {
            try {
                val response = groqClient.chat(agentPrompt, emptyList())
                if (response.isSuccess && response.action != "ERROR") return response
            } catch (_: Exception) {}
        }

        // 3. Ollama
        if (useLocalOllama && !ollamaFailed) {
            try {
                val response = ollamaClient.chat(agentPrompt, emptyList())
                if (response.isSuccess) return response
                ollamaFailed = true
            } catch (_: Exception) {
                ollamaFailed = true
            }
        }

        // 4. Gemini
        if (useGeminiFallback && geminiClient.isAvailable()) {
            try {
                val response = geminiClient.chat(agentPrompt, emptyList())
                if (response.isSuccess) return response
            } catch (_: Exception) {}
        }

        // 5. Offline fallback
        return offlineFallback(agentPrompt, null)
    }

    /**
     * Offline Regex Fallback — Robust local device control when no AI model is reachable.
     */
    private fun offlineFallback(userMessage: String, lastCloudError: String? = null): AiParsedResponse {
        val trimmed = userMessage.trim()
        val lower = trimmed.lowercase()

        // 1. Torch / Flashlight
        if (lower.containsAny("torch on", "flashlight on", "torch chalu", "flashlight chalu")) {
            return AiParsedResponse(action = "FLASHLIGHT", target = "on", message = "Torch on kar di hai 🔦", source = AiSource.FALLBACK)
        }
        if (lower.containsAny("torch off", "flashlight off", "torch band", "flashlight band")) {
            return AiParsedResponse(action = "FLASHLIGHT", target = "off", message = "Torch band kar di hai 🔦", source = AiSource.FALLBACK)
        }

        // 2. WiFi
        if (lower.containsAny("wifi on", "wi-fi on", "wifi chalu", "turn on wifi")) {
            return AiParsedResponse(action = "WIFI", target = "on", message = "WiFi on kar diya hai 📶", source = AiSource.FALLBACK)
        }
        if (lower.containsAny("wifi off", "wi-fi off", "wifi band", "turn off wifi")) {
            return AiParsedResponse(action = "WIFI", target = "off", message = "WiFi band kar diya hai 📶", source = AiSource.FALLBACK)
        }

        // 3. Bluetooth
        if (lower.containsAny("bluetooth on", "bluetooth chalu", "turn on bluetooth")) {
            return AiParsedResponse(action = "BLUETOOTH", target = "on", message = "Bluetooth on kar diya hai 🎧", source = AiSource.FALLBACK)
        }
        if (lower.containsAny("bluetooth off", "bluetooth band", "turn off bluetooth")) {
            return AiParsedResponse(action = "BLUETOOTH", target = "off", message = "Bluetooth band kar diya hai 🎧", source = AiSource.FALLBACK)
        }

        // 4. Volume
        if (lower.containsAny("volume up", "aawaz badhao", "awaaz badhao", "sound badhao")) {
            return AiParsedResponse(action = "VOLUME", target = "up", message = "Volume badha diya 🔉", source = AiSource.FALLBACK)
        }
        if (lower.containsAny("volume down", "aawaz kam", "awaaz kam", "sound kam")) {
            return AiParsedResponse(action = "VOLUME", target = "down", message = "Volume kam kar diya 🔉", source = AiSource.FALLBACK)
        }
        if (lower.containsAny("mute", "silent", "sound off")) {
            return AiParsedResponse(action = "VOLUME", target = "mute", message = "Phone silent kar diya hai 🔕", source = AiSource.FALLBACK)
        }

        // 5. Screenshot
        if (lower.containsAny("screenshot", "screen shot")) {
            return AiParsedResponse(action = "SCREENSHOT", target = "", message = "Screenshot capture kar raha hoon 📸", source = AiSource.FALLBACK)
        }

        // 6. Lock
        if (lower.containsAny("lock phone", "phone lock", "screen lock", "lock screen")) {
            return AiParsedResponse(action = "LOCK", target = "", message = "Phone lock kar raha hoon 🔒", source = AiSource.FALLBACK)
        }

        // 7. Navigation
        if (lower.containsAny("go back", "peeche jao", "wapas jao", "back jao")) {
            return AiParsedResponse(action = "BACK", target = "", message = "Peeche ja raha hoon ⬅️", source = AiSource.FALLBACK)
        }
        if (lower.containsAny("go home", "home screen", "ghar jao")) {
            return AiParsedResponse(action = "HOME", target = "", message = "Home screen par ja raha hoon 🏠", source = AiSource.FALLBACK)
        }

        // 8. Open App (regex)
        val openAppMatch = Regex("(?:open|kholo|chalao|start)\\s+([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE).find(trimmed)
        if (openAppMatch != null) {
            val app = openAppMatch.groupValues[1].lowercase()
            return AiParsedResponse(action = "OPEN_APP", target = app, message = "$app khol raha hoon 🚀", source = AiSource.FALLBACK)
        }

        // 9. Call (regex)
        val callMatch = Regex("(?:call|phone lagao|dial)\\s+([a-zA-Z0-9\\s]+)", RegexOption.IGNORE_CASE).find(trimmed)
        if (callMatch != null) {
            val contact = callMatch.groupValues[1].trim()
            return AiParsedResponse(action = "CALL", target = contact, message = "$contact ko call mila raha hoon 📞", source = AiSource.FALLBACK)
        }

        // 10. Alarm (regex)
        val alarmMatch = Regex("(?:alarm).*?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)?", RegexOption.IGNORE_CASE).find(trimmed)
        if (alarmMatch != null) {
            var hour = alarmMatch.groupValues[1].toIntOrNull() ?: 7
            val min = alarmMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = alarmMatch.groupValues[3].lowercase()
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            val targetStr = String.format(Locale.US, "%02d:%02d", hour, min)
            return AiParsedResponse(
                action = "SET_ALARM",
                target = targetStr,
                message = "$targetStr ka alarm set kar diya hai ⏰",
                data = mapOf("hour" to hour, "minute" to min, "label" to "Offline Alarm"),
                source = AiSource.FALLBACK
            )
        }

        // 11. Timer (regex)
        val timerMatch = Regex("(?:timer).*?(\\d+)\\s*(minute|second|min|sec)?", RegexOption.IGNORE_CASE).find(trimmed)
        if (timerMatch != null) {
            val num = timerMatch.groupValues[1].toIntOrNull() ?: 60
            val unit = timerMatch.groupValues[2].lowercase()
            val totalSeconds = if (unit.startsWith("min") || unit.isEmpty()) num * 60 else num
            return AiParsedResponse(
                action = "SET_TIMER",
                target = totalSeconds.toString(),
                message = "$num ${if (unit.startsWith("sec")) "second" else "minute"} ka timer shuru kar diya ⏱️",
                data = mapOf("seconds" to totalSeconds, "label" to "Offline Timer"),
                source = AiSource.FALLBACK
            )
        }

        // 12. Time / Date queries
        if (lower.containsAny("time", "samay", "waqt", "kitne baje")) {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            return AiParsedResponse(action = "GENERAL", message = "🕐 Abhi $time baje hain!", source = AiSource.FALLBACK)
        }
        if (lower.containsAny("date", "tarikh", "aaj ka din", "today")) {
            val date = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale("hi", "IN")).format(Date())
            return AiParsedResponse(action = "GENERAL", message = "📅 Aaj ki tarikh: $date", source = AiSource.FALLBACK)
        }

        // 13. Conversational greetings
        val message = when {
            lower.containsAny("hello", "hi", "hey", "namaste", "namaskar") ->
                "Namaste! 🙏 Main MyAI Assistant hoon. Offline mode mein basic phone controls (Torch, Call, Alarm, App Launch) kaam karte hain!"
            lower.containsAny("kaun", "naam", "name", "who are you") ->
                "Main hoon MyAI Assistant! 🤖 Phone ko control karne ke liye hamesha taiyar."
            lower.containsAny("thank", "shukriya", "dhanyavad") ->
                "Arey koi baat nahi, shukriya! 😊🙏"
            !lastCloudError.isNullOrBlank() ->
                "⚠️ AI Error: $lastCloudError\n\n" +
                "Tip: Kripya Settings mein API Key check karein:\n" +
                "• Gemini Key 'AIzaSy...' se start hoti hai\n" +
                "• Groq Key 'gsk_...' se start hoti hai"
            else ->
                "⚠️ Offline Mode: AI connection uplabdh nahi hai.\n\n" +
                "Aap basic commands chala sakte hain jaise 'Torch on', 'Volume up', 'Alarm 7 baje', 'Open WhatsApp' etc."
        }

        return AiParsedResponse(action = "GENERAL", message = message, isSuccess = true, source = AiSource.FALLBACK)
    }

    /**
     * Check which AI services are available
     */
    suspend fun checkAvailability(): Map<String, Boolean> {
        val ollamaAvailable = if (useLocalOllama) {
            try {
                ollamaClient.isServerAvailable()
            } catch (e: Exception) {
                Log.w(TAG, "Ollama check failed: ${e.message}")
                false
            }
        } else false

        val geminiAvailable = useGeminiFallback && geminiClient.isAvailable()
        val groqAvailable = useGroq && groqClient.isAvailable()

        Log.d(TAG, "📊 Availability — Groq: $groqAvailable, Gemini: $geminiAvailable, Ollama: $ollamaAvailable")

        if (ollamaAvailable) ollamaFailed = false

        return mapOf(
            "groq" to groqAvailable,
            "gemini" to geminiAvailable,
            "ollama" to ollamaAvailable
        )
    }

    fun resetOllamaState() {
        ollamaFailed = false
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
