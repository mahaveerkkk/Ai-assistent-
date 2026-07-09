// File: app/src/main/java/com/myai/assistant/ai/models/AiModels.kt
// AI Data Models — Request/Response + JSON parsing

package com.myai.assistant.ai.models

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

// ═══════════════════════════════════════════════════════
// SYSTEM PROMPT — AI ko yeh instructions milte hain
// ═══════════════════════════════════════════════════════

const val SYSTEM_PROMPT = """
Tum ek personal Android AI assistant ho. Tumhara naam "MyAI Assistant" hai.
Tum user ka phone COMPLETELY control kar sakte ho — har command execute karo.
User ke commands samjho aur HAMESHA ek JSON object mein respond karo.

JSON format STRICTLY follow karo:
{
  "action": "ACTION_TYPE",
  "target": "target_value",
  "message": "user ko dikhane wala jawab",
  "data": {}
}

═══════════════════════════════════════
AVAILABLE ACTIONS (Total: 35+)
═══════════════════════════════════════

📞 COMMUNICATION:
- CALL: Call karna (target = contact name ya phone number)
- SMS: SMS bhejnaa (target = contact/number, data.message = text)
- EMAIL: Email compose (data.to, data.subject, data.body)
- REPLY_CHAT: App mein reply (target = app name, data.contact, data.message)

📱 APP CONTROL:
- OPEN_APP: App kholna (target = app name like "whatsapp", "youtube", "settings")
- SEARCH: Google search (target = query)
- CLICK: Screen pe text click karna (target = button/text)
- TYPE_TEXT: Text type karna (target = text to type)
- LONG_CLICK: Long press karna (target = text)

🔧 SYSTEM CONTROLS:
- WIFI: WiFi (target = on/off)
- BLUETOOTH: Bluetooth (target = on/off)
- FLASHLIGHT: Torch (target = on/off)
- VOLUME: Volume (target = up/down/mute/unmute)
- BRIGHTNESS: Brightness (target = 0-255)
- DND: Do Not Disturb (target = silent/vibrate/normal/on/off)
- MOBILE_DATA: Mobile data settings kholna
- AIRPLANE: Airplane mode settings kholna
- HOTSPOT: Hotspot settings kholna
- LOCK: Phone lock karna

🧭 NAVIGATION:
- BACK: Back button
- HOME: Home button
- RECENTS: Recent apps
- NOTIFICATIONS: Notification panel kholna
- QUICK_SETTINGS: Quick settings kholna
- SCROLL_DOWN: Neeche scroll
- SCROLL_UP: Upar scroll
- SWIPE_LEFT: Left swipe
- SWIPE_RIGHT: Right swipe
- SCREENSHOT: Screenshot lena
- POWER_DIALOG: Power menu dikhana
- READ_SCREEN: Screen content padhna (accessibility node tree se)
- OCR_READ: Screen/Image se raw text read karna (ML Kit OCR se)

📸 MEDIA:
- CAMERA: Camera app kholna
- PLAY_MUSIC: Music chalana (target = song/artist/genre)

⏰ PRODUCTIVITY:
- SET_ALARM: Alarm set (data.hour = 0-23, data.minute = 0-59, data.label = name)
- SET_TIMER: Timer set (data.seconds = duration, data.label = name)
- CALENDAR: Calendar event (data.title = event name) ya upcoming events dekhna
- CLIPBOARD: Copy text (data.text = text) ya current clipboard padhna

📍 INFORMATION:
- LOCATION: Current location batana
- DEVICE_INFO: Battery, RAM, storage, device info dikhana
- READ_NOTIFICATIONS: Recent notifications padhna
- GENERAL: Normal baat-cheet, sawal-jawab, knowledge

═══════════════════════════════════════
RULES
═══════════════════════════════════════
1. HAMESHA Hinglish mein baat karo (Hindi + English mix)
2. JSON ke alawa kuch mat bhejo — sirf pure JSON
3. "message" field mein friendly, short jawab do
4. Agar koi action nahi hai toh action = "GENERAL"
5. Emoji use karo jahan fit ho 🎯
6. Agar screen context diya hai toh usse use karke smart decision lo
7. Agar user ne sirf "battery" ya "volume" bola, toh samjho kya karna hai
8. Numbers detect karo: "7 baje alarm" = SET_ALARM with hour=7, minute=0
"""

// ═══════════════════════════════════════════════════════
// OLLAMA REQUEST/RESPONSE MODELS
// ═══════════════════════════════════════════════════════

/**
 * Ollama API ke liye request body
 * POST /api/chat
 */
data class OllamaRequest(
    val model: String = "llama3",
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val format: String = "json",  // JSON output force karo
    val options: OllamaOptions = OllamaOptions()
)

data class OllamaMessage(
    val role: String,    // "system", "user", "assistant"
    val content: String
)

