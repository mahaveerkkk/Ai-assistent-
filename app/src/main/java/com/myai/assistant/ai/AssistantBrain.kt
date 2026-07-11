// File: app/src/main/java/com/myai/assistant/ai/AssistantBrain.kt
package com.myai.assistant.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
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
            "playstore" to "com.android.vending",
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
            "google pay" to "com.google.android.apps.nbu.paisa.user",
            "amazon" to "in.amazon.mShop.android.shopping",
            "flipkart" to "com.flipkart.android",
            "swiggy" to "in.swiggy.android",
            "zomato" to "com.application.zomato",
            "ola" to "com.olacabs.customer",
            "uber" to "com.ubercab",
            "hotstar" to "in.startv.hotstar",
            "jio cinema" to "com.jio.jioplay",
            "prime video" to "com.amazon.avod.thirdpartyclient",
            "linkedin" to "com.linkedin.android",
            "reddit" to "com.reddit.frontpage",
            "discord" to "com.discord"
        )
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
                Log.d(TAG, "⚡ Shortcut triggered: ${shortcutResponse.action}")
                chatRepository.saveAiResponse(content = shortcutResponse.message)
                _isAiThinking.value = false
                _aiSource.value = "shortcut"
                if (isVoiceOutputEnabled && shortcutResponse.message.isNotBlank()) {
                    geminiTts.speak(shortcutResponse.message)
                }
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

            if (isVoiceOutputEnabled && response.message.isNotBlank()) {
                geminiTts.speak(response.message)
            }

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
     * ⚡ Zero-Latency Shortcut Parser — no AI needed
     */
    private fun tryLocalShortcutParser(message: String): AiParsedResponse? {
        val lower = message.trim().lowercase()

        // 1. FLASHLIGHT
        if (lower.containsAny("flashlight on", "torch on", "flashlight kholo", "torch chalao"))
            return AiParsedResponse(action = "FLASHLIGHT", target = "on", message = "🔦 Flashlight ON!", isSuccess = true, source = AiSource.FALLBACK)
        if (lower.containsAny("flashlight off", "torch off", "flashlight band", "torch band"))
            return AiParsedResponse(action = "FLASHLIGHT", target = "off", message = "🔦 Flashlight OFF!", isSuccess = true, source = AiSource.FALLBACK)

        // 2. LOCK
        if (lower.containsAny("lock screen", "lock phone", "screen lock", "lock karo"))
            return AiParsedResponse(action = "LOCK", target = null, message = "🔒 Locking device...", isSuccess = true, source = AiSource.FALLBACK)

        // 3. WIFI
        if (lower.containsAny("wifi on", "wifi open", "wifi kholo", "wifi chalao"))
            return AiParsedResponse(action = "WIFI", target = "on", message = "📡 Opening WiFi...", isSuccess = true, source = AiSource.FALLBACK)
        if (lower.containsAny("wifi off", "wifi close", "wifi band"))
            return AiParsedResponse(action = "WIFI", target = "off", message = "📡 Closing WiFi...", isSuccess = true, source = AiSource.FALLBACK)

        // 4. BLUETOOTH
        if (lower.containsAny("bluetooth on", "bluetooth chalao", "bluetooth open"))
            return AiParsedResponse(action = "BLUETOOTH", target = "on", message = "📶 Bluetooth ON!", isSuccess = true, source = AiSource.FALLBACK)
        if (lower.containsAny("bluetooth off", "bluetooth band"))
            return AiParsedResponse(action = "BLUETOOTH", target = "off", message = "📶 Bluetooth OFF!", isSuccess = true, source = AiSource.FALLBACK)

        // 5. VOLUME
        if (lower.containsAny("volume up", "volume badhao"))
            return AiParsedResponse(action = "VOLUME", target = "up", message = "🔊 Volume up!", isSuccess = true, source = AiSource.FALLBACK)
        if (lower.containsAny("volume down", "volume kam"))
            return AiParsedResponse(action = "VOLUME", target = "down", message = "🔉 Volume down!", isSuccess = true, source = AiSource.FALLBACK)
        if (lower.containsAny("mute", "silent mode", "volume zero"))
            return AiParsedResponse(action = "VOLUME", target = "mute", message = "🔇 Muted!", isSuccess = true, source = AiSource.FALLBACK)

        // 6. DEVICE INFO
        if (lower.containsAny("battery", "storage info", "ram info", "device info"))
            return AiParsedResponse(action = "DEVICE_INFO", target = null, message = "📱 Fetching device info...", isSuccess = true, source = AiSource.FALLBACK)

        // 7. SCREENSHOT
        if (lower.containsAny("take screenshot", "screenshot lo", "screenshot"))
            return AiParsedResponse(action = "SCREENSHOT", target = null, message = "📷 Taking screenshot...", isSuccess = true, source = AiSource.FALLBACK)

        // 8. CAMERA
        if (lower.containsAny("open camera", "camera kholo", "photo lo"))
            return AiParsedResponse(action = "CAMERA", target = null, message = "📸 Opening Camera...", isSuccess = true, source = AiSource.FALLBACK)

        // 9. DND
        if (lower.containsAny("dnd on", "do not disturb on"))
            return AiParsedResponse(action = "DND", target = "on", message = "🔇 DND ON!", isSuccess = true, source = AiSource.FALLBACK)
        if (lower.containsAny("dnd off", "do not disturb off"))
            return AiParsedResponse(action = "DND", target = "off", message = "🔔 DND OFF!", isSuccess = true, source = AiSource.FALLBACK)

        // 10. YOUTUBE SEARCH
        val ytRegex = Regex("(youtube|yt).*(search|play|dekho|dhundho)\\s+(.+)")
        val ytMatch = ytRegex.find(lower)
        if (ytMatch != null) {
            val query = ytMatch.groupValues[3].trim()
            if (query.isNotBlank())
                return AiParsedResponse(action = "YOUTUBE_SEARCH", target = query,
                    message = "▶️ YouTube pe '$query' search!", isSuccess = true, source = AiSource.FALLBACK)
        }

        // 11. WHATSAPP MESSAGE
        val waRegex = Regex("(whatsapp|wa)\\s+(?:pe\\s+)?([a-zA-Z]+)\\s+(?:ko\\s+)?(?:message|msg|bolo|bhejo)\\s+(.+)")
        val waMatch = waRegex.find(lower)
        if (waMatch != null) {
            val contact = waMatch.groupValues[2].trim()
            val msg = waMatch.groupValues[3].trim()
            if (contact.isNotBlank() && msg.isNotBlank())
                return AiParsedResponse(action = "WHATSAPP_MSG", target = contact,
                    message = "📱 WhatsApp '$contact' ko message...", data = mapOf("message" to msg),
                    isSuccess = true, source = AiSource.FALLBACK)
        }

        // 12. OPEN APP — direct package lookup
        val openAppRegex = Regex("(open|kholo|chalao|launch|start)\\s+([a-zA-Z0-9 ]+)")
        val appMatch = openAppRegex.find(lower)
        if (appMatch != null) {
            val appName = appMatch.groupValues[2].trim()
            if (appName.isNotBlank() && !appName.containsAny("wifi", "bluetooth", "camera", "flashlight")) {
                return AiParsedResponse(action = "OPEN_APP", target = appName,
                    message = "🚀 Opening $appName...", isSuccess = true, source = AiSource.FALLBACK)
            }
        }

        return null
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }

    /**
     * Execute AI action
     */
    suspend fun executeAction(response: AiParsedResponse) {
        val action = response.action.uppercase()
        val target = response.target

        when (action) {

            // 📞 CALL
            "CALL" -> {
                if (target != null) {
                    val isNumber = target.matches(Regex("^[+]*[0-9 -]{5,15}$"))
                    if (isNumber) contactsHelper.makeCall(appContext, target)
                    else {
                        val success = contactsHelper.callByName(appContext, target)
                        if (!success) contactsHelper.makeCall(appContext, target)
                    }
                }
            }

            // 💬 SMS
            "SMS" -> {
                if (target != null) {
                    val isNumber = target.matches(Regex("^[+]*[0-9 -]{5,15}$"))
                    val destination = if (isNumber) target else contactsHelper.findContact(appContext, target)?.phoneNumber
                    val msg = response.data?.get("message")?.toString() ?: ""
                    if (destination != null && msg.isNotBlank()) {
                        val success = smsHelper.sendSms(appContext, destination, msg)
                        chatRepository.saveSystemMessage(if (success) "✅ SMS sent to $target" else "❌ SMS failed")
                    } else {
                        chatRepository.saveSystemMessage("❌ Contact/number not found")
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
                        content = "📍 Location:\n${location.address}\nCity: ${location.city}\n(${location.latitude}, ${location.longitude})"
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
                    chatRepository.saveSystemMessage(if (success) "🎵 Playing: $target" else "❌ Music play failed")
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
                    val events = calendarHelper.getUpcomingEvents()
                    if (events.isNotEmpty()) {
                        val list = events.take(5).joinToString("\n") { "• ${it.title}" }
                        chatRepository.saveAiResponse(content = "📅 Upcoming:\n$list")
                    } else {
                        chatRepository.saveAiResponse(content = "📅 No upcoming events")
                    }
                }
            }

            // 📋 CLIPBOARD
            "CLIPBOARD" -> {
                val copyText = response.data?.get("text")?.toString()
                if (copyText != null) {
                    clipboardHelper.copyText(copyText)
                    chatRepository.saveSystemMessage("📋 Copied!")
                } else {
                    val current = clipboardHelper.getClipboardText()
                    chatRepository.saveAiResponse(content = if (current.isNotBlank()) "📋 Clipboard: $current" else "📋 Clipboard empty")
                }
            }

            // 📸 CAMERA
            "CAMERA" -> {
                try {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    chatRepository.saveSystemMessage("📸 Camera opened!")
                } catch (e: Exception) {
                    chatRepository.saveSystemMessage("❌ Camera error: ${e.message}")
                }
            }

            // 📱 WHATSAPP DIRECT MESSAGE
            "WHATSAPP_MSG" -> {
                val contact = target ?: ""
                val msg = response.data?.get("message")?.toString() ?: ""
                try {
                    val phoneNumber = contactsHelper.findContact(appContext, contact)?.phoneNumber
                    if (phoneNumber != null) {
                        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
                        val uri = Uri.parse("https://wa.me/$cleanNumber?text=${Uri.encode(msg)}")
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.whatsapp")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        appContext.startActivity(intent)
                        chatRepository.saveSystemMessage("📱 WhatsApp opened for '$contact'")
                    } else {
                        val intent = appContext.packageManager.getLaunchIntentForPackage("com.whatsapp")?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (intent != null) appContext.startActivity(intent)
                        chatRepository.saveSystemMessage("📱 WhatsApp opened — search '$contact' manually")
                    }
                } catch (e: Exception) {
                    chatRepository.saveSystemMessage("❌ WhatsApp error: ${e.message}")
                }
            }

            // ▶️ YOUTUBE SEARCH
            "YOUTUBE_SEARCH" -> {
                val query = target ?: ""
                try {
                    val intent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage("com.google.android.youtube")
                        putExtra("query", query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    chatRepository.saveSystemMessage("▶️ YouTube searching: $query")
                } catch (e: Exception) {
                    try {
                        val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                        appContext.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        chatRepository.saveSystemMessage("▶️ YouTube (browser): $query")
                    } catch (ex: Exception) {
                        chatRepository.saveSystemMessage("❌ YouTube error")
                    }
                }
            }

            // 🔔 READ NOTIFICATIONS
            "READ_NOTIFICATIONS" -> {
                val notifications = com.myai.assistant.service.MyNotificationListener.notifications.value
                if (notifications.isNotEmpty()) {
                    val list = notifications.take(5).joinToString("\n\n") { n ->
                        "📱 ${n.appName}\n📌 ${n.title}\n💬 ${n.text}"
                    }
                    chatRepository.saveAiResponse(content = "🔔 Notifications:\n\n$list")
                } else {
                    chatRepository.saveAiResponse(content = "🔔 No recent notifications")
                }
            }

            // 🔍 OCR READ
            "OCR_READ" -> {
                try {
                    val cacheDir = appContext.cacheDir
                    val latestImage = cacheDir.listFiles { f -> f.name.endsWith(".jpg") || f.name.endsWith(".png") }
                        ?.maxByOrNull { it.lastModified() }
                    if (latestImage != null) {
                        val uri = Uri.fromFile(latestImage)
                        val ocrText = cameraManager.recognizeText(appContext, uri)
                        chatRepository.saveAiResponse(
                            content = if (ocrText.isNotBlank()) "📝 OCR:\n\n$ocrText" else "🔍 No text found"
                        )
                    } else {
                        chatRepository.saveSystemMessage("⚠️ Take screenshot first, then run OCR")
                    }
                } catch (e: Exception) {
                    chatRepository.saveSystemMessage("❌ OCR failed: ${e.message}")
                }
            }

            // 🗺️ NAVIGATE
            "NAVIGATE" -> {
                if (target != null) {
                    try {
                        val uri = Uri.parse("google.navigation:q=${Uri.encode(target)}")
                        appContext.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        chatRepository.saveSystemMessage("🗺️ Navigating to: $target")
                    } catch (e: Exception) {
                        chatRepository.saveSystemMessage("❌ Navigation failed: ${e.message}")
                    }
                }
            }

            // 🏪 INSTALL APP
            "INSTALL_APP" -> {
                if (target != null) {
                    try {
                        val uri = Uri.parse("market://search?q=${Uri.encode(target)}")
                        appContext.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        chatRepository.saveSystemMessage("🏪 Play Store: $target")
                    } catch (e: Exception) {
                        chatRepository.saveSystemMessage("❌ Play Store error: ${e.message}")
                    }
                }
            }

            // 📱 OPEN APP — direct package intent
            "OPEN_APP" -> {
                val appName = target?.lowercase()?.trim() ?: ""
                val packageName = APP_PACKAGE_MAP[appName]
                    ?: APP_PACKAGE_MAP.entries.firstOrNull { appName.contains(it.key) }?.value

                if (packageName != null) {
                    val intent = appContext.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent != null) {
                        appContext.startActivity(intent)
                        chatRepository.saveSystemMessage("✅ $target opened!")
                    } else {
                        chatRepository.saveSystemMessage("❌ $target not installed")
                    }
                } else {
                    // Try accessibility service for unknown apps
                    val service = MyAccessibilityService.instance
                    if (service != null) {
                        service.executeAiCommand(action, target, response.data)
                    } else {
                        try {
                            val uri = Uri.parse("market://search?q=${Uri.encode(appName)}")
                            appContext.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            chatRepository.saveSystemMessage("🔍 Searching '$appName' on Play Store")
                        } catch (e: Exception) {
                            chatRepository.saveSystemMessage("❌ App not found: $appName")
                        }
                    }
                }
            }

            // 📷 SCREENSHOT
            "SCREENSHOT" -> {
                val service = MyAccessibilityService.instance
                if (service != null) {
                    val success = service.executeAiCommand("SCREENSHOT", null, null)
                    chatRepository.saveSystemMessage(if (success) "📷 Screenshot taken!" else "❌ Screenshot failed")
                } else {
                    chatRepository.saveSystemMessage("⚠️ Enable Accessibility Service for screenshots")
                }
            }

            // All other actions via Accessibility Service
            else -> {
                val service = MyAccessibilityService.instance
                if (service != null) {
                    val success = service.executeAiCommand(action, target, response.data)
                    if (!success) chatRepository.saveSystemMessage("❌ Action '$action' failed")
                } else {
                    chatRepository.saveSystemMessage("⚠️ Enable Accessibility Service for action: $action")
                }
            }
        }
    }
}
