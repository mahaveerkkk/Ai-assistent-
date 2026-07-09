// File: app/src/main/java/com/myai/assistant/viewmodel/AssistantViewModel.kt
// Assistant ViewModel — Full features: AI + Voice + Contacts + SMS + Accessibility

package com.myai.assistant.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myai.assistant.accessibility.MyAccessibilityService
import com.myai.assistant.ai.AIClient
import com.myai.assistant.ai.models.AiParsedResponse
import com.myai.assistant.data.model.ChatMessage
import com.myai.assistant.data.model.MessageSender
import com.myai.assistant.data.model.MessageType
import com.myai.assistant.data.repository.ChatRepository
import com.myai.assistant.features.contacts.ContactsHelper
import com.myai.assistant.features.device.DeviceInfoManager
import com.myai.assistant.features.device.AlarmHelper
import com.myai.assistant.features.device.CalendarHelper
import com.myai.assistant.features.device.ClipboardHelper
import com.myai.assistant.features.camera.CameraManager
import com.myai.assistant.features.location.LocationHelper
import com.myai.assistant.features.media.MediaHelper
import com.myai.assistant.features.messages.SmsHelper
import com.myai.assistant.features.voice.GeminiTTSManager
import com.myai.assistant.features.voice.VoiceManager
import com.myai.assistant.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isAiThinking: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isVoiceEnabled: Boolean = true,
    val aiSource: String = "offline",  // ollama, gemini, fallback
    val error: String? = null
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
    private val chatRepository: ChatRepository,
    private val aiClient: AIClient,
    private val voiceManager: VoiceManager,
    private val geminiTts: GeminiTTSManager,
    private val contactsHelper: ContactsHelper,
    private val smsHelper: SmsHelper,
    private val locationHelper: LocationHelper,
    private val deviceInfoManager: DeviceInfoManager,
    private val alarmHelper: AlarmHelper,
    private val mediaHelper: MediaHelper,
    private val calendarHelper: CalendarHelper,
    private val clipboardHelper: ClipboardHelper,
    private val cameraManager: CameraManager,
    val settings: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "AssistantVM"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Initialize voice settings from repository
        _uiState.update { it.copy(isVoiceEnabled = settings.voiceEnabled) }

        // Messages load karo
        viewModelScope.launch {
            chatRepository.getAllMessages().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }

        // Welcome message + AI availability check
        viewModelScope.launch {
            delay(500)
            if (_uiState.value.messages.isEmpty()) {
                sendWelcomeMessage()
            }
            // Check AI availability — retry if offline
            checkAiAvailability()
            if (_uiState.value.aiSource == "offline") {
                delay(2000) // Wait 2s and retry
                Log.d(TAG, "Retrying AI availability check...")
                checkAiAvailability()
            }
            Log.d(TAG, "AI source: ${_uiState.value.aiSource}")
        }

        // Voice Manager initialize
        initializeVoice()
    }

    // ═══════════════════════════════════════
    // VOICE SETUP
    // ═══════════════════════════════════════

    private fun initializeVoice() {
        // STT initialize
        voiceManager.initialize(appContext)

        // Gemini TTS initialize (with Android TTS fallback)
        geminiTts.initialize(appContext)

        // Voice result → message bhejo
        voiceManager.onResult = { transcript ->
            sendVoiceMessage(transcript)
        }

        voiceManager.onPartialResult = { partial ->
            _uiState.update { it.copy(inputText = partial) }
        }

        voiceManager.onError = { error ->
            _uiState.update { it.copy(isListening = false, error = error) }
        }

        // Speaking state track karo (Gemini TTS)
        viewModelScope.launch {
            geminiTts.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
            }
        }
    }

    // ═══════════════════════════════════════
    // USER ACTIONS
    // ═══════════════════════════════════════

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "", isAiThinking = true) }
            chatRepository.sendUserMessage(text)
            processWithAi(text)
        }
    }

    fun sendVoiceMessage(transcript: String) {
        if (transcript.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "", isAiThinking = true, isListening = false) }
            chatRepository.sendUserMessage(transcript, MessageType.VOICE)
            processWithAi(transcript)
        }
    }

    fun toggleListening() {
        val currentlyListening = _uiState.value.isListening
        if (currentlyListening) {
            voiceManager.stopListening()
            _uiState.update { it.copy(isListening = false) }
        } else {
            voiceManager.startListening()
            _uiState.update { it.copy(isListening = true) }
        }
    }

    fun stopListening() {
        voiceManager.stopListening()
        _uiState.update { it.copy(isListening = false) }
    }

    fun toggleVoiceOutput() {
        val newState = !_uiState.value.isVoiceEnabled
        settings.voiceEnabled = newState
        _uiState.update { it.copy(isVoiceEnabled = newState) }
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.clearAll()
            sendWelcomeMessage()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // ═══════════════════════════════════════
    // AI CONFIG
    // ═══════════════════════════════════════

    fun setOllamaUrl(url: String) = aiClient.setOllamaUrl(url)
    fun setOllamaModel(model: String) = aiClient.setOllamaModel(model)
    fun setGeminiApiKey(key: String) = aiClient.setGeminiApiKey(key)

    fun setUseOllama(use: Boolean) { aiClient.useLocalOllama = use }
    fun setUseGemini(use: Boolean) { aiClient.useGeminiFallback = use }

    // ═══════════════════════════════════════
    // AI PROCESSING
    // ═══════════════════════════════════════

    private suspend fun processWithAi(userMessage: String) {
        try {
            // Check for zero-latency local shortcuts first
            val shortcutResponse = tryLocalShortcutParser(userMessage)
            if (shortcutResponse != null) {
                Log.d(TAG, "⚡ Zero-Latency Shortcut triggered: ${shortcutResponse.action}")
                
                chatRepository.saveAiResponse(content = shortcutResponse.message)
                _uiState.update {
                    it.copy(isAiThinking = false, aiSource = "shortcut")
                }

                // Voice output if enabled
                if (_uiState.value.isVoiceEnabled && shortcutResponse.message.isNotBlank()) {
                    geminiTts.speak(shortcutResponse.message)
                }

                // Execute instantly
                executeAction(shortcutResponse)
                return
            }

            val loadingId = chatRepository.insertLoadingMessage()

            val currentMessages = _uiState.value.messages
            val response: AiParsedResponse = aiClient.sendMessage(
                userMessage = userMessage,
                chatHistory = currentMessages
            )

            chatRepository.replaceLoadingWithResponse(
                loadingId = loadingId,
                content = response.message,
                actionType = if (response.action != "GENERAL") response.action else null,
                actionData = response.target
            )

            _uiState.update {
                it.copy(isAiThinking = false, aiSource = response.source.name.lowercase())
            }

            // Voice output (agar enabled hai) — Gemini TTS
            if (_uiState.value.isVoiceEnabled && response.message.isNotBlank()) {
                geminiTts.speak(response.message)
            }

            // Action execute karo
            if (response.action != "GENERAL" && response.isSuccess) {
                executeAction(response)
            }

        } catch (e: Exception) {
            _uiState.update { it.copy(isAiThinking = false, error = "Error: ${e.message}") }
            chatRepository.saveErrorMessage("⚠️ ${e.message}")
        }
    }

    /**
     * ⚡ Zero-Latency Shortcut Parser
     * Bypasses the LLM completely for common commands
     */
    private fun tryLocalShortcutParser(message: String): AiParsedResponse? {
        val lower = message.trim().lowercase()
        
        // 1. FLASHLIGHT
        if (lower.containsAny("flashlight on", "torch on", "flashlight kholo", "torch chalao")) {
            return AiParsedResponse(action = "FLASHLIGHT", target = "on", message = "🔦 Flashlight turning ON...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }
        if (lower.containsAny("flashlight off", "torch off", "flashlight band", "torch band")) {
            return AiParsedResponse(action = "FLASHLIGHT", target = "off", message = "🔦 Flashlight turning OFF...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }

        // 2. LOCK PHONE
        if (lower.containsAny("lock screen", "lock phone", "screen lock", "lock karo")) {
            return AiParsedResponse(action = "LOCK", target = null, message = "🔒 Locking device...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }

        // 3. WIFI
        if (lower.containsAny("wifi on", "wifi open", "wifi kholo", "wifi chalao")) {
            return AiParsedResponse(action = "WIFI", target = "on", message = "📡 Opening WiFi...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }
        if (lower.containsAny("wifi off", "wifi close", "wifi band")) {
            return AiParsedResponse(action = "WIFI", target = "off", message = "📡 Closing WiFi...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }

        // 4. BLUETOOTH
        if (lower.containsAny("bluetooth on", "bluetooth chalao", "bluetooth open")) {
            return AiParsedResponse(action = "BLUETOOTH", target = "on", message = "📶 Bluetooth turning ON...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }
        if (lower.containsAny("bluetooth off", "bluetooth band")) {
            return AiParsedResponse(action = "BLUETOOTH", target = "off", message = "📶 Bluetooth turning OFF...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }

        // 5. VOLUME
        if (lower.containsAny("volume up", "volume badhao", " आवाज बढ़ाओ")) {
            return AiParsedResponse(action = "VOLUME", target = "up", message = "🔊 Raising volume...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }
        if (lower.containsAny("volume down", "volume kam", "आवाज कम करो")) {
            return AiParsedResponse(action = "VOLUME", target = "down", message = "🔉 Lowering volume...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }
        if (lower.containsAny("mute", "silent mode", "volume zero")) {
            return AiParsedResponse(action = "VOLUME", target = "mute", message = "🔇 Muting volume...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }

        // 6. DEVICE INFO
        if (lower.containsAny("battery", "storage info", "ram info", "device info", "system info")) {
            return AiParsedResponse(action = "DEVICE_INFO", target = null, message = "📱 Fetching device details...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }

        // 7. SCREENSHOT
        if (lower.containsAny("take screenshot", "screenshot lo", "screenshot")) {
            return AiParsedResponse(action = "SCREENSHOT", target = null, message = "📷 Capturing screen...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }

        // 8. CAMERA
        if (lower.containsAny("open camera", "camera kholo", "photo lo")) {
            return AiParsedResponse(action = "CAMERA", target = null, message = "📸 Opening Camera...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }

        // 9. DND
        if (lower.containsAny("dnd on", "dnd activation", "dnd close")) {
            return AiParsedResponse(action = "DND", target = "on", message = "🔇 Activating Do Not Disturb...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }
        if (lower.containsAny("dnd off", "dnd deactivation")) {
            return AiParsedResponse(action = "DND", target = "off", message = "🔔 Deactivating Do Not Disturb...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
        }

        // 10. APP OPENING (Regex matched)
        val openAppRegex = Regex("(open|kholo|chalao|launch)\\s+([a-zA-Z0-9\\s]+)")
        val matchResult = openAppRegex.find(lower)
        if (matchResult != null) {
            val appName = matchResult.groupValues[2].trim()
            if (appName.isNotBlank() && !appName.containsAny("wifi", "bluetooth", "camera", "flashlight")) {
                return AiParsedResponse(action = "OPEN_APP", target = appName, message = "🚀 Opening $appName...", isSuccess = true, source = com.myai.assistant.ai.models.AiSource.FALLBACK)
            }
        }

        return null
    }

    // Helper extension
    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }

    /**
     * AI action execute karo — FULL FEATURES
     */
    private suspend fun executeAction(response: AiParsedResponse) {
        val action = response.action.uppercase()
        val target = response.target

        when (action) {
            // 📞 CALL
            "CALL" -> {
                if (target != null) {
                    val isNumber = target.matches(Regex("^[+]*[0-9\\s-]{5,15}$"))
                    if (isNumber) {
                        contactsHelper.makeCall(appContext, target)
                    } else {
                        val success = contactsHelper.callByName(appContext, target)
                        if (!success) contactsHelper.makeCall(appContext, target)
                    }
                }
            }

            // 💬 SMS
            "SMS" -> {
                if (target != null) {
                    val isNumber = target.matches(Regex("^[+]*[0-9\\s-]{5,15}$"))
                    val destination = if (isNumber) target else contactsHelper.findContact(appContext, target)?.phoneNumber
                    val message = response.data?.get("message")?.toString() ?: ""
                    if (destination != null && message.isNotBlank()) {
                        val success = smsHelper.sendSms(appContext, destination, message)
                        if (success) {
                            chatRepository.saveSystemMessage("✅ SMS sent to $target")
                        } else {
                            chatRepository.saveSystemMessage("❌ SMS failed (check permissions)")
                        }
                    } else {
                        chatRepository.saveSystemMessage("❌ SMS contact/number not found or message empty")
                    }
                }
            }

            // 📱 DEVICE INFO
            "DEVICE_INFO" -> {
                val summary = deviceInfoManager.getFullSummary()
                chatRepository.saveAiResponse(content = summary)
            }

            // 📍 LOCATION
            "LOCATION" -> {
                val location = locationHelper.getCurrentLocation(appContext)
                if (location != null) {
                    chatRepository.saveAiResponse(
                        content = "📍 Aapki location:\n${location.address}\n\n" +
                                "Area: ${location.area}\nCity: ${location.city}\n" +
                                "Coordinates: ${location.latitude}, ${location.longitude}"
                    )
                }
            }

            // ⏰ SET ALARM
            "SET_ALARM" -> {
                val hour = response.data?.get("hour")?.toString()?.toIntOrNull() ?: 7
                val minute = response.data?.get("minute")?.toString()?.toIntOrNull() ?: 0
                val label = response.data?.get("label")?.toString() ?: "MyAI Alarm"
                val success = alarmHelper.setAlarm(hour, minute, label)
                if (success) chatRepository.saveSystemMessage("✅ Alarm set for $hour:${String.format("%02d", minute)}")
            }

            // ⏱️ SET TIMER
            "SET_TIMER" -> {
                val seconds = response.data?.get("seconds")?.toString()?.toIntOrNull() ?: 60
                val label = response.data?.get("label")?.toString() ?: "MyAI Timer"
                val success = alarmHelper.setTimer(seconds, label)
                if (success) chatRepository.saveSystemMessage("✅ Timer set for ${seconds}s")
            }

            // 🎵 PLAY MUSIC
            "PLAY_MUSIC" -> {
                if (target != null) {
                    val success = mediaHelper.playMusic(target)
                    if (success) chatRepository.saveSystemMessage("🎵 Playing: $target")
                    else chatRepository.saveSystemMessage("❌ Music play failed")
                }
            }

            // 📧 EMAIL
            "EMAIL" -> {
                val to = response.data?.get("to")?.toString() ?: ""
                val subject = response.data?.get("subject")?.toString() ?: ""
                val body = response.data?.get("body")?.toString() ?: ""
                mediaHelper.composeEmail(to, subject, body)
                chatRepository.saveSystemMessage("📧 Email compose opened")
            }

            // 📅 CALENDAR
            "CALENDAR" -> {
                val eventTitle = response.data?.get("title")?.toString()
                if (eventTitle != null) {
                    calendarHelper.createEvent(title = eventTitle)
                    chatRepository.saveSystemMessage("📅 Event created: $eventTitle")
                } else {
                    // Show upcoming events
                    val events = calendarHelper.getUpcomingEvents()
                    if (events.isNotEmpty()) {
                        val list = events.take(5).joinToString("\n") { "• ${it.title}" }
                        chatRepository.saveAiResponse(content = "📅 Upcoming events:\n$list")
                    } else {
                        chatRepository.saveAiResponse(content = "📅 Koi upcoming event nahi hai")
                    }
                }
            }

            // 📋 CLIPBOARD
            "CLIPBOARD" -> {
                val copyText = response.data?.get("text")?.toString()
                if (copyText != null) {
                    clipboardHelper.copyText(copyText)
                    chatRepository.saveSystemMessage("📋 Text copied!")
                } else {
                    val current = clipboardHelper.getClipboardText()
                    if (current.isNotBlank()) {
                        chatRepository.saveAiResponse(content = "📋 Clipboard: $current")
                    } else {
                        chatRepository.saveAiResponse(content = "📋 Clipboard khali hai")
                    }
                }
            }

            // 📸 CAMERA
            "CAMERA" -> {
                try {
                    val intent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    chatRepository.saveSystemMessage("📸 Camera opened!")
                } catch (e: Exception) {
                    // Fallback: open camera app
                    val service = MyAccessibilityService.instance
                    service?.executeAiCommand("OPEN_APP", "camera", null)
                    chatRepository.saveSystemMessage("📸 Camera app opened")
                }
            }

            // 📷 SCREENSHOT
            "SCREENSHOT" -> {
                val service = MyAccessibilityService.instance
                if (service != null) {
                    val success = service.executeAiCommand("SCREENSHOT", null, null)
                    if (success) chatRepository.saveSystemMessage("📷 Screenshot liya!")
                    else chatRepository.saveSystemMessage("❌ Screenshot failed")
                } else {
                    chatRepository.saveSystemMessage("⚠️ Accessibility Service ON karo")
                }
            }

            // 🔔 READ_NOTIFICATIONS
            "READ_NOTIFICATIONS" -> {
                val notifications = com.myai.assistant.service.MyNotificationListener.notifications.value
                if (notifications.isNotEmpty()) {
                    val list = notifications.take(5).joinToString("\n\n") { notif ->
                        "📱 ${notif.appName}\n📌 ${notif.title}\n💬 ${notif.text}"
                    }
                    chatRepository.saveAiResponse(content = "🔔 Recent Notifications:\n\n$list")
                } else {
                    chatRepository.saveAiResponse(content = "🔔 Koi recent notification nahi hai")
                }
            }

            // 🔍 OCR_READ — Screen/Image se text padhho
            "OCR_READ" -> {
                chatRepository.saveSystemMessage("🔍 Reading text from screen...")
                try {
                    // Latest screenshot ya image se OCR karo
                    val cacheDir = appContext.cacheDir
                    val imageFiles = cacheDir.listFiles { f -> f.name.endsWith(".jpg") || f.name.endsWith(".png") }
                        ?.sortedByDescending { it.lastModified() }
                    
                    val latestImage = imageFiles?.firstOrNull()
                    if (latestImage != null) {
                        val uri = android.net.Uri.fromFile(latestImage)
                        val ocrText = cameraManager.recognizeText(appContext, uri)
                        if (ocrText.isNotBlank()) {
                            chatRepository.saveAiResponse(content = "📝 OCR Result:\n\n$ocrText")
                        } else {
                            chatRepository.saveAiResponse(content = "🔍 Koi text nahi mila image mein")
                        }
                    } else {
                        chatRepository.saveSystemMessage("⚠️ Pehle screenshot lo ya photo lo, phir OCR chalao")
                    }
                } catch (e: Exception) {
                    chatRepository.saveSystemMessage("❌ OCR failed: ${e.message}")
                }
            }

            // 🗺️ NAVIGATE — Google Maps navigation
            "NAVIGATE" -> {
                if (target != null) {
                    val service = MyAccessibilityService.instance
                    if (service != null) {
                        val success = service.appAutomator.navigateTo(target)
                        if (success) chatRepository.saveSystemMessage("🗺️ Navigating to: $target")
                        else chatRepository.saveSystemMessage("❌ Navigation failed")
                    } else {
                        // Fallback: direct intent
                        try {
                            val uri = android.net.Uri.parse("google.navigation:q=${android.net.Uri.encode(target)}")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            appContext.startActivity(intent)
                            chatRepository.saveSystemMessage("🗺️ Navigating to: $target")
                        } catch (e: Exception) {
                            chatRepository.saveSystemMessage("❌ Maps nahi khul raha")
                        }
                    }
                }
            }

            // 🏪 INSTALL_APP — Play Store se app install
            "INSTALL_APP" -> {
                if (target != null) {
                    val service = MyAccessibilityService.instance
                    if (service != null) {
                        val success = service.appAutomator.searchPlayStore(target)
                        if (success) {
                            chatRepository.saveSystemMessage("🏪 Play Store: '$target' search ho raha hai")
                            // Agent loop se install button click hoga
                        }
                    } else {
                        try {
                            val uri = android.net.Uri.parse("market://search?q=${android.net.Uri.encode(target)}")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            appContext.startActivity(intent)
                            chatRepository.saveSystemMessage("🏪 Play Store opened for: $target")
                        } catch (e: Exception) {
                            chatRepository.saveSystemMessage("❌ Play Store nahi khul raha")
                        }
                    }
                }
            }

            // Baaki actions Accessibility Service se
            else -> {
                val service = MyAccessibilityService.instance
                if (service != null) {
                    val success = service.executeAiCommand(action, target, response.data)
                    if (success) {
                        Log.d(TAG, "✅ Action success: $action")
                    } else {
                        Log.w(TAG, "❌ Action failed: $action")
                        chatRepository.saveSystemMessage("❌ Action '$action' failed")
                    }
                } else if (action in listOf("OPEN_APP", "TYPE_TEXT", "CLICK", "BACK", "HOME", 
                    "READ_SCREEN", "FLASHLIGHT", "LOCK", "DND", "WIFI", "BLUETOOTH", "VOLUME",
                    "BRIGHTNESS", "MOBILE_DATA", "AIRPLANE", "HOTSPOT", "SCROLL_DOWN", "SCROLL_UP")) {
                    chatRepository.saveSystemMessage("⚠️ Accessibility Service ON karo for this action")
                }
            }
        }
    }

    private suspend fun sendWelcomeMessage() {
        chatRepository.saveAiResponse(
            content = "Namaste! 🙏 Main aapka personal AI assistant hoon.\n\n" +
                    "Mujhse kuch bhi poocho:\n" +
                    "• \"Rahul ko call karo\" 📞\n" +
                    "• \"Mummy ko SMS bhejo\" 💬\n" +
                    "• \"WhatsApp kholo\" 📱\n" +
                    "• \"Photo lo\" 📸\n" +
                    "• \"Meri location batao\" 📍\n\n" +
                    "Voice button dabao ya type karo! 🚀"
        )
    }

    private suspend fun checkAiAvailability() {
        val availability = aiClient.checkAvailability()
        val source = when {
            availability["ollama"] == true -> "ollama"
            availability["gemini"] == true -> "gemini"
            else -> "offline"
        }
        _uiState.update { it.copy(aiSource = source) }
    }

    /**
     * Diagnostics ke liye action trigger karo
     */
    fun triggerDiagnosticAction(action: String, target: String? = null) {
        viewModelScope.launch {
            val service = MyAccessibilityService.instance
            if (service != null) {
                val success = service.executeAiCommand(action, target, null)
                if (success) {
                    chatRepository.saveSystemMessage("✅ Diagnostics: Successfully executed $action")
                } else {
                    chatRepository.saveSystemMessage("❌ Diagnostics: Failed to execute $action")
                }
            } else {
                chatRepository.saveSystemMessage("⚠️ Diagnostics: Accessibility Service is not active")
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // AGENT LOOP — Multi-step autonomous execution
    // ═══════════════════════════════════════════════════════

    private val agentLoop = com.myai.assistant.ai.AgentLoop(aiClient, chatRepository)

    /**
     * 🔁 Multi-step agent mode — complex tasks ke liye
     * Example: "WhatsApp pe Rahul ko birthday wish karo"
     * Agent will: Open WhatsApp → Search Rahul → Click → Type → Send
     */
    fun executeAgentGoal(goal: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isAiThinking = true) }

                val result = agentLoop.executeGoal(
                    userGoal = goal,
                    executeAction = { response ->
                        try {
                            executeActionAndReturn(response)
                        } catch (e: Exception) {
                            Log.e(TAG, "Agent action error: ${e.message}")
                            false
                        }
                    }
                )

                if (result.success) {
                    chatRepository.saveAiResponse(content = "✅ ${result.message}")
                } else {
                    chatRepository.saveSystemMessage("⚠️ Agent: ${result.message} (${result.stepsUsed} steps)")
                }

            } catch (e: Exception) {
                chatRepository.saveSystemMessage("❌ Agent error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isAiThinking = false) }
            }
        }
    }

    /**
     * 🛑 Stop running agent loop
     */
    fun stopAgent() {
        agentLoop.stop()
        viewModelScope.launch {
            chatRepository.saveSystemMessage("🛑 Agent stopped")
        }
    }

    /**
     * Execute action and return success/failure (for agent loop)
     */
    private suspend fun executeActionAndReturn(response: AiParsedResponse): Boolean {
        val action = response.action.uppercase()
        val target = response.target

        return try {
            when (action) {
                "CALL", "SMS", "LOCATION", "DEVICE_INFO", "SET_ALARM", "SET_TIMER",
                "PLAY_MUSIC", "EMAIL", "CALENDAR", "CLIPBOARD", "CAMERA",
                "SCREENSHOT", "READ_NOTIFICATIONS" -> {
                    executeAction(response)
                    true
                }
                "DONE", "GENERAL" -> true
                else -> {
                    val service = MyAccessibilityService.instance
                    service?.executeAiCommand(action, target, response.data) ?: false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeActionAndReturn error: ${e.message}")
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        agentLoop.stop()
        voiceManager.destroy()
        geminiTts.destroy()
    }
}

