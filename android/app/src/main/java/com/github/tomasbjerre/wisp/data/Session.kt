package com.github.tomasbjerre.wisp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.tomasbjerre.wisp.util.CaloriesCalculator

/**
 * See specs/data-model.md#session. Aggregate fields are stored (not
 * recomputed on read) so the history list stays cheap to render.
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Long = 0,
    val averageSpeedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val nearestCity: String? = null,
    val steps: Long = 0,
    /** See specs/heart-rate.md#recording. Null when no heart rate reading was ever received. */
    val maxHeartRateBpm: Int? = null,
    /** An [ActivityType] id — see specs/calories.md#activity-type. Null on a session recorded before this existed. */
    val activityType: String? = null,
    /** The weight in effect when the session started — see specs/calories.md#weight. Null if none. */
    val weightKg: Double? = null,
) {
    /**
     * See specs/calories.md#calculation and #where-it-is-shown: null unless the session has both
     * an activity type and a stored weight, and some duration to calculate over.
     */
    fun kilocalories(): Double? {
        val activity = ActivityType.fromId(activityType) ?: return null
        val weight = weightKg ?: return null
        return CaloriesCalculator.kilocalories(activity, weight, distanceMeters, durationSeconds)
    }
}
