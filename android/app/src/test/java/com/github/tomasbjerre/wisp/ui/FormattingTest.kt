package com.github.tomasbjerre.wisp.ui

import com.github.tomasbjerre.wisp.data.UnitSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

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
        assertThat(Formatting.pace(337, UnitSystem.METRIC)).isEqualTo("5:37 min/km")
        assertThat(Formatting.pace(337, UnitSystem.IMPERIAL)).isEqualTo("5:37 min/mi")
    }

    @Test
    fun `durations under an hour omit the hours component`() {
        assertThat(Formatting.duration(309)).isEqualTo("5:09")
    }

    @Test
    fun `durations of an hour or more include the hours component`() {
        assertThat(Formatting.duration(3605)).isEqualTo("1:00:05")
    }

    @Test
    fun `calories are whole kilocalories`() {
        assertThat(Formatting.calories(311.6)).isEqualTo("312\u00A0kcal")
    }

    @Test
    fun `a weight is shown in kilograms under metric and pounds under imperial`() {
        assertThat(Formatting.weightForEditing(70.0, UnitSystem.METRIC)).isEqualTo("70")
        assertThat(Formatting.weightForEditing(70.5, UnitSystem.METRIC)).isEqualTo("70.5")
        assertThat(Formatting.weightForEditing(70.0, UnitSystem.IMPERIAL)).isEqualTo("154.3")
    }

    @Test
    fun `a weight entered in pounds is stored in kilograms`() {
        assertThat(UnitSystem.IMPERIAL.displayToKilograms(154.3)).isEqualTo(
            70.0,
            org.assertj.core.api.Assertions
                .within(0.05),
        )
        assertThat(UnitSystem.METRIC.displayToKilograms(70.0)).isEqualTo(70.0)
    }

    @Test
    fun `relative time under a minute reads as just now`() {
        val now = at(2026, 10, 3, 12, 0, 0)
        assertThat(Formatting.relativeTime(now - 30_000, now, ZONE)).isEqualTo("Just now")
    }

    @Test
    fun `relative time under an hour is in minutes`() {
        val now = at(2026, 10, 3, 12, 0, 0)
        assertThat(Formatting.relativeTime(now - minutes(1), now, ZONE)).isEqualTo("1 minute ago")
        assertThat(Formatting.relativeTime(now - minutes(5), now, ZONE)).isEqualTo("5 minutes ago")
    }

    @Test
    fun `relative time on the same calendar day is in hours`() {
        // 12:00 to 00:05 the same day — just over 11 hours, not yet a new calendar day.
        val now = at(2026, 10, 3, 23, 5, 0)
        assertThat(Formatting.relativeTime(at(2026, 10, 3, 12, 0, 0), now, ZONE)).isEqualTo("11 hours ago")
    }

    @Test
    fun `relative time on the previous calendar day reads as yesterday regardless of elapsed hours`() {
        // Only ~1 hour apart, but crosses midnight.
        val now = at(2026, 10, 3, 0, 30, 0)
        assertThat(Formatting.relativeTime(at(2026, 10, 2, 23, 0, 0), now, ZONE)).isEqualTo("Yesterday")
    }

    @Test
    fun `relative time two to six calendar days back is in days`() {
        val now = at(2026, 10, 10, 8, 0, 0)
        assertThat(Formatting.relativeTime(at(2026, 10, 6, 8, 0, 0), now, ZONE)).isEqualTo("4 days ago")
    }

    @Test
    fun `relative time one to three weeks back is in weeks`() {
        val now = at(2026, 10, 24, 8, 0, 0)
        assertThat(Formatting.relativeTime(at(2026, 10, 10, 8, 0, 0), now, ZONE)).isEqualTo("2 weeks ago")
    }

    @Test
    fun `relative time beyond four weeks is omitted`() {
        val now = at(2026, 11, 3, 8, 0, 0)
        assertThat(Formatting.relativeTime(at(2026, 10, 3, 8, 0, 0), now, ZONE)).isNull()
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZONE).toInstant().toEpochMilli()

    private fun minutes(count: Long): Long = count * 60_000

    private companion object {
        val ZONE: ZoneId = ZoneOffset.UTC
    }
}
