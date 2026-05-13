// File: app/src/main/java/com/myai/assistant/permissions/PermissionManager.kt
// Permission Manager — Sabhi permissions ka ek jagah check/request handler

package com.myai.assistant.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.myai.assistant.accessibility.MyAccessibilityService

/**
 * Permission Groups — Har group mein related permissions hain
 * UI pe inhe category-wise dikhayenge
 */
data class PermissionGroup(
    val name: String,           // Display name
    val icon: String,           // Emoji icon
    val description: String,    // Hindi description
    val permissions: List<String>, // Android permission strings
    val isSpecial: Boolean = false, // Kya yeh special permission hai?
    val specialType: SpecialPermissionType? = null
)

enum class SpecialPermissionType {
    ACCESSIBILITY,
    OVERLAY,
    NOTIFICATION_LISTENER,
    USAGE_ACCESS,
    MANAGE_STORAGE
}

object PermissionManager {

    // ═══════════════════════════════════════════════════════
    // RUNTIME PERMISSION GROUPS (dangerous permissions)
    // ═══════════════════════════════════════════════════════

    fun getRuntimePermissionGroups(): List<PermissionGroup> {
        val groups = mutableListOf<PermissionGroup>()

        // 🎤 Microphone
        groups.add(
            PermissionGroup(
                name = "Microphone",
                icon = "🎤",
                description = "Aapki awaaz sunne ke liye — voice commands",
                permissions = listOf(Manifest.permission.RECORD_AUDIO)
            )
        )

        // 📸 Camera
        groups.add(
            PermissionGroup(
                name = "Camera",
                icon = "📸",
                description = "Photo lena, cheezein pehchaanna — ML Kit",
                permissions = listOf(Manifest.permission.CAMERA)
            )
        )

        // 👥 Contacts
        groups.add(
            PermissionGroup(
                name = "Contacts",
                icon = "👥",
                description = "Contacts padhna aur calls karna",
                permissions = listOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.GET_ACCOUNTS
                )
            )
        )

