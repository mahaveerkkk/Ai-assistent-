// File: app/src/main/java/com/myai/assistant/ai/AIClient.kt
// AI Client Manager — Ollama (primary) + Gemini (fallback) + Offline fallback
// FIXED: Better error handling, logging, and Gemini priority when Ollama fails

package com.myai.assistant.ai

import android.util.Log
import com.myai.assistant.ai.models.*
import com.myai.assistant.data.model.ChatMessage
import com.myai.assistant.data.model.MessageSender
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Client — Main brain manager
 *
 * Priority order:
 * 1. Ollama (local) — Fast, private, free
 * 2. Gemini (cloud) — Fallback jab Ollama na chale
 * 3. Offline — Basic responses jab kuch na chale
 */
@Singleton
class AIClient @Inject constructor(
    private val ollamaClient: OllamaClient,
    private val geminiClient: GeminiClient
) {
    companion object {
        private const val TAG = "AIClient"
    }

    // ═══════════════════════════════════════
    // CONFIG — Runtime pe change ho sakta hai
    // ═══════════════════════════════════════
    var useLocalOllama: Boolean = true       // Primary: Ollama
    var useGeminiFallback: Boolean = true    // Fallback: Gemini

    // Track which service is actually working
    private var ollamaFailed = false

    // Ollama config
    fun setOllamaUrl(url: String) { ollamaClient.baseUrl = url }
    fun setOllamaModel(model: String) { ollamaClient.model = model }

    // Gemini config
    fun setGeminiApiKey(key: String) { geminiClient.updateApiKey(key) }

    /**
     * AI se baat karo — Main function
     * FIXED: Better error propagation, Gemini actually tries now
     */
    suspend fun sendMessage(
        userMessage: String,
        chatHistory: List<ChatMessage> = emptyList()
    ): AiParsedResponse {
        // Chat history ko (role, content) pairs mein convert karo
        val historyPairs = chatHistory
            .filter { !it.isLoading && !it.isError }
            .takeLast(10) // Keep last 10 for context (less = fewer issues)
            .map { msg ->
                val role = when (msg.sender) {
                    MessageSender.USER -> "user"
                    MessageSender.AI -> "assistant"
                    MessageSender.SYSTEM -> "system"
                }
                Pair(role, msg.content)
            }

        // Strategy 1: Ollama (local) — skip if previously failed (retry every 5th call)
        if (useLocalOllama && !ollamaFailed) {
            Log.d(TAG, "🧠 Trying Ollama...")
            try {
                val ollamaResponse = ollamaClient.chat(userMessage, historyPairs)

                if (ollamaResponse.isSuccess) {
                    Log.d(TAG, "✅ Ollama success: ${ollamaResponse.action}")
                    return ollamaResponse
                }
                Log.w(TAG, "❌ Ollama failed: ${ollamaResponse.error}")
                ollamaFailed = true // Don't retry Ollama until reset
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ollama exception: ${e.message}")
                ollamaFailed = true
            }
        }

        // Strategy 2: Gemini (cloud)
        if (useGeminiFallback && geminiClient.isAvailable()) {
            Log.d(TAG, "🧠 Trying Gemini...")
            try {
                val geminiResponse = geminiClient.chat(userMessage, historyPairs)

                if (geminiResponse.isSuccess) {
                    Log.d(TAG, "✅ Gemini success: ${geminiResponse.action}")
                    return geminiResponse
                }
                Log.w(TAG, "❌ Gemini failed: ${geminiResponse.error}")
                // Return Gemini error message instead of going to offline
                // So user can see WHAT went wrong
                return geminiResponse
            } catch (e: Exception) {
                Log.e(TAG, "❌ Gemini exception: ${e.message}", e)
                return AiResponseParser.errorResponse(
                    "Gemini error: ${e.message}",
                    AiSource.GEMINI
                )
            }
        } else {
            Log.w(TAG, "⚠️ Gemini not available — key blank: ${!geminiClient.isAvailable()}")
        }

        // Strategy 3: Offline fallback
        Log.w(TAG, "⚠️ Both AI services failed, using offline fallback")
        return offlineFallback(userMessage)
    }

    /**
     * Offline fallback — basic responses jab koi AI na chale
     */
    private fun offlineFallback(userMessage: String): AiParsedResponse {
        val lower = userMessage.lowercase()
        val message = when {
            lower.containsAny("hello", "hi", "hey", "namaste", "namaskar") ->
                "Namaste! 🙏 Main offline mode mein hoon. Ollama server start karo ya Gemini API key daalo."
            lower.containsAny("call", "phone", "dial") ->
                "📞 Call karne ke liye mujhe AI brain chahiye. Abhi offline hoon."
            lower.containsAny("sms", "message", "msg") ->
                "💬 SMS ke liye AI brain connect karo pehle."
            lower.containsAny("time", "samay", "waqt", "baje") -> {
                val time = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date())
                "🕐 Abhi $time baje hain!"
            }
            lower.containsAny("date", "tarikh", "din") -> {
                val date = java.text.SimpleDateFormat("dd MMMM yyyy, EEEE", java.util.Locale("hi", "IN"))
                    .format(java.util.Date())
                "📅 Aaj ki tarikh: $date"
            }
            lower.containsAny("kaun", "naam", "name", "who") ->
                "Main hoon MyAI Assistant! 🤖 Abhi offline mode mein hoon."
            lower.containsAny("thank", "shukriya", "dhanyavad") ->
                "Arey koi baat nahi! 😊🙏"
            else ->
                "⚠️ Main abhi offline hoon. AI brain connect nahi hai.\n\n" +
                "Fix karne ke liye:\n" +
                "1️⃣ Ollama server start karo: `ollama serve`\n" +
                "2️⃣ Ya Settings mein Gemini API key daalo"
        }

        return AiParsedResponse(
            action = "GENERAL",
            message = message,
            isSuccess = true,
            source = AiSource.FALLBACK
        )
    }

    /**
     * Check which AI services are available
     */
    suspend fun checkAvailability(): Map<String, Boolean> {
        val ollamaAvailable = try {
            ollamaClient.isServerAvailable()
        } catch (e: Exception) {
            Log.w(TAG, "Ollama check failed: ${e.message}")
            false
        }

        val geminiAvailable = geminiClient.isAvailable()

        Log.d(TAG, "📊 Availability — Ollama: $ollamaAvailable, Gemini: $geminiAvailable")

        // Reset ollamaFailed if server is now available
        if (ollamaAvailable) ollamaFailed = false

        return mapOf(
            "ollama" to ollamaAvailable,
            "gemini" to geminiAvailable
        )
    }

    /**
     * Reset Ollama failed state (for retry from Settings)
     */
    fun resetOllamaState() {
        ollamaFailed = false
    }

    // Extension function for clean keyword matching
    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
