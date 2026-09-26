package com.github.tomasbjerre.wisp.ui

import com.github.tomasbjerre.wisp.data.UnitSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies the summary stats required by specs/ui-flows.md render sensibly. */
class FormattingTest {
    @Test
    fun `distances under a kilometer are shown in meters`() {
        assertThat(Formatting.distance(250.0, UnitSystem.METRIC)).isEqualTo("250 m")
    }

    @Test
    fun `distances of a kilometer or more are shown in kilometers`() {
        assertThat(Formatting.distance(1500.0, UnitSystem.METRIC)).isEqualTo("1.50 km")
    }

    @Test
    fun `distances under a mile are shown in feet under imperial`() {
        assertThat(Formatting.distance(100.0, UnitSystem.IMPERIAL)).isEqualTo("328 ft")
    }

    @Test
    fun `distances of a mile or more are shown in miles under imperial`() {
        // 2 miles, not 2 km re-expressed as "1.24 mi".
        assertThat(Formatting.distance(2 * UnitSystem.METERS_PER_MILE, UnitSystem.IMPERIAL)).isEqualTo("2.00 mi")
    }

    @Test
    fun `speed is converted from meters per second to kilometers per hour`() {
        assertThat(Formatting.speed(5.0, UnitSystem.METRIC)).isEqualTo("18.0 km/h")
    }

    @Test
    fun `speed is converted from meters per second to miles per hour under imperial`() {
        assertThat(Formatting.speed(5.0, UnitSystem.IMPERIAL)).isEqualTo("11.2 mph")
    }

    @Test
    fun `pace appends the unit's own abbreviation`() {
        assertThat(Formatting.pace(337, UnitSystem.METRIC)).isEqualTo("5:37/km")
        assertThat(Formatting.pace(337, UnitSystem.IMPERIAL)).isEqualTo("5:37/mi")
    }

    @Test
    fun `durations under an hour omit the hours component`() {
        assertThat(Formatting.duration(309)).isEqualTo("5:09")
    }

    @Test
    fun `durations of an hour or more include the hours component`() {
        assertThat(Formatting.duration(3605)).isEqualTo("1:00:05")
    }
}
