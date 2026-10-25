// File: app/src/main/java/com/myai/assistant/accessibility/AppAutomator.kt
// App Automator — High-level automation for popular apps (WhatsApp, YouTube, etc.)

package com.myai.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.myai.assistant.features.contacts.ContactsHelper
import kotlinx.coroutines.delay

/**
 * App Automator — Popular apps ko automate karo
 * WhatsApp mein message bhejo, YouTube mein search karo, etc.
 */
class AppAutomator(
    private val service: AccessibilityService,
    private val actionPerformer: ActionPerformer
) {
    private val contactsHelper = ContactsHelper()

    companion object {
        private const val TAG = "AppAutomator"

        // Popular app package names
        const val PKG_WHATSAPP = "com.whatsapp"
        const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_CHROME = "com.android.chrome"
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_TELEGRAM = "org.telegram.messenger"
        const val PKG_GMAIL = "com.google.android.gm"
        const val PKG_MAPS = "com.google.android.apps.maps"
        const val PKG_CAMERA = "com.android.camera"
        const val PKG_SETTINGS = "com.android.settings"
        const val PKG_CLOCK = "com.google.android.deskclock"
        const val PKG_CALCULATOR = "com.google.android.calculator"
        const val PKG_PHONE = "com.android.dialer"
        const val PKG_MESSAGES = "com.google.android.apps.messaging"
        const val PKG_GALLERY = "com.google.android.apps.photos"
        const val PKG_SPOTIFY = "com.spotify.music"
        const val PKG_TWITTER = "com.twitter.android"
        const val PKG_FACEBOOK = "com.facebook.katana"
    }

    // ═══════════════════════════════════════════════════════
    // APP LAUNCHER — Koi bhi app kholo
    // ═══════════════════════════════════════════════════════

    /**
     * App name se app kholo
     */
    fun openApp(appName: String): Boolean {
        val packageName = resolvePackageName(appName)
        return if (packageName != null) {
            openAppByPackage(packageName)
        } else {
            Log.w(TAG, "App not found: $appName")
            false
        }
    }

    /**
     * Package name se app kholo
     */
    fun openAppByPackage(packageName: String): Boolean {
        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(intent)
                Log.d(TAG, "Opened app: $packageName")
                true
            } else {
                Log.w(TAG, "No launch intent for: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open $packageName: ${e.message}", e)
            false
        }
    }

    /**
     * App name ko package name mein convert karo
     */
    private fun resolvePackageName(appName: String): String? {
        val lower = appName.lowercase().trim()
        return when {
            lower.containsAny("whatsapp business", "wa business") -> PKG_WHATSAPP_BUSINESS
            lower.containsAny("whatsapp", "whats app", "wp", "wa") -> PKG_WHATSAPP
            lower.containsAny("youtube", "yt") -> PKG_YOUTUBE
            lower.containsAny("chrome", "browser") -> PKG_CHROME
            lower.containsAny("instagram", "insta", "ig") -> PKG_INSTAGRAM
            lower.containsAny("telegram", "tg") -> PKG_TELEGRAM
            lower.containsAny("gmail", "email", "mail") -> PKG_GMAIL
            lower.containsAny("maps", "map", "navigate") -> PKG_MAPS
            lower.containsAny("camera") -> PKG_CAMERA
            lower.containsAny("settings", "setting") -> PKG_SETTINGS
            lower.containsAny("clock", "alarm", "timer") -> PKG_CLOCK
            lower.containsAny("calculator", "calc") -> PKG_CALCULATOR
            lower.containsAny("phone", "dialer", "call") -> PKG_PHONE
            lower.containsAny("messages", "sms") -> PKG_MESSAGES
            lower.containsAny("gallery", "photos", "photo") -> PKG_GALLERY
            lower.containsAny("spotify", "music") -> PKG_SPOTIFY
            lower.containsAny("twitter", "x") -> PKG_TWITTER
            lower.containsAny("facebook", "fb") -> PKG_FACEBOOK
            else -> findPackageByName(appName)
        }
    }

    /**
     * Installed apps mein search karo by name
     */
    private fun findPackageByName(appName: String): String? {
        return try {
            val pm = service.packageManager
            val apps = pm.getInstalledApplications(0)
            apps.find {
                pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true)
            }?.packageName
        } catch (e: Exception) {
            null
        }
    }

    // ═══════════════════════════════════════════════════════
    // WHATSAPP AUTOMATION
    // ═══════════════════════════════════════════════════════

    /**
     * WhatsApp Send button dhund kar click karo (Content Description + View ID)
     * Multiple retries across view hierarchy
     */
    fun findAndClickWhatsAppSendButton(): Boolean {
        val root = service.rootInActiveWindow ?: return false

        // 1. Direct search by Resource ID (com.whatsapp:id/send, com.whatsapp.w4b:id/send)
        val candidateIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send",
            "send"
        )
        for (id in candidateIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (actionPerformer.clickNode(node)) {
                    Log.d(TAG, "Clicked WhatsApp Send button by ID: $id")
                    return true
                }
            }
        }

        // 2. Direct search by Content Description
        val sendLabels = listOf("Send", "Send message", "भेजें", "Bhejein", "Bhejo")
        for (label in sendLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                val desc = node.contentDescription?.toString()
                if (desc != null && sendLabels.any { desc.contains(it, ignoreCase = true) }) {
                    if (actionPerformer.clickNode(node)) {
                        Log.d(TAG, "Clicked WhatsApp Send button by ContentDescription: $desc")
                        return true
                    }
                }
            }
        }

        // 3. Fallback: Search all clickable icon views near bottom right
        return clickByContentDescription("Send")
    }

    /**
     * Poll and automatically click WhatsApp Send button with adaptive delay
     * Checks repeatedly every 350ms up to maxAttempts (~4.5s) to ensure the chat window is rendered
     */
    suspend fun autoClickSendButton(maxAttempts: Int = 12): Boolean {
        for (attempt in 1..maxAttempts) {
            delay(350)
            if (findAndClickWhatsAppSendButton()) {
                Log.d(TAG, "✅ WhatsApp Send button clicked on attempt #$attempt")
                return true
            }
        }
        Log.w(TAG, "WhatsApp Send button not found after $maxAttempts attempts")
        return false
    }

    /**
     * WhatsApp mein kisi ko message bhejo
     * @param contact Contact name ya phone number
     * @param message Message text
     */
    suspend fun sendWhatsAppMessage(contact: String, message: String): Boolean {
        try {
            Log.d(TAG, "WhatsApp: Preparing message to '$contact': '$message'")

            // 1. Check if contact is a raw phone number
            val isRawNumber = contact.matches(Regex("^[+]*[0-9 -]{7,16}$"))

            // 2. If not raw number, lookup contact in device contacts
            val foundContact = if (!isRawNumber) contactsHelper.findContact(service, contact) else null
            val targetNumber = if (isRawNumber) contact else foundContact?.phoneNumber

            // 3. If phone number is available, use fast and reliable wa.me deep link
            if (targetNumber != null) {
                val formattedNumber = contactsHelper.formatForWhatsApp(targetNumber)
                Log.d(TAG, "Opening WhatsApp deep link for $formattedNumber")

                val uri = Uri.parse("https://wa.me/$formattedNumber?text=${Uri.encode(message)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(PKG_WHATSAPP)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                try {
                    service.startActivity(intent)
                } catch (e: Exception) {
                    // Try without setPackage (for WhatsApp Business or default handler)
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    service.startActivity(fallbackIntent)
                }

                // Auto-click Send button via accessibility polling
                return autoClickSendButton(maxAttempts = 12)
            }

            // 4. Fallback: Search inside WhatsApp UI if contact number not in address book
            Log.d(TAG, "Contact '$contact' not found in contacts, falling back to WhatsApp UI automation")
            val opened = openAppByPackage(PKG_WHATSAPP) || openAppByPackage(PKG_WHATSAPP_BUSINESS)
            if (!opened) return false

            delay(2000)

            // Click Search icon in WhatsApp
            val searchClicked = actionPerformer.clickByViewId("com.whatsapp:id/menuitem_search")
                    || actionPerformer.clickByContentDescription("Search")
                    || actionPerformer.clickByText("Search")

            if (!searchClicked) {
                Log.w(TAG, "Could not find Search button in WhatsApp")
            }
            delay(1000)

            // Type contact name in search input
            actionPerformer.typeInFirstField(contact)
            delay(2000)

            // Click on the matching contact from search results
            val contactClicked = actionPerformer.clickByText(contact)
            if (!contactClicked) {
                Log.w(TAG, "Could not find search result for '$contact' in WhatsApp")
                return false
            }
            delay(1500)

            // Type message in input field
            val typed = actionPerformer.typeInFieldWithHint("Type a message", message)
                    || actionPerformer.typeInFieldWithHint("Message", message)
                    || actionPerformer.typeInFirstField(message)

            if (!typed) {
                Log.w(TAG, "Could not type message in WhatsApp chat")
            }
            delay(500)

            // Click Send button
            return autoClickSendButton(maxAttempts = 6)

        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp automation error: ${e.message}", e)
            return false
        }
    }

    // ═══════════════════════════════════════════════════════
    // YOUTUBE AUTOMATION
    // ═══════════════════════════════════════════════════════

    /**
     * YouTube mein search karo
     */
    suspend fun searchYouTube(query: String): Boolean {
        try {
            Log.d(TAG, "YouTube: Searching '$query'")

            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(PKG_YOUTUBE)
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                service.startActivity(intent)
                true
            } catch (e: Exception) {
                openAppByPackage(PKG_YOUTUBE)
                delay(2000)
                actionPerformer.clickByContentDescription("Search")
                delay(1000)
                actionPerformer.typeInFirstField(query)
                delay(500)
                val root = service.rootInActiveWindow
                val fields = ScreenReader.findEditableFields(root)
                if (fields.isNotEmpty()) {
                    fields[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "YouTube error: ${e.message}", e)
            return false
        }
    }

    // ═══════════════════════════════════════════════════════
    // CHROME / BROWSER
    // ═══════════════════════════════════════════════════════

    /**
     * Browser mein URL kholo
     */
    fun openUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Browser error: ${e.message}", e)
            false
        }
    }

    /**
     * Google search karo
     */
    fun searchGoogle(query: String): Boolean {
        return openUrl("https://www.google.com/search?q=${Uri.encode(query)}")
    }

    // ═══════════════════════════════════════════════════════
    // TELEGRAM AUTOMATION
    // ═══════════════════════════════════════════════════════

    /**
     * Telegram mein kisi ko message bhejo
     */
    suspend fun sendTelegramMessage(contact: String, message: String): Boolean {
        try {
            Log.d(TAG, "Telegram: Sending to $contact: $message")

            openAppByPackage(PKG_TELEGRAM)
            delay(2000)

            if (!actionPerformer.clickByContentDescription("Search")) {
                actionPerformer.clickByText("Search")
            }
            delay(1000)

            actionPerformer.typeInFirstField(contact)
            delay(2000)

            actionPerformer.clickByText(contact)
            delay(1500)

            actionPerformer.typeInFirstField(message)
            delay(500)

            return actionPerformer.clickByContentDescription("Send") || actionPerformer.clickByText("Send")
        } catch (e: Exception) {
            Log.e(TAG, "Telegram error: ${e.message}", e)
            return false
        }
    }

    // ═══════════════════════════════════════════════════════
    // INSTAGRAM AUTOMATION
    // ═══════════════════════════════════════════════════════

    /**
     * Instagram DM bhejo
     */
    suspend fun sendInstagramDM(contact: String, message: String): Boolean {
        try {
            Log.d(TAG, "Instagram: DM to $contact: $message")

            openAppByPackage(PKG_INSTAGRAM)
            delay(2500)

            if (!actionPerformer.clickByContentDescription("Direct")) {
                actionPerformer.clickByContentDescription("Messenger")
            }
            delay(1500)

            if (!actionPerformer.clickByText("Search")) {
                actionPerformer.clickByContentDescription("Search")
            }
            delay(1000)

            actionPerformer.typeInFirstField(contact)
            delay(2000)

            actionPerformer.clickByText(contact)
            delay(1500)

            actionPerformer.typeInFirstField(message)
            delay(500)

            return actionPerformer.clickByContentDescription("Send") || actionPerformer.clickByText("Send")
        } catch (e: Exception) {
            Log.e(TAG, "Instagram DM error: ${e.message}", e)
            return false
        }
    }

    // ═══════════════════════════════════════════════════════
    // GOOGLE MAPS NAVIGATION
    // ═══════════════════════════════════════════════════════

    /**
     * Google Maps mein navigate karo
     */
    fun navigateTo(destination: String): Boolean {
        return try {
            val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(PKG_MAPS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            Log.d(TAG, "🗺️ Navigating to: $destination")
            true
        } catch (e: Exception) {
            try {
                val searchUri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
                val searchIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                service.startActivity(searchIntent)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Maps error: ${ex.message}", ex)
                false
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // PLAY STORE
    // ═══════════════════════════════════════════════════════

    /**
     * Play Store mein app search karo
     */
    fun searchPlayStore(appName: String): Boolean {
        return try {
            val uri = Uri.parse("market://search?q=${Uri.encode(appName)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            Log.d(TAG, "🏪 Play Store: searching '$appName'")
            true
        } catch (e: Exception) {
            openUrl("https://play.google.com/store/search?q=${Uri.encode(appName)}")
        }
    }

    // ═══════════════════════════════════════════════════════
    // UTILITY ACTIONS
    // ═══════════════════════════════════════════════════════

    /**
     * Content description se element click karo
     */
    fun clickByContentDescription(desc: String): Boolean = actionPerformer.clickByContentDescription(desc)

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
