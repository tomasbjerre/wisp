package com.github.tomasbjerre.wisp.ui

import com.github.tomasbjerre.wisp.data.UnitSystem
import java.text.SimpleDateFormat
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
     * e.g. "5:37/km" or "9:03/mi". */
    fun pace(
        seconds: Long,
        unit: UnitSystem,
    ): String = "${duration(seconds)}/${unit.distanceAbbreviation}"

    /** See specs/tracking.md#step-count. Callers omit this entirely when steps is 0. */
    fun stepsPerMinute(
        steps: Long,
        durationSeconds: Long,
    ): String {
        val minutes = durationSeconds / 60.0
        val perMinute = if (minutes > 0) steps / minutes else 0.0
        return "%.0f steps/min".format(perMinute)
    }

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private val dateTimeFormat = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())

    fun dateTime(epochMillis: Long): String = dateTimeFormat.format(epochMillis)
}
