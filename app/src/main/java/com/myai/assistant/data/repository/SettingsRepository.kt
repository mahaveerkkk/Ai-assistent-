// File: app/src/main/java/com/myai/assistant/data/repository/SettingsRepository.kt
// Settings Repository — Persist settings via DataStore Preferences

package com.myai.assistant.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.myai.assistant.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
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
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_CHAT_THEME = "chat_theme"
        const val KEY_CONTINUOUS_VOICE = "continuous_voice"

        private const val DEFAULT_OLLAMA_URL = "http://10.0.2.2:11434"
        private const val DEFAULT_OLLAMA_MODEL = "llama3"
        private const val DEFAULT_LITERT_PATH = "/sdcard/Download/gemma.litertlm"
        private const val DEFAULT_CHAT_THEME = "Modern Blue"

        private val PREF_KEY_OLLAMA_URL = stringPreferencesKey(KEY_OLLAMA_URL)
        private val PREF_KEY_OLLAMA_MODEL = stringPreferencesKey(KEY_OLLAMA_MODEL)
        private val PREF_KEY_GEMINI_API_KEY = stringPreferencesKey(KEY_GEMINI_API_KEY)
        private val PREF_KEY_USE_OLLAMA = booleanPreferencesKey(KEY_USE_OLLAMA)
        private val PREF_KEY_USE_GEMINI = booleanPreferencesKey(KEY_USE_GEMINI)
        private val PREF_KEY_USE_LITERT = booleanPreferencesKey(KEY_USE_LITERT)
        private val PREF_KEY_LITERT_MODEL_PATH = stringPreferencesKey(KEY_LITERT_MODEL_PATH)
        private val PREF_KEY_VOICE_ENABLED = booleanPreferencesKey(KEY_VOICE_ENABLED)
        private val PREF_KEY_FLOATING_BUBBLE = booleanPreferencesKey(KEY_FLOATING_BUBBLE)
        private val PREF_KEY_AUTO_START_BOOT = booleanPreferencesKey(KEY_AUTO_START_BOOT)
        private val PREF_KEY_BACKGROUND_SERVICE = booleanPreferencesKey(KEY_BACKGROUND_SERVICE)
        private val PREF_KEY_ONBOARDING_COMPLETED = booleanPreferencesKey(KEY_ONBOARDING_COMPLETED)
        private val PREF_KEY_CHAT_THEME = stringPreferencesKey(KEY_CHAT_THEME)
        private val PREF_KEY_CONTINUOUS_VOICE = booleanPreferencesKey(KEY_CONTINUOUS_VOICE)
    }

    // Onboarding Completed
    var onboardingCompleted: Boolean
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_ONBOARDING_COMPLETED] ?: false }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_ONBOARDING_COMPLETED] = value }
            }
        }

    // Chat Theme
    var chatTheme: String
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_CHAT_THEME] ?: DEFAULT_CHAT_THEME }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_CHAT_THEME] = value }
            }
        }

    // Ollama URL
    var ollamaUrl: String
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_OLLAMA_URL] ?: DEFAULT_OLLAMA_URL }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_OLLAMA_URL] = value }
            }
        }

    // Ollama Model
    var ollamaModel: String
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_OLLAMA_MODEL] ?: DEFAULT_OLLAMA_MODEL }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_OLLAMA_MODEL] = value }
            }
        }

    // Gemini API Key
    var geminiApiKey: String
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_GEMINI_API_KEY] ?: BuildConfig.GEMINI_API_KEY }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_GEMINI_API_KEY] = value }
            }
        }

    // Use Ollama (Local)
    var useOllama: Boolean
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_USE_OLLAMA] ?: true }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_USE_OLLAMA] = value }
            }
        }

    // Use Gemini (Cloud)
    var useGemini: Boolean
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_USE_GEMINI] ?: true }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_USE_GEMINI] = value }
            }
        }

    // Voice Enabled
    var voiceEnabled: Boolean
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_VOICE_ENABLED] ?: true }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_VOICE_ENABLED] = value }
            }
        }

    // Floating Bubble Overlay
    var floatingBubble: Boolean
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_FLOATING_BUBBLE] ?: false }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_FLOATING_BUBBLE] = value }
            }
        }

    // Auto Start on Boot
    var autoStartBoot: Boolean
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_AUTO_START_BOOT] ?: true }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_AUTO_START_BOOT] = value }
            }
        }

    // Background Active Service
    var backgroundService: Boolean
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_BACKGROUND_SERVICE] ?: false }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_BACKGROUND_SERVICE] = value }
            }
        }

    // Use LiteRT (On-Device Local LLM)
    var useLiteRt: Boolean
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_USE_LITERT] ?: false }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_USE_LITERT] = value }
            }
        }

    // LiteRT model file path on SD Card / Download folder
    var liteRtModelPath: String
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_LITERT_MODEL_PATH] ?: DEFAULT_LITERT_PATH }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_LITERT_MODEL_PATH] = value }
            }
        }

    // Continuous Voice Mode
    var continuousVoiceMode: Boolean
        get() = runBlocking {
            dataStore.data.map { it[PREF_KEY_CONTINUOUS_VOICE] ?: false }.first()
        }
        set(value) {
            runBlocking {
                dataStore.edit { it[PREF_KEY_CONTINUOUS_VOICE] = value }
            }
        }
}
