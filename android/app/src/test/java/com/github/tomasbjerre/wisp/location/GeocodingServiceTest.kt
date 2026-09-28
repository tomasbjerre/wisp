package com.github.tomasbjerre.wisp.location

import android.location.Address
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * [pickCityName] is the pure/testable piece extracted out of [GeocodingService] (which
 * itself just calls the platform's Geocoder — see specs/permissions-and-privacy.md).
 * Robolectric is only needed here because [Address] is an Android SDK class.
 */
@RunWith(RobolectricTestRunner::class)
class GeocodingServiceTest {
    @Test
    fun `a sub-locality is preferred over the locality`() {
        val address =
            Address(Locale.getDefault()).apply {
                subLocality = "Trossö"
                locality = "Karlskrona"
                adminArea = "Blekinge"
            }

        assertThat(pickCityName(listOf(address))).isEqualTo("Trossö")
    }

    @Test
    fun `a sub-locality on a later candidate beats a locality on the closest match`() {
        val closest = Address(Locale.getDefault()).apply { locality = "Karlskrona" }
        val nearby =
            Address(Locale.getDefault()).apply {
                subLocality = "Trossö"
                locality = "Karlskrona"
            }

        assertThat(pickCityName(listOf(closest, nearby))).isEqualTo("Trossö")
    }

    @Test
    fun `a blank sub-locality falls back to the locality`() {
        val address =
            Address(Locale.getDefault()).apply {
                subLocality = " "
                locality = "Karlskrona"
            }

        assertThat(pickCityName(listOf(address))).isEqualTo("Karlskrona")
    }

    @Test
    fun `a locality is preferred over broader place names`() {
        val address =
            Address(Locale.getDefault()).apply {
                locality = "Stockholm"
                subAdminArea = "Stockholm County"
                adminArea = "Stockholm"
            }

        assertThat(pickCityName(listOf(address))).isEqualTo("Stockholm")
    }

    @Test
    fun `subAdminArea is used when there is no locality`() {
        val address =
            Address(Locale.getDefault()).apply {
                subAdminArea = "Some County"
                adminArea = "Some Region"
            }

        assertThat(pickCityName(listOf(address))).isEqualTo("Some County")
    }

    @Test
    fun `adminArea is the last resort`() {
        val address = Address(Locale.getDefault()).apply { adminArea = "Some Region" }

        assertThat(pickCityName(listOf(address))).isEqualTo("Some Region")
    }

    @Test
    fun `blank fields are skipped rather than returned as the city name`() {
        val address =
            Address(Locale.getDefault()).apply {
                locality = ""
                adminArea = "Some Region"
            }

        assertThat(pickCityName(listOf(address))).isEqualTo("Some Region")
    }

    @Test
    fun `no candidates at all yields no city name`() {
        assertThat(pickCityName(emptyList())).isNull()
    }

    @Test
    fun `a locality on a later candidate beats a broader field on the closest match`() {
        val closest = Address(Locale.getDefault()).apply { adminArea = "Blekinge län" }
        val nearby =
            Address(Locale.getDefault()).apply {
                locality = "Karlskrona"
                adminArea = "Blekinge län"
            }

        assertThat(pickCityName(listOf(closest, nearby))).isEqualTo("Karlskrona")
    }
}
