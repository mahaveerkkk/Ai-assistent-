// File: app/src/main/java/com/myai/assistant/data/repository/SettingsRepository.kt
// Settings Repository — Persist settings via SharedPreferences

package com.myai.assistant.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.myai.assistant.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "myai_settings_prefs"

        const val KEY_OLLAMA_URL = "ollama_url"
        const val KEY_OLLAMA_MODEL = "ollama_model"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_USE_OLLAMA = "use_ollama"
        const val KEY_USE_GEMINI = "use_gemini"
        const val KEY_USE_LITERT = "use_litert"
        const val KEY_LITERT_MODEL_PATH = "litert_model_path"
        const val KEY_VOICE_ENABLED = "voice_enabled"
        const val KEY_FLOATING_BUBBLE = "floating_bubble"
        const val KEY_AUTO_START_BOOT = "auto_start_boot"
        const val KEY_BACKGROUND_SERVICE = "background_service"

        private const val DEFAULT_OLLAMA_URL = "http://10.0.2.2:11434"
        private const val DEFAULT_OLLAMA_MODEL = "llama3"
        private const val DEFAULT_LITERT_PATH = "/sdcard/Download/gemma.litertlm"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Ollama URL
    var ollamaUrl: String
        get() = prefs.getString(KEY_OLLAMA_URL, DEFAULT_OLLAMA_URL) ?: DEFAULT_OLLAMA_URL
        set(value) = prefs.edit().putString(KEY_OLLAMA_URL, value).apply()

    // Ollama Model
    var ollamaModel: String
        get() = prefs.getString(KEY_OLLAMA_MODEL, DEFAULT_OLLAMA_MODEL) ?: DEFAULT_OLLAMA_MODEL
        set(value) = prefs.edit().putString(KEY_OLLAMA_MODEL, value).apply()

    // Gemini API Key
    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, BuildConfig.GEMINI_API_KEY) ?: BuildConfig.GEMINI_API_KEY
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()

    // Use Ollama (Local)
    var useOllama: Boolean
        get() = prefs.getBoolean(KEY_USE_OLLAMA, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_OLLAMA, value).apply()

    // Use Gemini (Cloud)
    var useGemini: Boolean
        get() = prefs.getBoolean(KEY_USE_GEMINI, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_GEMINI, value).apply()

    // Voice Enabled
    var voiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()

    // Floating Bubble Overlay
    var floatingBubble: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_BUBBLE, false)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_BUBBLE, value).apply()

    // Auto Start on Boot
    var autoStartBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START_BOOT, value).apply()

    // Background Active Service
    var backgroundService: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_SERVICE, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_SERVICE, value).apply()

    // Use LiteRT (On-Device Local LLM)
    var useLiteRt: Boolean
        get() = prefs.getBoolean(KEY_USE_LITERT, false) // Default false, user turns on in settings
        set(value) = prefs.edit().putBoolean(KEY_USE_LITERT, value).apply()

    // LiteRT model file path on SD Card / Download folder
    var liteRtModelPath: String
        get() = prefs.getString(KEY_LITERT_MODEL_PATH, DEFAULT_LITERT_PATH) ?: DEFAULT_LITERT_PATH
        set(value) = prefs.edit().putString(KEY_LITERT_MODEL_PATH, value).apply()
}
