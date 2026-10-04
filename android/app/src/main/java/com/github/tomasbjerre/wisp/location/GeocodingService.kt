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
        val addresses = runCatching { lookup(geocoder, latitude, longitude) }.getOrNull() ?: emptyList()
        return pickCityName(addresses)
    }

    // More than one candidate: the closest match isn't always the one with a proper
    // locality filled in (e.g. a point right at a municipality boundary), but a nearby
    // candidate returned for the same coordinate often is — see [pickCityName].
    private suspend fun lookup(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): List<Address>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, MAX_RESULTS) {
                    continuation.resumeWith(Result.success(it))
                }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, MAX_RESULTS)
        }

    private companion object {
        const val MAX_RESULTS = 5
    }
}

/**
 * Picks the best available place name across every geocoded candidate, preferring the
 * most specific field (locality, then subLocality, then subAdminArea) available on ANY
 * candidate over a broader one (adminArea) on the top match — see
 * [GeocodingService.lookup] and specs/data-model.md#place-name. locality outranks
 * subLocality (a neighborhood or district) so a city name is shown whenever the
 * geocoder has one, but subLocality still beats falling all the way back to
 * subAdminArea/adminArea when the geocoder has no locality at all for a point: the
 * platform's Geocoder doesn't reliably fill in locality for every address, but it
 * fills in subLocality more consistently, so dropping it entirely regresses
 * well-known places to their county/region name instead of a neighborhood name.
 */
fun pickCityName(addresses: List<Address>): String? =
    addresses.firstNotNullOfOrNull { it.locality?.takeIf { name -> name.isNotBlank() } }
        ?: addresses.firstNotNullOfOrNull { it.subLocality?.takeIf { name -> name.isNotBlank() } }
        ?: addresses.firstNotNullOfOrNull { it.subAdminArea?.takeIf { name -> name.isNotBlank() } }
        ?: addresses.firstNotNullOfOrNull { it.adminArea?.takeIf { name -> name.isNotBlank() } }
