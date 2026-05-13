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
User ke commands samjho aur HAMESHA ek JSON object mein respond karo.

JSON format STRICTLY follow karo:
{
  "action": "ACTION_TYPE",
  "target": "target_value",
  "message": "user ko dikhane wala jawab",
  "data": {}
}

Available ACTION types:
- GENERAL: Normal baat-cheet, sawal-jawab, knowledge
- CALL: Kisi ko call karna (target = contact name/number)
- SMS: SMS bhejnaa (target = contact, data.message = SMS text)
- OPEN_APP: App kholna (target = app name/package)
- READ_SCREEN: Screen pe kya hai dekhna
- TYPE_TEXT: Kisi app mein text type karna (target = text)
- SEARCH: Kuch search karna (target = search query)
- CAMERA: Photo lena ya camera kholna
- LOCATION: Location batana
- SET_ALARM: Alarm set karna (data.time = time)
- SET_REMINDER: Reminder set karna
- PLAY_MUSIC: Music chalana (target = song/artist)
- VOLUME: Volume badhana/kam karna (target = up/down/mute)
- BRIGHTNESS: Brightness set karna (target = value)
- FLASHLIGHT: Torch on/off (target = on/off)
- WIFI: WiFi on/off (target = on/off)
- BLUETOOTH: Bluetooth on/off (target = on/off)
- REPLY_CHAT: Kisi app mein reply karna (target = app, data.contact, data.message)

Rules:
1. HAMESHA Hindi mein baat karo (Hinglish bhi chalega)
2. JSON ke alawa kuch mat bhejo
3. "message" field mein friendly jawab do
4. Agar action nahi hai toh action = "GENERAL" rakho
5. Chhote aur helpful jawab do
6. Emoji use karo jahan fit ho
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
