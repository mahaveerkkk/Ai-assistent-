// File: app/src/main/java/com/myai/assistant/features/device/DeviceInfoManager.kt
// Device Info Manager — Battery, RAM, Storage, Device details

package com.myai.assistant.features.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInfoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DeviceInfoManager"
    }

    /**
     * 🔋 Battery level aur status
     */
    fun getBatteryInfo(): BatteryInfo {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val percentage = if (scale > 0) (level * 100) / scale else -1

            val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                    plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                    plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val statusText = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> "Unknown"
            }

            val temperature = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

            BatteryInfo(
                percentage = percentage,
                isCharging = isCharging,
                statusText = statusText,
                temperature = temperature
            )
        } catch (e: Exception) {
            Log.e(TAG, "Battery info error: ${e.message}")
            BatteryInfo(-1, false, "Unknown", 0f)
        }
    }

    /**
     * 📱 Device details (model, brand, Android version)
     */
    fun getDeviceDetails(): DeviceDetails {
        return DeviceDetails(
            brand = Build.BRAND.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            deviceName = Build.DEVICE
        )
    }

    /**
     * 💾 Storage info (used / total)
     */
    fun getStorageInfo(): StorageInfo {
        return try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val totalBytes = statFs.totalBytes
            val freeBytes = statFs.availableBytes
            val usedBytes = totalBytes - freeBytes

            StorageInfo(
                totalGB = String.format("%.1f", totalBytes / (1024.0 * 1024 * 1024)),
                usedGB = String.format("%.1f", usedBytes / (1024.0 * 1024 * 1024)),
                freeGB = String.format("%.1f", freeBytes / (1024.0 * 1024 * 1024))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Storage info error: ${e.message}")
            StorageInfo("?", "?", "?")
        }
    }

    /**
     * 🧠 RAM info (used / total)
     */
    fun getRamInfo(): RamInfo {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val totalGB = String.format("%.1f", memInfo.totalMem / (1024.0 * 1024 * 1024))
            val freeGB = String.format("%.1f", memInfo.availMem / (1024.0 * 1024 * 1024))
            val usedGB = String.format("%.1f", (memInfo.totalMem - memInfo.availMem) / (1024.0 * 1024 * 1024))

            RamInfo(
                totalGB = totalGB,
                usedGB = usedGB,
                freeGB = freeGB,
                isLowMemory = memInfo.lowMemory
            )
        } catch (e: Exception) {
            Log.e(TAG, "RAM info error: ${e.message}")
            RamInfo("?", "?", "?", false)
        }
    }

    /**
     * 📊 Full device summary — formatted for AI response
     */
    fun getFullSummary(): String {
        val battery = getBatteryInfo()
        val device = getDeviceDetails()
        val storage = getStorageInfo()
        val ram = getRamInfo()

        return buildString {
            appendLine("📱 Device: ${device.brand} ${device.model}")
            appendLine("🤖 Android ${device.androidVersion} (SDK ${device.sdkVersion})")
            appendLine()
            appendLine("🔋 Battery: ${battery.percentage}% ${if (battery.isCharging) "⚡ Charging" else ""}")
            appendLine("🌡️ Temperature: ${battery.temperature}°C")
            appendLine()
            appendLine("💾 Storage: ${storage.usedGB}GB / ${storage.totalGB}GB used (${storage.freeGB}GB free)")
            appendLine("🧠 RAM: ${ram.usedGB}GB / ${ram.totalGB}GB used (${ram.freeGB}GB free)")
            if (ram.isLowMemory) appendLine("⚠️ Low memory warning!")
        }.trim()
    }
}

// Data classes
data class BatteryInfo(
    val percentage: Int,
    val isCharging: Boolean,
    val statusText: String,
    val temperature: Float
)

data class DeviceDetails(
    val brand: String,
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val deviceName: String
)

data class StorageInfo(
    val totalGB: String,
    val usedGB: String,
    val freeGB: String
)

data class RamInfo(
    val totalGB: String,
    val usedGB: String,
    val freeGB: String,
    val isLowMemory: Boolean
)
