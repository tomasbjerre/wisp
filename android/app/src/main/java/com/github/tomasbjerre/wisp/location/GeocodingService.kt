package com.github.tomasbjerre.wisp.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale

/**
 * Reverse-geocodes a coordinate to its nearest city name via the platform's Geocoder.
 * This is the one exception to Wisp's on-device-only data handling — see
 * specs/permissions-and-privacy.md#data-handling — since most devices resolve this by
 * calling a remote geocoding service.
 */
class GeocodingService(
    private val context: Context,
) {
    suspend fun nearestCity(
        latitude: Double,
        longitude: Double,
    ): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = runCatching { lookup(geocoder, latitude, longitude) }.getOrNull()
        return pickCityName(addresses?.firstOrNull())
    }

    private suspend fun lookup(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): List<Address>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1) { continuation.resumeWith(Result.success(it)) }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1)
        }
}

/** Picks the best available place name from a geocoded address, or null if none is usable. */
fun pickCityName(address: Address?): String? =
    address?.locality?.takeIf { it.isNotBlank() }
        ?: address?.subAdminArea?.takeIf { it.isNotBlank() }
        ?: address?.adminArea?.takeIf { it.isNotBlank() }
