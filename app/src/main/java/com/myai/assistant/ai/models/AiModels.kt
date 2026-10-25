// File: app/src/main/java/com/myai/assistant/ai/models/AiModels.kt
// AI Data Models — Request/Response + JSON parsing

package com.myai.assistant.ai.models

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

// ═══════════════════════════════════════════════════════
// SYSTEM PROMPT — AI Instructions & Comprehensive Tool Schema
// ═══════════════════════════════════════════════════════

const val SYSTEM_PROMPT = """
Tum ek super-intelligent personal Android AI assistant ho. Tumhara naam "MyAI Assistant" hai.
Tum user ka phone COMPLETELY control kar sakte ho. Har command ko samajh kar turant execute karo.
User Hinglish, Hindi ya English mein baat karega. Tum HAMESHA Hinglish (Hindi + English mix) mein friendly jawab do.

CRITICAL INSTRUCTION:
Tumhara response HAMESHA aur SIRF ek valid JSON object hona chahiye.
Kabhi bhi JSON ke bahar koi extra text, explanation ya markdown chat mat bhejo.

JSON OUTPUT FORMAT (STRICT):
{
  "action": "ACTION_TYPE",
  "target": "target_value",
  "message": "User ko sunane/dikhane wala short aur sweet Hinglish response",
  "data": {}
}

═══════════════════════════════════════
AVAILABLE ACTIONS & SPECIFICATIONS
═══════════════════════════════════════

🔦 TORCH / FLASHLIGHT:
- FLASHLIGHT: target = "on" ya "off"

📞 CALL & COMMUNICATION:
- CALL: target = contact name ya phone number (e.g. "Papa", "9876543210")
- SMS: target = contact/number, data.message = "message content"
- REPLY_CHAT: target = "whatsapp" ya koi chat app name, data.contact = "name", data.message = "message"
- EMAIL: data.to = "email", data.subject = "subject", data.body = "body"

⏰ ALARM & TIMER:
- SET_ALARM: target = "HH:mm", data.hour = 0-23, data.minute = 0-59, data.label = "alarm name"
- SET_TIMER: target = "seconds", data.seconds = duration in seconds, data.label = "timer name"

📶 SYSTEM CONTROLS & SETTINGS:
- WIFI: target = "on" ya "off"
- BLUETOOTH: target = "on" ya "off"
- VOLUME: target = "up", "down", "mute", "unmute", ya "max"
- DND: target = "silent", "vibrate", "normal", "on", ya "off"
- BRIGHTNESS: target = "0-255" ya "auto"
- MOBILE_DATA: target = "settings"
- HOTSPOT: target = "on" ya "off"
- AIRPLANE: target = "settings"
- LOCK: Phone lock/screen off karna (target = "")
- SCREENSHOT: Screen ka screenshot capture karna (target = "")

📱 APP CONTROL:
- OPEN_APP: target = app name (e.g. "whatsapp", "youtube", "camera", "instagram", "spotify", "settings", "chrome", "maps")
- SEARCH: Google web search (target = "search query")
- PLAY_MUSIC: Song/Artist play karna (target = "song or artist name")
- CAMERA: Photo ya video capture (target = "photo" ya "video")

🧭 NAVIGATION & UI AUTOMATION:
- BACK: Back button press karna (target = "")
- HOME: Home screen par jana (target = "")
- RECENTS: Recent apps dikhana (target = "")
- NOTIFICATIONS: Notification panel kholna (target = "")
- QUICK_SETTINGS: Quick settings panel kholna (target = "")
- SCROLL_DOWN: Screen par neeche scroll karna (target = "")
- SCROLL_UP: Screen par upar scroll karna (target = "")
- CLICK: Screen par diye gaye text ya button par tap karna (target = "button text")
- TYPE_TEXT: Screen ke active input field mein text type karna (target = "text to type")
- READ_SCREEN: Screen content read karna

📋 PRODUCTIVITY & INFO:
- CLIPBOARD: target = "read" ya "copy", data.text = "text"
- DEVICE_INFO: Battery, storage ya status batana (target = "battery" ya "device")
- READ_NOTIFICATIONS: Notifications padhna (target = "")
- GENERAL: Knowledge questions, casual conversation, jokes, calculations, or status

═══════════════════════════════════════
FEW-SHOT EXAMPLES (Hinglish, Hindi, English)
═══════════════════════════════════════

User: "Torch on karo" / "टॉर्च चालू करो"
AI: {"action": "FLASHLIGHT", "target": "on", "message": "Torch on kar di hai! 🔦", "data": {}}

User: "Turn off flashlight"
AI: {"action": "FLASHLIGHT", "target": "off", "message": "Torch band kar di hai! 🔦", "data": {}}

User: "Rohan ko WhatsApp par message bhejo ki main 10 min mein pahunch raha hoon"
AI: {"action": "REPLY_CHAT", "target": "whatsapp", "message": "Rohan ko WhatsApp par message bhej raha hoon... 💬", "data": {"contact": "Rohan", "message": "Main 10 min mein pahunch raha hoon"}}

User: "Papa ko phone lagao" / "पापा को कॉल करो"
AI: {"action": "CALL", "target": "Papa", "message": "Papa ko call mila raha hoon 📞", "data": {}}

User: "Call 9876543210"
AI: {"action": "CALL", "target": "9876543210", "message": "Calling 9876543210... 📞", "data": {}}

User: "Mummy ko SMS karo dinner ready hai"
AI: {"action": "SMS", "target": "Mummy", "message": "Mummy ko SMS bhej raha hoon ✉️", "data": {"message": "dinner ready hai"}}

User: "Subah 6:30 baje ka alarm laga do" / "Set alarm for 6:30 AM"
AI: {"action": "SET_ALARM", "target": "06:30", "message": "Subah 6:30 baje ka alarm set ho gaya hai! ⏰", "data": {"hour": 6, "minute": 30, "label": "Morning Alarm"}}

User: "5 minute ka timer laga do" / "Set timer for 5 minutes"
AI: {"action": "SET_TIMER", "target": "300", "message": "5 minute ka timer start ho gaya! ⏱️", "data": {"seconds": 300, "label": "5 min timer"}}

User: "WiFi band kar do" / "Turn off WiFi"
AI: {"action": "WIFI", "target": "off", "message": "WiFi band kar diya hai 📶", "data": {}}

User: "Bluetooth on karo" / "Enable Bluetooth"
AI: {"action": "BLUETOOTH", "target": "on", "message": "Bluetooth chalu kar diya hai 🎧", "data": {}}

User: "Aawaz thodi kam karo" / "Volume down" / "आवाज़ धीमी करो"
AI: {"action": "VOLUME", "target": "down", "message": "Volume kam kar diya 🔉", "data": {}}

User: "Phone silent mode pe daal do" / "Turn on DND"
AI: {"action": "DND", "target": "silent", "message": "Phone ko silent mode par daal diya hai 🔕", "data": {}}

User: "Screenshot lo" / "Take a screenshot"
AI: {"action": "SCREENSHOT", "target": "", "message": "Screenshot capture kar raha hoon 📸", "data": {}}

User: "Phone lock kar do" / "Lock the phone"
AI: {"action": "LOCK", "target": "", "message": "Phone lock kar raha hoon 🔒", "data": {}}

User: "YouTube open karo" / "YouTube kholo"
AI: {"action": "OPEN_APP", "target": "youtube", "message": "YouTube khol raha hoon ▶️", "data": {}}

User: "Peeche jao" / "Go back"
AI: {"action": "BACK", "target": "", "message": "Wapas ja raha hoon ⬅️", "data": {}}

User: "Taj Mahal kisne banwaya tha?"
AI: {"action": "GENERAL", "target": "", "message": "Taj Mahal Mughal Badshah Shah Jahan ne apni begum Mumtaz Mahal ki yaad mein banwaya tha 🏛️", "data": {}}
"""

