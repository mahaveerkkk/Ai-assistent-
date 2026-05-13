// File: app/src/main/java/com/myai/assistant/accessibility/AppAutomator.kt
// App Automator — High-level automation for popular apps

package com.myai.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * App Automator — Popular apps ko automate karo
 * WhatsApp mein message bhejo, YouTube mein search karo, etc.
 */
class AppAutomator(
    private val service: AccessibilityService,
    private val actionPerformer: ActionPerformer
) {
    companion object {
        private const val TAG = "AppAutomator"

        // Popular app package names
        const val PKG_WHATSAPP = "com.whatsapp"
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
            Log.e(TAG, "Failed to open $packageName: ${e.message}")
            false
        }
    }

    /**
     * App name ko package name mein convert karo
     */
    private fun resolvePackageName(appName: String): String? {
        val lower = appName.lowercase().trim()
        return when {
            lower.containsAny("whatsapp", "whats app", "wp") -> PKG_WHATSAPP
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
     * WhatsApp mein kisi ko message bhejo
     * @param contact Contact name ya number
     * @param message Message text
     */
    suspend fun sendWhatsAppMessage(contact: String, message: String): Boolean {
        try {
            Log.d(TAG, "WhatsApp: Sending to $contact: $message")

            // WhatsApp kholo via deep link (number ho toh)
            if (contact.matches(Regex(".*\\d{10,}.*"))) {
                val number = contact.replace(Regex("[^0-9+]"), "")
                val uri = Uri.parse("https://wa.me/$number?text=${Uri.encode(message)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                service.startActivity(intent)
                delay(3000)

                // Send button click
                return actionPerformer.clickByContentDescription("Send")
            }

            // Name se contact dhundo
            openAppByPackage(PKG_WHATSAPP)
            delay(2000)

            // Search icon click
            actionPerformer.clickByContentDescription("Search")
            delay(1000)

            // Contact name type karo
            actionPerformer.typeInFirstField(contact)
            delay(2000)

            // Contact pe click karo (search result)
            actionPerformer.clickByText(contact)
            delay(1500)

            // Message type karo
            actionPerformer.typeInFieldWithHint("Type a message", message)
            delay(500)

            // Send button click
            return actionPerformer.clickByContentDescription("Send")

        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp error: ${e.message}", e)
            return false
        }
    }

    // ═══════════════════════════════════════════════════════
    // YOUTUBE AUTOMATION
    // ═══════════════════════════════════════════════════════

    /**
     * YouTube mein kuch search karo
     */
    suspend fun searchYouTube(query: String): Boolean {
        try {
            Log.d(TAG, "YouTube: Searching '$query'")

            // YouTube kholo with search intent
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(PKG_YOUTUBE)
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                service.startActivity(intent)
                true
            } catch (e: Exception) {
                // Fallback: manually kholo aur search karo
                openAppByPackage(PKG_YOUTUBE)
                delay(2000)
                actionPerformer.clickByContentDescription("Search")
                delay(1000)
                actionPerformer.typeInFirstField(query)
                delay(500)
                // Enter press equivalent
                val root = service.rootInActiveWindow
                val fields = ScreenReader.findEditableFields(root)
                if (fields.isNotEmpty()) {
                    // Enter key simulate — search submit
                    val args = android.os.Bundle()
                    args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, 1)
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
            Log.e(TAG, "Browser error: ${e.message}")
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
    // UTILITY ACTIONS
    // ═══════════════════════════════════════════════════════

    /**
     * Content description se element click karo
     */
    private fun ActionPerformer.clickByContentDescription(desc: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(desc)
        for (node in nodes) {
            if (node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) {
                return clickNode(node)
            }
        }
        // Text mein bhi dhundo
        return clickByText(desc)
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
