// File: app/src/main/java/com/myai/assistant/ai/AssistantBrain.kt
package com.myai.assistant.ai

import android.content.Context
import android.util.Log
import com.myai.assistant.accessibility.MyAccessibilityService
import com.myai.assistant.ai.models.AiParsedResponse
import com.myai.assistant.ai.models.AiSource
import com.myai.assistant.data.model.ChatMessage
import com.myai.assistant.data.model.MessageSender
import com.myai.assistant.data.model.MessageType
import com.myai.assistant.data.repository.ChatRepository
import com.myai.assistant.features.camera.CameraManager
import com.myai.assistant.features.contacts.ContactsHelper
import com.myai.assistant.features.device.AlarmHelper
import com.myai.assistant.features.device.CalendarHelper
import com.myai.assistant.features.device.ClipboardHelper
import com.myai.assistant.features.device.DeviceInfoManager
import com.myai.assistant.features.location.LocationHelper
import com.myai.assistant.features.media.MediaHelper
import com.myai.assistant.features.messages.SmsHelper
import com.myai.assistant.features.voice.GeminiTTSManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantBrain @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val chatRepository: ChatRepository,
    private val aiClient: AIClient,
    private val geminiTts: GeminiTTSManager,
    private val contactsHelper: ContactsHelper,
    private val smsHelper: SmsHelper,
    private val locationHelper: LocationHelper,
    private val deviceInfoManager: DeviceInfoManager,
    private val alarmHelper: AlarmHelper,
    private val mediaHelper: MediaHelper,
    private val calendarHelper: CalendarHelper,
    private val clipboardHelper: ClipboardHelper,
    private val cameraManager: CameraManager
) {
    companion object {
        private const val TAG = "AssistantBrain"
    }

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _aiSource = MutableStateFlow("offline")
    val aiSource: StateFlow<String> = _aiSource.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun setAiSource(source: String) {
        _aiSource.value = source
    }

    suspend fun processRequest(userMessage: String, isVoiceOutputEnabled: Boolean): String {
        try {
            _isAiThinking.value = true
            _error.value = null

            // Check for zero-latency local shortcuts first
            val shortcutResponse = tryLocalShortcutParser(userMessage)
            if (shortcutResponse != null) {
                Log.d(TAG, "⚡ Zero-Latency Shortcut triggered: ${shortcutResponse.action}")

                chatRepository.saveAiResponse(content = shortcutResponse.message)
                _isAiThinking.value = false
                _aiSource.value = "shortcut"

                // Voice output if enabled
                if (isVoiceOutputEnabled && shortcutResponse.message.isNotBlank()) {
                    geminiTts.speak(shortcutResponse.message)
                }

                // Execute instantly
                executeAction(shortcutResponse)
                return shortcutResponse.message
            }

            val loadingId = chatRepository.insertLoadingMessage()

            val currentMessages = chatRepository.getAllMessages().first()
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

            _isAiThinking.value = false
            _aiSource.value = response.source.name.lowercase()

            // Voice output (agar enabled hai) — Gemini TTS
            if (isVoiceOutputEnabled && response.message.isNotBlank()) {
                geminiTts.speak(response.message)
            }

            // Action execute karo
            if (response.action != "GENERAL" && response.isSuccess) {
                executeAction(response)
            }

            return response.message

        } catch (e: Exception) {
            Log.e(TAG, "Error processing request: ${e.message}", e)
            _isAiThinking.value = false
            _error.value = "Error: ${e.message}"
            chatRepository.saveErrorMessage("⚠️ ${e.message}")
            return "Error: ${e.message}"
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
            return AiParsedResponse(action = "FLASHLIGHT", target = "on", message = "🔦 Flashlight turning ON...", isSuccess = true, source = AiSource.FALLBACK)
        }
        if (lower.containsAny("flashlight off", "torch off", "flashlight band", "torch band")) {
            return AiParsedResponse(action = "FLASHLIGHT", target = "off", message = "🔦 Flashlight turning OFF...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // 2. LOCK PHONE
        if (lower.containsAny("lock screen", "lock phone", "screen lock", "lock karo")) {
            return AiParsedResponse(action = "LOCK", target = null, message = "🔒 Locking device...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // 3. WIFI
        if (lower.containsAny("wifi on", "wifi open", "wifi kholo", "wifi chalao")) {
            return AiParsedResponse(action = "WIFI", target = "on", message = "📡 Opening WiFi...", isSuccess = true, source = AiSource.FALLBACK)
        }
        if (lower.containsAny("wifi off", "wifi close", "wifi band")) {
            return AiParsedResponse(action = "WIFI", target = "off", message = "📡 Closing WiFi...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // 4. BLUETOOTH
        if (lower.containsAny("bluetooth on", "bluetooth chalao", "bluetooth open")) {
            return AiParsedResponse(action = "BLUETOOTH", target = "on", message = "📶 Bluetooth turning ON...", isSuccess = true, source = AiSource.FALLBACK)
        }
        if (lower.containsAny("bluetooth off", "bluetooth band")) {
            return AiParsedResponse(action = "BLUETOOTH", target = "off", message = "📶 Bluetooth turning OFF...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // 5. VOLUME
        if (lower.containsAny("volume up", "volume badhao", " आवाज बढ़ाओ")) {
            return AiParsedResponse(action = "VOLUME", target = "up", message = "🔊 Raising volume...", isSuccess = true, source = AiSource.FALLBACK)
        }
        if (lower.containsAny("volume down", "volume kam", "आवाज कम करो")) {
            return AiParsedResponse(action = "VOLUME", target = "down", message = "🔉 Lowering volume...", isSuccess = true, source = AiSource.FALLBACK)
        }
        if (lower.containsAny("mute", "silent mode", "volume zero")) {
            return AiParsedResponse(action = "VOLUME", target = "mute", message = "🔇 Muting volume...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // 6. DEVICE INFO
        if (lower.containsAny("battery", "storage info", "ram info", "device info", "system info")) {
            return AiParsedResponse(action = "DEVICE_INFO", target = null, message = "📱 Fetching device details...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // 7. SCREENSHOT
        if (lower.containsAny("take screenshot", "screenshot lo", "screenshot")) {
            return AiParsedResponse(action = "SCREENSHOT", target = null, message = "📷 Capturing screen...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // 8. CAMERA
        if (lower.containsAny("open camera", "camera kholo", "photo lo")) {
            return AiParsedResponse(action = "CAMERA", target = null, message = "📸 Opening Camera...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // 9. DND
        if (lower.containsAny("dnd on", "dnd activation", "dnd close")) {
            return AiParsedResponse(action = "DND", target = "on", message = "🔇 Activating Do Not Disturb...", isSuccess = true, source = AiSource.FALLBACK)
        }
        if (lower.containsAny("dnd off", "dnd deactivation")) {
            return AiParsedResponse(action = "DND", target = "off", message = "🔔 Deactivating Do Not Disturb...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // 10. WHATSAPP MESSAGE — "whatsapp pe X ko message karo Y"
        val waRegex = Regex("(whatsapp|wa).*?(ko|to)?\\s*([a-zA-Z]+)\\s*(message|msg|bolo|bhejo)\\s*(.+)")
        val waMatch = waRegex.find(lower)
        if (waMatch != null) {
            val contact = waMatch.groupValues[3].trim()
            val msg = waMatch.groupValues[5].trim()
            if (contact.isNotBlank() && msg.isNotBlank()) {
                return AiParsedResponse(action = "WHATSAPP_MSG", target = contact,
                    message = "📱 WhatsApp pe '$contact' ko message bhej raha hoon...",
                    data = mapOf("message" to msg), isSuccess = true, source = AiSource.FALLBACK)
            }
        }

        // 11. APP OPENING — Direct package mapping (no accessibility needed)
        val openAppRegex = Regex("(open|kholo|chalao|launch|start)\\s+([a-zA-Z0-9\\s]+)")
        val matchResult = openAppRegex.find(lower)
        if (matchResult != null) {
            val appName = matchResult.groupValues[2].trim()
            if (appName.isNotBlank() && !appName.containsAny("wifi", "bluetooth", "camera", "flashlight")) {
                return AiParsedResponse(action = "OPEN_APP", target = appName,
                    message = "🚀 Opening $appName...", isSuccess = true, source = AiSource.FALLBACK)
            }
        }

        // 12. YOUTUBE SEARCH — "youtube pe X search karo" / "play X on youtube"
        val ytRegex = Regex("(youtube|yt).*(search|play|dekho|dhundho|find)\\s+(.+)|play\\s+(.+)\\s+(on|pe)\\s+(youtube|yt)")
        val ytMatch = ytRegex.find(lower)
        if (ytMatch != null) {
            val query = (ytMatch.groupValues[3].takeIf { it.isNotBlank() } ?: ytMatch.groupValues[4]).trim()
            if (query.isNotBlank()) {
                return AiParsedResponse(action = "YOUTUBE_SEARCH", target = query,
                    message = "▶️ YouTube pe '$query' search kar raha hoon...", isSuccess = true, source = AiSource.FALLBACK)
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
    suspend fun executeAction(response: AiParsedResponse) {
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
                    chatRepository.saveSystemMessage("❌ Camera nahi khula: ${e.message}")
                }
            }

            // 📱 WHATSAPP MESSAGE — Direct via WhatsApp API (no accessibility needed)
            "WHATSAPP_MSG" -> {
                val contact = target ?: ""
                val message = response.data?.get("message")?.toString() ?: ""
                try {
                    // Try by phone number first
                    val phoneNumber = contactsHelper.findContact(appContext, contact)?.phoneNumber
                    if (phoneNumber != null) {
                        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
                        val uri = android.net.Uri.parse("https://wa.me/$cleanNumber?text=${android.net.Uri.encode(message)}")
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.whatsapp")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        appContext.startActivity(intent)
                        chatRepository.saveSystemMessage("📱 WhatsApp khul gaya! '$contact' ko message bhejo.")
                    } else {
                        // Open WhatsApp and let user search
                        val intent = appContext.packageManager.getLaunchIntentForPackage("com.whatsapp")?.apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (intent != null) appContext.startActivity(intent)
                        chatRepository.saveSystemMessage("📱 WhatsApp khola — '$contact' manually search karo")
                    }
                } catch (e: Exception) {
                    chatRepository.saveSystemMessage("❌ WhatsApp nahi khula. WhatsApp install hai?")
                }
            }

            // ▶️ YOUTUBE SEARCH — Direct intent (no accessibility needed)
            "YOUTUBE_SEARCH" -> {
                val query = target ?: ""
                try {
                    // Try YouTube app first
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEARCH).apply {
                        setPackage("com.google.android.youtube")
                        putExtra("query", query)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    chatRepository.saveSystemMessage("▶️ YouTube pe '$query' search ho raha hai!")
                } catch (e: Exception) {
                    try {
                        // Fallback: browser
                        val uri = android.net.Uri.parse("https://www.youtube.com/results?search_query=${android.net.Uri.encode(query)}")
                        val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        appContext.startActivity(browserIntent)
                        chatRepository.saveSystemMessage("▶️ YouTube browser mein khola: $query")
                    } catch (ex: Exception) {
                        chatRepository.saveSystemMessage("❌ YouTube nahi khul raha")
                    }
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

            // OPEN_APP — Direct package launch (no accessibility needed for common apps)
            "OPEN_APP" -> {
                val appName = target?.lowercase()?.trim() ?: ""
                val packageName = APP_PACKAGE_MAP[appName]
                    ?: APP_PACKAGE_MAP.entries.firstOrNull { appName.contains(it.key) }?.value

                if (packageName != null) {
                    val intent = appContext.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent != null) {
                        appContext.startActivity(intent)
                        chatRepository.saveSystemMessage("✅ $target khul gaya!")
                    } else {
                        chatRepository.saveSystemMessage("❌ $target install nahi hai")
                    }
                } else {
                    // Try accessibility service for unknown apps
                    val service = MyAccessibilityService.instance
                    if (service != null) {
                        service.executeAiCommand(action, target, response.data)
                    } else {
                        // Last resort: search on Play Store
                        try {
                            val uri = android.net.Uri.parse("market://search?q=${android.net.Uri.encode(appName)}")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            appContext.startActivity(intent)
                            chatRepository.saveSystemMessage("🔍 '$appName' Play Store mein search ho raha hai")
                        } catch (e: Exception) {
                            chatRepository.saveSystemMessage("❌ App nahi mila: $appName")
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
                } else {
                    chatRepository.saveSystemMessage("⚠️ Action '$action' ke liye Accessibility Service ON karo")
                }
            }
        }
    }

    companion object {
        // Common app package names — direct launch without accessibility
        val APP_PACKAGE_MAP = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "chrome" to "com.android.chrome",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "telegram" to "org.telegram.messenger",
            "snapchat" to "com.snapchat.android",
            "settings" to "com.android.settings",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "photos" to "com.google.android.apps.photos",
            "camera" to "com.google.android.GoogleCamera",
            "play store" to "com.android.vending",
            "phone" to "com.google.android.dialer",
            "contacts" to "com.google.android.contacts",
            "messages" to "com.google.android.apps.messaging",
            "files" to "com.google.android.apps.nbu.files",
            "drive" to "com.google.android.apps.docs",
            "meet" to "com.google.android.apps.tachyon",
            "zoom" to "us.zoom.videomeetings",
            "paytm" to "net.one97.paytm",
            "phonepe" to "com.phonepe.app",
            "gpay" to "com.google.android.apps.nbu.paisa.user",
            "amazon" to "in.amazon.mShop.android.shopping",
            "flipkart" to "com.flipkart.android",
            "swiggy" to "in.swiggy.android",
            "zomato" to "com.application.zomato",
            "ola" to "com.olacabs.customer",
            "uber" to "com.ubercab",
            "hotstar" to "in.startv.hotstar",
            "jio cinema" to "com.jio.jioplay",
            "prime video" to "com.amazon.avod.thirdpartyclient"
        )
    }
}