        // 📞 Phone / Calls
        groups.add(
            PermissionGroup(
                name = "Phone & Calls",
                icon = "📞",
                description = "Calls karna, call log padhna",
                permissions = buildList {
                    add(Manifest.permission.CALL_PHONE)
                    add(Manifest.permission.READ_CALL_LOG)
                    add(Manifest.permission.WRITE_CALL_LOG)
                    add(Manifest.permission.READ_PHONE_STATE)
                    add(Manifest.permission.ANSWER_PHONE_CALLS)
                    add(Manifest.permission.ADD_VOICEMAIL)
                    add(Manifest.permission.USE_SIP)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        add(Manifest.permission.READ_PHONE_NUMBERS)
                    }
                }
            )
        )

        // 💬 SMS
        groups.add(
            PermissionGroup(
                name = "SMS & Messages",
                icon = "💬",
                description = "SMS padhna, bhejnaa aur receive karna",
                permissions = listOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.RECEIVE_MMS,
                    Manifest.permission.RECEIVE_WAP_PUSH
                )
            )
        )

        // 📍 Location
        groups.add(
            PermissionGroup(
                name = "Location",
                icon = "📍",
                description = "Aapki location jaanne ke liye",
                permissions = buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    // Background location alag se maangna padta hai
                }
            )
        )

        // 📁 Storage / Media
        groups.add(
            PermissionGroup(
                name = "Files & Media",
                icon = "📁",
                description = "Files aur media access karna",
                permissions = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Android 13+ — granular media permissions
                        add(Manifest.permission.READ_MEDIA_IMAGES)
                        add(Manifest.permission.READ_MEDIA_VIDEO)
                        add(Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        // Older Android
                        add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                }
            )
        )

        // 🔔 Notifications (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            groups.add(
                PermissionGroup(
                    name = "Notifications",
                    icon = "🔔",
                    description = "Notifications dikhane ke liye",
                    permissions = listOf(Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }

        // 🏃 Activity & Body Sensors
        groups.add(
            PermissionGroup(
                name = "Activity & Sensors",
                icon = "🏃",
                description = "Activity recognition aur body sensors",
                permissions = buildList {
                    add(Manifest.permission.ACTIVITY_RECOGNITION)
                    add(Manifest.permission.BODY_SENSORS)
                }
            )
        )

        // 📡 Bluetooth & Nearby
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            groups.add(
                PermissionGroup(
                    name = "Bluetooth & Nearby",
                    icon = "📡",
                    description = "Bluetooth devices aur nearby devices",
                    permissions = buildList {
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.NEARBY_WIFI_DEVICES)
                        }
                    }
                )
            )
        }

        // 📍 Background Location (alag se — pehle foreground location milni chahiye)
        groups.add(
            PermissionGroup(
                name = "Background Location",
                icon = "🌐",
                description = "Background mein bhi location track karna",
                permissions = listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            )
        )

        return groups
    }

    // ═══════════════════════════════════════════════════════
    // SPECIAL PERMISSION GROUPS (Settings se enable karna)
    // ═══════════════════════════════════════════════════════

    fun getSpecialPermissionGroups(): List<PermissionGroup> {
        return listOf(
            PermissionGroup(
                name = "Accessibility Service",
                icon = "♿",
                description = "Screen padhna, apps control karna — SABSE ZAROORI",
                permissions = emptyList(),
                isSpecial = true,
                specialType = SpecialPermissionType.ACCESSIBILITY
            ),
            PermissionGroup(
                name = "Draw Over Apps (Overlay)",
                icon = "🪟",
                description = "Floating bubble har jagah dikhana",
                permissions = emptyList(),
                isSpecial = true,
                specialType = SpecialPermissionType.OVERLAY
            ),
            PermissionGroup(
                name = "Notification Access",
                icon = "🔔",
                description = "Doosre apps ki notifications padhna",
                permissions = emptyList(),
                isSpecial = true,
                specialType = SpecialPermissionType.NOTIFICATION_LISTENER
            ),
            PermissionGroup(
                name = "Usage Access",
                icon = "📊",
                description = "App usage stats dekhna",
                permissions = emptyList(),
                isSpecial = true,
                specialType = SpecialPermissionType.USAGE_ACCESS
            ),
            PermissionGroup(
                name = "All Files Access",
                icon = "🗂️",
                description = "Sabhi files manage karna",
                permissions = emptyList(),
                isSpecial = true,
                specialType = SpecialPermissionType.MANAGE_STORAGE
            )
        )
    }

    // ═══════════════════════════════════════════════════════
    // CHECK FUNCTIONS — Permission granted hai ya nahi?
    // ═══════════════════════════════════════════════════════

    /**
     * Ek permission check karo
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Permission group ke saare permissions check karo
     * Returns: granted count / total count
     */
    fun getGroupStatus(context: Context, group: PermissionGroup): Pair<Int, Int> {
        if (group.isSpecial) {
            val granted = isSpecialPermissionGranted(context, group.specialType!!)
            return if (granted) Pair(1, 1) else Pair(0, 1)
        }

        val total = group.permissions.size
        val granted = group.permissions.count { isPermissionGranted(context, it) }
        return Pair(granted, total)
    }

    /**
     * Kya group ke SAARE permissions granted hain?
     */
    fun isGroupFullyGranted(context: Context, group: PermissionGroup): Boolean {
        val (granted, total) = getGroupStatus(context, group)
        return granted == total && total > 0
    }

    // ═══════════════════════════════════════════════════════
    // SPECIAL PERMISSION CHECKS
    // ═══════════════════════════════════════════════════════

    fun isSpecialPermissionGranted(context: Context, type: SpecialPermissionType): Boolean {
        return when (type) {
            SpecialPermissionType.ACCESSIBILITY -> isAccessibilityEnabled(context)
            SpecialPermissionType.OVERLAY -> isOverlayEnabled(context)
            SpecialPermissionType.NOTIFICATION_LISTENER -> isNotificationListenerEnabled(context)
            SpecialPermissionType.USAGE_ACCESS -> isUsageAccessEnabled(context)
            SpecialPermissionType.MANAGE_STORAGE -> isManageStorageEnabled()
        }
    }

    /**
     * Accessibility Service on hai ya nahi?
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    /**
     * Overlay permission (Draw over other apps)
     */
    fun isOverlayEnabled(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Notification Listener Service enabled hai?
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return flat?.contains(context.packageName) == true
    }

    /**
     * Usage Access permission
     */
    fun isUsageAccessEnabled(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Manage All Files (Android 11+)
     */
    fun isManageStorageEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Older versions don't need this
        }
    }

    // ═══════════════════════════════════════════════════════
    // SETTINGS INTENTS — Special permissions ke liye
    // ═══════════════════════════════════════════════════════

    fun getSpecialPermissionIntent(context: Context, type: SpecialPermissionType): Intent {
        return when (type) {
            SpecialPermissionType.ACCESSIBILITY -> {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
            SpecialPermissionType.OVERLAY -> {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            }
            SpecialPermissionType.NOTIFICATION_LISTENER -> {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            SpecialPermissionType.USAGE_ACCESS -> {
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            }
            SpecialPermissionType.MANAGE_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // OVERALL STATUS
    // ═══════════════════════════════════════════════════════

    /**
     * Total kitni permissions granted hain (percentage)
     */
    fun getOverallProgress(context: Context): Float {
        val runtimeGroups = getRuntimePermissionGroups()
        val specialGroups = getSpecialPermissionGroups()
        val allGroups = runtimeGroups + specialGroups

        var totalGranted = 0
        var totalCount = 0

        for (group in runtimeGroups) {
            val (granted, total) = getGroupStatus(context, group)
            totalGranted += granted
            totalCount += total
        }

        for (group in specialGroups) {
            totalCount++
            if (isSpecialPermissionGranted(context, group.specialType!!)) {
                totalGranted++
            }
        }

        return if (totalCount > 0) totalGranted.toFloat() / totalCount.toFloat() else 0f
    }

    /**
     * Sabhi critical permissions granted hain? (Mic, Camera, Accessibility)
     */
    fun areCriticalPermissionsGranted(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.RECORD_AUDIO) &&
                isAccessibilityEnabled(context)
    }
}