// ═══════════════════════════════════════════════════════
// OLLAMA REQUEST/RESPONSE MODELS
// ═══════════════════════════════════════════════════════

data class OllamaRequest(
    val model: String = "llama3",
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val format: String = "json",  // Force JSON mode
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

data class OllamaResponse(
    val model: String?,
    val message: OllamaMessage?,
    val done: Boolean = false,
    val error: String? = null,
    @SerializedName("total_duration") val totalDuration: Long? = null
)

// ═══════════════════════════════════════════════════════
// AI PARSED RESPONSE — Unified representation
// ═══════════════════════════════════════════════════════

data class AiParsedResponse(
    val action: String = "GENERAL",
    val target: String? = null,
    val message: String = "",
    val data: Map<String, Any>? = null,
    val rawResponse: String = "",  // Original raw string
    val isSuccess: Boolean = true,
    val error: String? = null,
    val source: AiSource = AiSource.FALLBACK
)

enum class AiSource {
    LITERT,    // Local LiteRT On-Device
    GROQ,      // Groq Cloud (Llama 3.3 70B)
    OLLAMA,    // Local Ollama server
    GEMINI,    // Google Gemini API
    FALLBACK   // Offline regex/rule fallback
}

// ═══════════════════════════════════════════════════════
// ROBUST JSON PARSER — Handles raw JSON, fenced JSON, & nested objects
// ═══════════════════════════════════════════════════════

object AiResponseParser {
    private val gson = Gson()

    /**
     * Converts a raw AI response string into a structured AiParsedResponse.
     * Robustly strips markdown code fences, balanced braces, and handles parsing failures.
     */
    fun parse(rawResponse: String, source: AiSource = AiSource.FALLBACK): AiParsedResponse {
        val trimmed = rawResponse.trim()
        if (trimmed.isEmpty()) {
            return AiParsedResponse(
                action = "GENERAL",
                message = "Kuch samajh nahi aaya, kripya dobara boliye.",
                rawResponse = rawResponse,
                isSuccess = true,
                source = source
            )
        }

        return try {
            val jsonStr = extractJson(trimmed)
            if (jsonStr != null) {
                @Suppress("UNCHECKED_CAST")
                val map = gson.fromJson(jsonStr, Map::class.java) as? Map<String, Any?>

                if (map != null) {
                    val rawAction = map["action"]?.toString()?.trim()?.uppercase() ?: "GENERAL"
                    val target = map["target"]?.toString()?.trim()
                    val message = map["message"]?.toString()?.trim()
                        ?: map["response"]?.toString()?.trim()
                        ?: trimmed
                    @Suppress("UNCHECKED_CAST")
                    val data = map["data"] as? Map<String, Any>

                    AiParsedResponse(
                        action = rawAction,
                        target = target,
                        message = message,
                        data = data,
                        rawResponse = rawResponse,
                        isSuccess = true,
                        source = source
                    )
                } else {
                    fallbackTextResponse(trimmed, rawResponse, source)
                }
            } else {
                fallbackTextResponse(trimmed, rawResponse, source)
            }
        } catch (e: Exception) {
            fallbackTextResponse(trimmed, rawResponse, source)
        }
    }

    private fun fallbackTextResponse(cleanText: String, rawResponse: String, source: AiSource): AiParsedResponse {
        // Strip markdown fences from text if left
        val sanitized = cleanText
            .replace(Regex("^```(?:json)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```$"), "")
            .trim()
            .ifEmpty { "Main samajh nahi paya, dobara bolein." }

        return AiParsedResponse(
            action = "GENERAL",
            target = null,
            message = sanitized,
            data = null,
            rawResponse = rawResponse,
            isSuccess = true,
            source = source
        )
    }

    /**
     * Robust JSON object extractor:
     * 1. Inspects inside ```json ... ``` markdown code fences if present.
     * 2. Scans for balanced braces `{ ... }` taking into account string literals and escaped quotes.
     * 3. Validates candidate using Gson.
     */
    fun extractJson(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // 1. Direct match if already clean JSON
        if (trimmed.startsWith("{") && trimmed.endsWith("}") && isValidJson(trimmed)) {
            return trimmed
        }

        // 2. Check for markdown code fences (```json ... ``` or ``` ... ```)
        val codeFenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
        val fenceMatches = codeFenceRegex.findAll(trimmed)
        for (match in fenceMatches) {
            val contentInsideFence = match.groupValues[1].trim()
            val extracted = findBalancedJsonObject(contentInsideFence)
            if (extracted != null && isValidJson(extracted)) {
                return extracted
            }
        }

        // 3. Scan the entire text for balanced JSON object { ... }
        val balanced = findBalancedJsonObject(trimmed)
        if (balanced != null && isValidJson(balanced)) {
            return balanced
        }

        // 4. Fallback: Substring from first '{' to last '}'
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            val candidate = trimmed.substring(firstBrace, lastBrace + 1).trim()
            if (isValidJson(candidate)) {
                return candidate
            }
        }

        return null
    }

    /**
     * Balanced brace finder that honors JSON string literals and escape characters.
     */
    private fun findBalancedJsonObject(str: String): String? {
        val startIndex = str.indexOf('{')
        if (startIndex == -1) return null

        var depth = 0
        var inString = false
        var consecutiveBackslashes = 0

        for (i in startIndex until str.length) {
            val char = str[i]

            if (char == '\\' && inString) {
                consecutiveBackslashes++
                continue
            }

            if (char == '"' && (consecutiveBackslashes % 2 == 0)) {
                inString = !inString
            }

            consecutiveBackslashes = 0

            if (!inString) {
                if (char == '{') {
                    depth++
                } else if (char == '}') {
                    depth--
                    if (depth == 0) {
                        return str.substring(startIndex, i + 1).trim()
                    }
                }
            }
        }

        return null
    }

    private fun isValidJson(json: String): Boolean {
        return try {
            gson.fromJson(json, Map::class.java) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Builds an explicit error response.
     */
    fun errorResponse(error: String, source: AiSource = AiSource.FALLBACK): AiParsedResponse {
        return AiParsedResponse(
            action = "GENERAL",
            target = null,
            message = "⚠️ $error",
            data = null,
            rawResponse = "",
            isSuccess = false,
            error = error,
            source = source
        )
    }
}

// ═══════════════════════════════════════════════════════
// CHAT HISTORY BUILDER — Helper for Ollama and clients
// ═══════════════════════════════════════════════════════

object ChatHistoryBuilder {
    /**
     * Builds Ollama messages with system prompt + trimmed chat history.
     */
    fun buildOllamaMessages(
        chatHistory: List<Pair<String, String>>,
        userMessage: String,
        maxHistory: Int = 10
    ): List<OllamaMessage> {
        val messages = mutableListOf<OllamaMessage>()

        // System prompt first
        messages.add(OllamaMessage(role = "system", content = SYSTEM_PROMPT.trimIndent()))

        // Recent history
        val recentHistory = chatHistory.takeLast(maxHistory)
        for ((role, content) in recentHistory) {
            val cleanRole = if (role == "assistant" || role == "model") "assistant" else "user"
            messages.add(OllamaMessage(role = cleanRole, content = content))
        }

        // Current message
        messages.add(OllamaMessage(role = "user", content = userMessage))

        return messages
    }
}