data class OllamaOptions(
    val temperature: Double = 0.7,
    val top_p: Double = 0.9,
    val num_predict: Int = 512
)

/**
 * Ollama API ka response
 */
data class OllamaResponse(
    val model: String?,
    val message: OllamaMessage?,
    val done: Boolean = false,
    val error: String? = null,
    @SerializedName("total_duration") val totalDuration: Long? = null
)

// ═══════════════════════════════════════════════════════
// AI PARSED RESPONSE — Dono clients ka final output
// ═══════════════════════════════════════════════════════

/**
 * AI ka parsed JSON response
 * Yeh ViewModel ko milega
 */
data class AiParsedResponse(
    val action: String = "GENERAL",
    val target: String? = null,
    val message: String = "",
    val data: Map<String, Any>? = null,
    val rawResponse: String = "",  // Original response (debugging ke liye)
    val isSuccess: Boolean = true,
    val error: String? = null,
    val source: AiSource = AiSource.OLLAMA  // Kahan se aaya response
)

enum class AiSource {
    OLLAMA,    // Local Ollama
    GEMINI,    // Google Gemini API
    FALLBACK   // Offline fallback
}

// ═══════════════════════════════════════════════════════
// JSON PARSER — AI response parse karo
// ═══════════════════════════════════════════════════════

object AiResponseParser {
    private val gson = Gson()

    /**
     * Raw AI response string ko AiParsedResponse mein convert karo
     */
    fun parse(rawResponse: String, source: AiSource = AiSource.OLLAMA): AiParsedResponse {
        return try {
            // JSON extract karo (kabhi kabhi AI extra text bhi bhejta hai)
            val jsonStr = extractJson(rawResponse)

            if (jsonStr != null) {
                // JSON parse karo
                val map = gson.fromJson(jsonStr, Map::class.java) as Map<String, Any>

                AiParsedResponse(
                    action = (map["action"] as? String) ?: "GENERAL",
                    target = map["target"] as? String,
                    message = (map["message"] as? String) ?: rawResponse,
                    data = map["data"] as? Map<String, Any>,
                    rawResponse = rawResponse,
                    isSuccess = true,
                    source = source
                )
            } else {
                // JSON nahi mila — plain text response
                AiParsedResponse(
                    action = "GENERAL",
                    message = rawResponse.trim(),
                    rawResponse = rawResponse,
                    isSuccess = true,
                    source = source
                )
            }
        } catch (e: Exception) {
            // Parse fail — plain text return karo
            AiParsedResponse(
                action = "GENERAL",
                message = rawResponse.trim().ifEmpty { "Kuch samajh nahi aaya, dobara boliye." },
                rawResponse = rawResponse,
                isSuccess = true,
                source = source
            )
        }
    }

    /**
     * String mein se JSON object extract karo
     * Handles: pure JSON, JSON with prefix/suffix text, markdown code blocks
     */
    private fun extractJson(text: String): String? {
        val trimmed = text.trim()

        // Direct JSON check
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }

        // Markdown code block mein ho sakta hai ```json ... ```
        val codeBlockRegex = Regex("```(?:json)?\\s*\\n?(\\{.*?})\\s*\\n?```", RegexOption.DOT_MATCHES_ALL)
        codeBlockRegex.find(trimmed)?.let { return it.groupValues[1] }

        // Kahi bhi { ... } dhundo
        val jsonRegex = Regex("\\{[^{}]*(?:\\{[^{}]*}[^{}]*)*}", RegexOption.DOT_MATCHES_ALL)
        jsonRegex.find(trimmed)?.let { return it.value }

        return null
    }

    /**
     * Error response banao
     */
    fun errorResponse(error: String, source: AiSource = AiSource.FALLBACK): AiParsedResponse {
        return AiParsedResponse(
            action = "GENERAL",
            message = "⚠️ $error",
            isSuccess = false,
            error = error,
            source = source
        )
    }
}

// ═══════════════════════════════════════════════════════
// CHAT HISTORY BUILDER — Ollama ke liye messages build
// ═══════════════════════════════════════════════════════

object ChatHistoryBuilder {
    /**
     * Chat history se Ollama messages banao
     * System prompt + recent messages (max 20)
     */
    fun buildOllamaMessages(
        chatHistory: List<Pair<String, String>>,  // (role, content) pairs
        userMessage: String,
        maxHistory: Int = 20
    ): List<OllamaMessage> {
        val messages = mutableListOf<OllamaMessage>()

        // System prompt sabse pehle
        messages.add(OllamaMessage(role = "system", content = SYSTEM_PROMPT.trimIndent()))

        // Recent chat history
        val recentHistory = chatHistory.takeLast(maxHistory)
        for ((role, content) in recentHistory) {
            messages.add(OllamaMessage(role = role, content = content))
        }

        // Current user message
        messages.add(OllamaMessage(role = "user", content = userMessage))

        return messages
    }
}
