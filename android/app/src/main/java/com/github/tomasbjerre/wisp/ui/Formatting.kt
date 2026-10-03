package com.github.tomasbjerre.wisp.ui

import com.github.tomasbjerre.wisp.data.UnitSystem
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

object Formatting {
    private const val SECONDS_PER_HOUR = 3_600.0

    /** See specs/units.md. Below one full unit, shown in the small unit instead of a
     * fraction of a kilometer/mile — meters under metric, feet under imperial. */
    fun distance(
        meters: Double,
        unit: UnitSystem,
    ): String {
        val perUnit = unit.splitDistanceMeters
        return if (meters >= perUnit) {
            "%.2f ${unit.distanceAbbreviation}".format(meters / perUnit)
        } else if (unit == UnitSystem.METRIC) {
            "%.0f ${unit.shortDistanceAbbreviation}".format(meters)
        } else {
            "%.0f ${unit.shortDistanceAbbreviation}".format(meters * UnitSystem.FEET_PER_METER)
        }
    }

    /** See specs/units.md. */
    fun speed(
        metersPerSecond: Double,
        unit: UnitSystem,
    ): String {
        val perHour = metersPerSecond * SECONDS_PER_HOUR / unit.splitDistanceMeters
        return "%.1f ${unit.speedAbbreviation}".format(perHour)
    }

    /** A time-per-split value (see specs/tracking.md#km-splits) with its unit suffix,
     * e.g. "5:37 min/km" or "9:03 min/mi". */
    fun pace(
        seconds: Long,
        unit: UnitSystem,
    ): String = "${duration(seconds)} min/${unit.distanceAbbreviation}"

    /** See specs/tracking.md#step-count. Callers omit this entirely when steps is 0. */
    fun stepsPerMinute(
        steps: Long,
        durationSeconds: Long,
    ): String {
        val minutes = durationSeconds / 60.0
        val perMinute = if (minutes > 0) steps / minutes else 0.0
        return "%.0f steps/min".format(perMinute)
    }

    /**
     * See specs/calories.md#calculation: whole kilocalories. A non-breaking space, so the unit
     * never wraps onto another line than its number.
     */
    fun calories(kilocalories: Double): String = "%.0f\u00A0kcal".format(kilocalories)

    /** A weight for the Weight field: no trailing zeros, at most one decimal — see specs/units.md. */
    fun weightForEditing(
        kilograms: Double,
        unit: UnitSystem,
    ): String = "%.1f".format(Locale.US, unit.kilogramsToDisplay(kilograms)).removeSuffix(".0")

    /** See specs/heart-rate.md#display. */
    fun heartRate(bpm: Int): String = "$bpm bpm"

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private val dateTimeFormat = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())

    fun dateTime(epochMillis: Long): String = dateTimeFormat.format(epochMillis)

    /**
     * See specs/ui-flows.md#1-home (issue #189): complementary to [dateTime], not a
     * replacement for it — "3 hours ago", "Yesterday", "4 days ago". Null beyond four
     * weeks, where [dateTime]'s own absolute date already says enough and a vague
     * "2 months ago" stops being more useful than it.
     *
     * Day-based buckets (Yesterday, N days/weeks ago) count calendar-day boundaries in
     * [zone], not a rolling 24-hour window — a session from 11pm yesterday reads as
     * "Yesterday" the moment it's past midnight, the same way a person would describe it,
     * not "23 hours ago" drifting to "Yesterday" an hour later.
     */
    fun relativeTime(
        epochMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        val then = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val dayDiff = ChronoUnit.DAYS.between(then.toLocalDate(), now.toLocalDate())
        return when {
            dayDiff <= 0 -> sameDayPhrase(nowMillis - epochMillis)
            dayDiff == 1L -> "Yesterday"
            dayDiff < 7 -> plural(dayDiff, "day")
            dayDiff < 28 -> plural(dayDiff / 7, "week")
            else -> null
        }
    }

    private fun sameDayPhrase(elapsedMillis: Long): String {
        val elapsedSeconds = (elapsedMillis / 1000).coerceAtLeast(0)
        val minutes = elapsedSeconds / 60
        val hours = minutes / 60
        return when {
            minutes < 1 -> "Just now"
            hours < 1 -> plural(minutes, "minute")
            else -> plural(hours, "hour")
        }
    }

    private fun plural(
        count: Long,
        unit: String,
    ) = "$count $unit" + (if (count == 1L) "" else "s") + " ago"
}
