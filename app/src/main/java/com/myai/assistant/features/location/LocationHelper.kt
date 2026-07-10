// File: app/src/main/java/com/myai/assistant/features/location/LocationHelper.kt
// Location Helper — GPS location

package com.myai.assistant.features.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val city: String = "",
    val area: String = ""
)

@Singleton
class LocationHelper @Inject constructor() {

    companion object {
        private const val TAG = "LocationHelper"

        @Volatile
        var lastCachedLocation: LocationInfo? = null
    }

    /**
     * Current location lo (one-shot)
     */
    suspend fun getCurrentLocation(context: Context): LocationInfo? {
        val cached = lastCachedLocation
        if (cached != null) {
            Log.d(TAG, "⚡ Returning cached location instantly: ${cached.latitude}, ${cached.longitude}")
            return cached
        }

        return suspendCancellableCoroutine { cont ->
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val client = LocationServices.getFusedLocationProviderClient(context)

            // Last known location try karo (fast)
            client.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val info = locationToInfo(context, location.latitude, location.longitude)
                    lastCachedLocation = info
                    Log.d(TAG, "📍 Location: ${info.latitude}, ${info.longitude} - ${info.address}")
                    cont.resume(info)
                } else {
                    // Fresh location request karo
                    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                        .setMaxUpdates(1)
                        .build()

                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            client.removeLocationUpdates(this)
                            val loc = result.lastLocation
                            if (loc != null) {
                                val info = locationToInfo(context, loc.latitude, loc.longitude)
                                lastCachedLocation = info
                                cont.resume(info)
                            } else {
                                cont.resume(null)
                            }
                        }
                    }
                    client.requestLocationUpdates(request, callback, context.mainLooper)
                }
            }.addOnFailureListener {
                Log.e(TAG, "Location failed: ${it.message}")
                cont.resume(null)
            }
        }
    }

    fun updateCachedLocation(context: Context, latitude: Double, longitude: Double): LocationInfo {
        val info = locationToInfo(context, latitude, longitude)
        lastCachedLocation = info
        return info
    }

    private fun locationToInfo(context: Context, lat: Double, lng: Double): LocationInfo {
        return try {
            val geocoder = Geocoder(context, Locale("hi", "IN"))
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val addr = addresses?.firstOrNull()
            LocationInfo(
                latitude = lat, longitude = lng,
                address = addr?.getAddressLine(0) ?: "$lat, $lng",
                city = addr?.locality ?: "",
                area = addr?.subLocality ?: ""
            )
        } catch (e: Exception) {
            LocationInfo(lat, lng, address = "$lat, $lng")
        }
    }
}
