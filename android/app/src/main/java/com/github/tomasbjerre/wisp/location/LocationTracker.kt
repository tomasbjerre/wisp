package com.github.tomasbjerre.wisp.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Thin wrapper around FusedLocationProviderClient. See
 * specs/tracking.md#location-sampling for the sampling targets this
 * configures.
 */
class LocationTracker(
    context: Context,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission") // caller is required to have checked permission first
    fun start(onLocation: (Location) -> Unit) {
        stop()
        val request =
            LocationRequest
                .Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MILLIS)
                .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MILLIS)
                .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
                .build()
        val newCallback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let(onLocation)
                }
            }
        callback = newCallback
        client.requestLocationUpdates(request, newCallback, Looper.getMainLooper())
    }

    fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }

    companion object {
        private const val UPDATE_INTERVAL_MILLIS = 3_000L
        private const val MIN_UPDATE_INTERVAL_MILLIS = 2_000L
        private const val MIN_UPDATE_DISTANCE_METERS = 5f
    }
}
