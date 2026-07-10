// File: app/src/main/java/com/myai/assistant/service/LocationTrackingService.kt
// Location Tracking — Background location updates

package com.myai.assistant.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.myai.assistant.MyAIApp
import com.myai.assistant.features.location.LocationHelper
import com.myai.assistant.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Background location tracking service
 * Continuous location updates ke liye
 */
@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var locationHelper: LocationHelper

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val TAG = "LocationService"
        private const val NOTIFICATION_ID = 3001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, LocationTrackingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "✅ Location tracking service created")
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "⚠️ Location permissions not granted for background tracking")
            return
        }

        try {
            val client = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient = client

            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 300_000L) // 5 mins
                .setMinUpdateIntervalMillis(60_000L) // 1 min
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    Log.d(TAG, "📍 New background location: ${loc.latitude}, ${loc.longitude}")
                    serviceScope.launch {
                        try {
                            val info = locationHelper.updateCachedLocation(applicationContext, loc.latitude, loc.longitude)
                            Log.d(TAG, "📍 Updated cache address: ${info.address}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error geocoding location: ${e.message}", e)
                        }
                    }
                }
            }
            locationCallback = callback

            client.requestLocationUpdates(request, callback, mainLooper)
            Log.d(TAG, "🔄 Registered location updates (5-min interval)")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while requesting location updates: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        locationCallback?.let { callback ->
            Log.d(TAG, "Removing location updates from fused location client")
            fusedLocationClient?.removeLocationUpdates(callback)
        }
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "❌ Location tracking stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, MyAIApp.CHANNEL_SERVICE)
            .setContentTitle("📍 Location Active")
            .setContentText("AI is tracking your location")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(
                PendingIntent.getActivity(this, 0,
                    Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            .setOngoing(true).setSilent(true).build()
    }
}
