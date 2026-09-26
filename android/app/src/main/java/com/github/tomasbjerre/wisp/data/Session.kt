package com.github.tomasbjerre.wisp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
)
