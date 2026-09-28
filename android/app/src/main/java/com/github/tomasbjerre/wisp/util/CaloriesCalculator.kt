package com.github.tomasbjerre.wisp.util

import com.github.tomasbjerre.wisp.data.ActivityType

/**
 * See specs/calories.md#calculation: `MET × weight (kg) × time (hours)`, with the MET looked
 * up from the activity type and the average speed. Pure, so it is tested directly against
 * the spec's table.
 */
object CaloriesCalculator {
    private const val SECONDS_PER_HOUR = 3_600.0
    private const val KMH_PER_MPS = 3.6

    /** (lower bound in km/h, MET) — the row with the highest bound the speed reaches wins. */
    private val walking =
        listOf(
            0.0 to 2.0,
            3.2 to 2.8,
            4.0 to 3.0,
            4.8 to 3.5,
            5.6 to 4.3,
            6.4 to 5.0,
            7.2 to 7.0,
            8.0 to 8.3,
        )
    private val running =
        listOf(
            0.0 to 6.0,
            8.0 to 8.3,
            8.4 to 9.0,
            9.7 to 9.8,
            10.8 to 10.5,
            11.3 to 11.0,
            12.1 to 11.5,
            12.9 to 11.8,
            13.8 to 12.3,
            14.5 to 12.8,
            16.1 to 14.5,
            19.3 to 19.0,
        )
    private val cycling =
        listOf(
            0.0 to 4.0,
            16.1 to 6.8,
            19.3 to 8.0,
            22.5 to 10.0,
            25.7 to 12.0,
            32.2 to 15.8,
        )

    fun met(
        activity: ActivityType,
        averageSpeedMps: Double,
    ): Double {
        val kmh = averageSpeedMps * KMH_PER_MPS
        val table =
            when (activity) {
                ActivityType.WALKING -> walking
                ActivityType.RUNNING -> running
                ActivityType.CYCLING -> cycling
            }
        return table.last { (lowerBound, _) -> kmh >= lowerBound }.second
    }

    /** Null when there is no duration to calculate over. Whole kilocalories are the caller's rounding. */
    fun kilocalories(
        activity: ActivityType,
        weightKg: Double,
        distanceMeters: Double,
        durationSeconds: Long,
    ): Double? {
        if (durationSeconds <= 0) return null
        val averageSpeedMps = distanceMeters / durationSeconds
        return met(activity, averageSpeedMps) * weightKg * (durationSeconds / SECONDS_PER_HOUR)
    }
}
