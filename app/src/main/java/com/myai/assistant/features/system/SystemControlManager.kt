// File: app/src/main/java/com/myai/assistant/features/system/SystemControlManager.kt
// System Control Manager — Device volume, brightness, WiFi panel, Bluetooth, Flashlight, DND

package com.myai.assistant.features.system

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager as Camera2Manager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemControlManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SystemControlManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val camera2Manager = context.getSystemService(Context.CAMERA_SERVICE) as Camera2Manager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Track real flashlight state via hardware callbacks
    @Volatile
    private var isFlashlightOn = false
    private var selectedCameraId: String? = null
    private var torchCallback: Camera2Manager.TorchCallback? = null

    init {
        registerTorchCallback()
    }

    private fun registerTorchCallback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val handler = Handler(Looper.getMainLooper())
                val callback = object : Camera2Manager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        super.onTorchModeChanged(cameraId, enabled)
                        if (selectedCameraId == null || cameraId == selectedCameraId) {
                            isFlashlightOn = enabled
                            Log.d(TAG, "🔦 Torch callback: camera=$cameraId, enabled=$enabled")
                        }
                    }

                    override fun onTorchModeUnavailable(cameraId: String) {
                        super.onTorchModeUnavailable(cameraId)
                        if (selectedCameraId == null || cameraId == selectedCameraId) {
                            isFlashlightOn = false
                            Log.w(TAG, "⚠️ Torch mode unavailable for camera: $cameraId")
                        }
                    }
                }
                torchCallback = callback
                camera2Manager.registerTorchCallback(callback, handler)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register torch callback: ${e.message}")
        }
    }

    /**
     * Unregister hardware callbacks to prevent memory leaks
     */
    fun cleanup() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                torchCallback?.let {
                    camera2Manager.unregisterTorchCallback(it)
                    torchCallback = null
                    Log.d(TAG, "Torch callback unregistered cleanly")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering torch callback: ${e.message}")
        }
    }

    /**
     * Get the camera ID that supports flash unit (prefers rear-facing camera)
     */
    private fun getFlashCameraId(): String? {
        selectedCameraId?.let { return it }

        return try {
            val cameraIds = camera2Manager.cameraIdList
            var candidateId: String? = null

            for (id in cameraIds) {
                val characteristics = camera2Manager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (hasFlash) {
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        selectedCameraId = id
                        return id
                    }
                    if (candidateId == null) {
                        candidateId = id
                    }
                }
            }

            val chosen = candidateId ?: cameraIds.firstOrNull()
            selectedCameraId = chosen
            chosen
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query camera list: ${e.message}", e)
            null
        }
    }

    /**
     * Is the flashlight currently turned on?
     */
    fun isTorchActive(): Boolean = isFlashlightOn

    /**
     * 🔦 Flashlight (Torch) toggle
     * Handles CameraAccessException (camera in use, camera disabled, etc.)
     * @param enable true = ON, false = OFF
     */
    fun toggleFlashlight(enable: Boolean): Boolean {
        return try {
            val cameraId = getFlashCameraId() ?: run {
                Log.e(TAG, "No camera with flash found on this device")
                return false
            }

            camera2Manager.setTorchMode(cameraId, enable)
            isFlashlightOn = enable
            Log.d(TAG, "🔦 Flashlight turned ${if (enable) "ON" else "OFF"}")
            true
        } catch (e: CameraAccessException) {
            val errorReason = when (e.reason) {
                CameraAccessException.CAMERA_IN_USE -> "Camera in use by another app"
                CameraAccessException.MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                CameraAccessException.CAMERA_DISABLED -> "Camera disabled by device policy"
                CameraAccessException.CAMERA_DISCONNECTED -> "Camera disconnected"
                CameraAccessException.CAMERA_ERROR -> "Camera hardware error"
                else -> "Camera error: ${e.message}"
            }
            Log.e(TAG, "Flashlight toggle failed: $errorReason", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Flashlight permission denied: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight toggle failed: ${e.message}", e)
            false
        }
    }

    /**
     * Toggle flashlight based on current state
     */
    fun toggleFlashlightAuto(): Boolean {
        return toggleFlashlight(!isFlashlightOn)
    }

    /**
     * WiFi toggle / open WiFi panel
     * Android 10+ restricts direct toggling, opens panel with fallback to settings
     */
    fun toggleWifi(enable: Boolean): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Log.d(TAG, "Opening WiFi Settings Panel (Android 10+)")
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "WiFi panel not supported, opening WiFi settings")
                openWifiSettings()
            } catch (e: Exception) {
                Log.e(TAG, "WiFi panel failed: ${e.message}, falling back to settings", e)
                openWifiSettings()
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val success = wifiManager.setWifiEnabled(enable)
                if (!success) {
                    openWifiSettings()
                } else true
            } catch (e: Exception) {
                Log.w(TAG, "Legacy WiFi toggle failed: ${e.message}, opening settings", e)
                openWifiSettings()
            }
        }
    }

    fun openWifiSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WiFi settings: ${e.message}", e)
            false
        }
    }

    /**
     * Bluetooth toggle with Android 12+ (API 31+) BLUETOOTH_CONNECT permission verification
     * Gracefully falls back to system settings if permission is missing or toggle fails.
     */
    fun toggleBluetooth(enable: Boolean): Boolean {
        val adapter = bluetoothAdapter ?: run {
            Log.w(TAG, "Bluetooth not supported on this device")
            return false
        }

        // Android 12+ (API 31+) requires BLUETOOTH_CONNECT permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnectPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasConnectPermission) {
                Log.w(TAG, "BLUETOOTH_CONNECT permission not granted on Android 12+, opening settings")
                return openBluetoothSettings()
            }
        }

        return try {
            @Suppress("DEPRECATION")
            val success = if (enable) {
                if (!adapter.isEnabled) adapter.enable() else true
            } else {
                if (adapter.isEnabled) adapter.disable() else true
            }

            if (!success) {
                Log.w(TAG, "Bluetooth direct toggle returned false, opening settings")
                openBluetoothSettings()
            } else {
                Log.d(TAG, "📶 Bluetooth turned ${if (enable) "ON" else "OFF"}")
                true
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth SecurityException: ${e.message}, opening settings")
            openBluetoothSettings()
        } catch (e: Exception) {
            Log.w(TAG, "Direct Bluetooth toggle failed: ${e.message}, opening settings")
            openBluetoothSettings()
        }
    }

    fun openBluetoothSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to open Bluetooth settings: ${ex.message}", ex)
            false
        }
    }

    /**
     * Adjust media volume
     * @param direction "up", "down", "mute", "unmute", "max", "min", "zero"
     */
    fun adjustVolume(direction: String): Boolean {
        return try {
            val dir = direction.lowercase().trim()
            when {
                dir in setOf("up", "raise", "increase", "badhao", "tez") -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    true
                }
                dir in setOf("down", "lower", "decrease", "kam", "ghatao") -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                    true
                }
                dir in setOf("mute", "chup", "silent") -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    true
                }
                dir in setOf("unmute", "un-mute") -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    true
                }
                dir in setOf("max", "full", "100") -> {
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
                    true
                }
                dir in setOf("min", "zero", "0") -> {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                    true
                }
                else -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Volume adjust failed: ${e.message}", e)
            false
        }
    }

    /**
     * Brightness setting (value from 0 to 255)
     */
    fun setBrightness(value: Int): Boolean {
        return try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    value.coerceIn(0, 255)
                )
                true
            } else {
                Log.d(TAG, "Permission WRITE_SETTINGS not granted, opening Display Settings")
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Brightness change failed: ${e.message}", e)
            false
        }
    }

    /**
     * 🔇 DND / Silent / Normal mode toggle with Android N+ permission verification
     * @param mode "silent", "vibrate", "normal", "dnd_on", "dnd_off"
     */
    fun toggleDnd(mode: String): Boolean {
        return try {
            when (mode.lowercase().trim()) {
                "silent", "mute" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !notificationManager.isNotificationPolicyAccessGranted) {
                        Log.w(TAG, "DND access not granted for silent mode, setting to vibrate")
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                        true
                    } else {
                        try {
                            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                            Log.d(TAG, "🔇 Ringer set to SILENT")
                            true
                        } catch (e: SecurityException) {
                            Log.w(TAG, "SecurityException setting silent mode: ${e.message}")
                            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                            true
                        }
                    }
                }
                "vibrate" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    Log.d(TAG, "📳 Ringer set to VIBRATE")
                    true
                }
                "normal" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !notificationManager.isNotificationPolicyAccessGranted) {
                        try {
                            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                            Log.d(TAG, "🔔 Ringer set to NORMAL")
                            true
                        } catch (e: SecurityException) {
                            Log.w(TAG, "DND access needed to exit silent/DND mode, opening settings: ${e.message}")
                            openDndSettings()
                            false
                        }
                    } else {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        Log.d(TAG, "🔔 Ringer set to NORMAL")
                        true
                    }
                }
                "dnd_on", "on" -> {
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        notificationManager.setInterruptionFilter(
                            NotificationManager.INTERRUPTION_FILTER_NONE
                        )
                        Log.d(TAG, "🔇 DND turned ON")
                        true
                    } else {
                        Log.w(TAG, "DND permission not granted, opening settings")
                        openDndSettings()
                        false
                    }
                }
                "dnd_off", "off" -> {
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        notificationManager.setInterruptionFilter(
                            NotificationManager.INTERRUPTION_FILTER_ALL
                        )
                        Log.d(TAG, "🔔 DND turned OFF")
                        true
                    } else {
                        Log.w(TAG, "DND permission not granted, opening settings")
                        openDndSettings()
                        false
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown DND mode: $mode")
                    false
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException in toggleDnd: ${e.message}, opening settings")
            openDndSettings()
            false
        } catch (e: Exception) {
            Log.e(TAG, "DND toggle failed: ${e.message}", e)
            false
        }
    }

    private fun openDndSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open DND settings: ${e.message}", e)
            false
        }
    }

    /**
     * 📱 Mobile Data toggle — opens mobile data settings as fallback
     */
    fun toggleMobileData(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "📱 Opening Mobile Data settings")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Mobile data toggle failed: ${e.message}", e)
            false
        }
    }

    /**
     * ✈️ Airplane Mode toggle — opens settings
     */
    fun toggleAirplaneMode(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "✈️ Opening Airplane Mode settings")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Airplane mode toggle failed: ${e.message}", e)
            false
        }
    }

    /**
     * 📡 Hotspot toggle — opens tethering settings
     */
    fun toggleHotspot(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "📡 Opening Hotspot/Tethering settings")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Hotspot toggle failed: ${e.message}", e)
            false
        }
    }
}
