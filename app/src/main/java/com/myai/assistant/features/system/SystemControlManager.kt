// File: app/src/main/java/com/myai/assistant/features/system/SystemControlManager.kt
// System Control Manager — Device volume, brightness, WiFi panel, Bluetooth, Flashlight, DND

package com.myai.assistant.features.system

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager as Camera2Manager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
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

    // Track flashlight state internally
    private var isFlashlightOn = false

    /**
     * WiFi toggle / open WiFi panel
     */
    fun toggleWifi(enable: Boolean): Boolean {
        return try {
            // Android 10+ restricts direct toggling, open settings panel
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                Log.d(TAG, "Opening WiFi Settings Panel (Android 10+)")
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } else {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = enable
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "WiFi toggle failed: ${e.message}")
            false
        }
    }

    /**
     * Bluetooth toggle
     */
    fun toggleBluetooth(enable: Boolean): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return try {
            if (enable) {
                if (!adapter.isEnabled) {
                    @Suppress("DEPRECATION")
                    adapter.enable()
                } else true
            } else {
                if (adapter.isEnabled) {
                    @Suppress("DEPRECATION")
                    adapter.disable()
                } else true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct Bluetooth toggle failed, opening settings: ${e.message}")
            // Fallback: settings open karo
            try {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (ex: Exception) {
                false
            }
        }
    }

    /**
     * Adjust media volume
     * @param direction "up", "down", "mute", "unmute"
     */
    fun adjustVolume(direction: String): Boolean {
        return try {
            val dir = when (direction.lowercase()) {
                "up", "raise" -> AudioManager.ADJUST_RAISE
                "down", "lower" -> AudioManager.ADJUST_LOWER
                "mute" -> AudioManager.ADJUST_MUTE
                "unmute" -> AudioManager.ADJUST_UNMUTE
                else -> return false
            }
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Volume adjust failed: ${e.message}")
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
            Log.e(TAG, "Brightness change failed: ${e.message}")
            false
        }
    }

    /**
     * 🔦 Flashlight (Torch) toggle
     * @param enable true = ON, false = OFF
     */
    fun toggleFlashlight(enable: Boolean): Boolean {
        return try {
            val cameraId = camera2Manager.cameraIdList.firstOrNull() ?: run {
                Log.e(TAG, "No camera found for flashlight")
                return false
            }
            camera2Manager.setTorchMode(cameraId, enable)
            isFlashlightOn = enable
            Log.d(TAG, "🔦 Flashlight ${if (enable) "ON" else "OFF"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight toggle failed: ${e.message}")
            false
        }
    }

    /**
     * 🔇 DND / Silent / Normal mode toggle
     * @param mode "silent", "vibrate", "normal", "dnd_on", "dnd_off"
     */
    fun toggleDnd(mode: String): Boolean {
        return try {
            when (mode.lowercase()) {
                "silent" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    Log.d(TAG, "🔇 Ringer set to SILENT")
                    true
                }
                "vibrate" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    Log.d(TAG, "📳 Ringer set to VIBRATE")
                    true
                }
                "normal" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    Log.d(TAG, "🔔 Ringer set to NORMAL")
                    true
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
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
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
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        false
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown DND mode: $mode")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DND toggle failed: ${e.message}")
            false
        }
    }

    /**
     * 📱 Mobile Data toggle
     * Android 10+ restricts direct toggle — opens data settings as fallback
     */
    fun toggleMobileData(): Boolean {
        return try {
            // Open mobile data settings — user can toggle from there
            // Or accessibility service can auto-click the toggle
            val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "📱 Opening Mobile Data settings")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Mobile data toggle failed: ${e.message}")
            false
        }
    }

    /**
     * ✈️ Airplane Mode toggle
     * Direct toggle not allowed since Android 4.2 — opens settings
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
            Log.e(TAG, "Airplane mode toggle failed: ${e.message}")
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
            Log.e(TAG, "Hotspot toggle failed: ${e.message}")
            false
        }
    }
}
