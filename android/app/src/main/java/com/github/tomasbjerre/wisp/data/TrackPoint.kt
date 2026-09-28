package com.github.tomasbjerre.wisp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** See specs/data-model.md#trackpoint. */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sequence: Int,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val segmentStart: Boolean,
    /** The session's running step count when this point was recorded — see specs/data-model.md#trackpoint. */
    val steps: Long = 0,
    /** The current heart rate when recorded — see specs/heart-rate.md#recording. Null if there was none. */
    val heartRateBpm: Int? = null,
    /**
     * True if this point looks like an error (poor accuracy, an implausible jump, GPS
     * jitter) or was recorded before movement was ever confirmed (see
     * specs/tracking.md#start-gating) — see specs/data-model.md#trackpoint and
     * specs/tracking.md#noise. Excluded from everything the app itself computes or
     * shows (route, distance, speed, splits); exported as-is so a user's own tooling
     * can decide what to do with it.
     */
    val isNoise: Boolean = false,
    /** Why [isNoise] is true — see specs/tracking.md#noise-reasons. Null on a non-noise point. */
    val noiseReason: String? = null,
    /** "manual" or "auto" on a point starting a segment after a pause — see specs/data-model.md#trackpoint. */
    val pauseCause: String? = null,
)
