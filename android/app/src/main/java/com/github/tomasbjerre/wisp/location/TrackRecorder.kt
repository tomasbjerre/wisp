package com.github.tomasbjerre.wisp.location

import com.github.tomasbjerre.wisp.util.GeoUtils

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val timestampMillis: Long,
)

data class RecordedPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val timestampMillis: Long,
    val segmentStart: Boolean,
)

/**
 * Pure decision logic for turning raw location fixes into recorded track
 * points: no Android framework types, no I/O, so it can be unit tested
 * directly against specs/tracking.md#location-sampling without mocks.
 *
 * One instance covers exactly one recording session; call [pause] when the
 * session pauses so the next accepted fix is marked as a new segment (see
 * specs/data-model.md#trackpoint).
 */
class TrackRecorder {
    private var lastAccepted: RecordedPoint? = null
    private var segmentPending = true
    private var smoothedSpeedMps = 0.0

    val currentSpeedMps: Double get() = smoothedSpeedMps

    fun pause() {
        segmentPending = true
    }

    /** Returns the recorded point for [fix], or null if it was filtered out. */
    fun accept(fix: LocationFix): RecordedPoint? {
        if (fix.accuracyMeters > MAX_ACCEPTABLE_ACCURACY_METERS) return null

        val previous = lastAccepted
        val isSegmentStart = segmentPending
        val movedMeters =
            if (previous == null) {
                Double.MAX_VALUE
            } else {
                GeoUtils.haversineMeters(previous.latitude, previous.longitude, fix.latitude, fix.longitude)
            }
        if (!isSegmentStart && movedMeters < MIN_MOVEMENT_METERS) return null

        segmentPending = false

        val rawSpeed =
            fix.speedMps?.toDouble()
                ?: if (previous != null && !isSegmentStart) {
                    val seconds = (fix.timestampMillis - previous.timestampMillis) / 1000.0
                    if (seconds > 0) movedMeters / seconds else 0.0
                } else {
                    0.0
                }
        smoothedSpeedMps =
            if (isSegmentStart) rawSpeed else smoothedSpeedMps * (1 - SPEED_SMOOTHING) + rawSpeed * SPEED_SMOOTHING

        val recorded =
            RecordedPoint(
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyMeters,
                speedMps = fix.speedMps,
                timestampMillis = fix.timestampMillis,
                segmentStart = isSegmentStart,
            )
        lastAccepted = recorded
        return recorded
    }

    companion object {
        /** See specs/tracking.md#location-sampling. */
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 30f
        const val MIN_MOVEMENT_METERS = 3.0
        const val SPEED_SMOOTHING = 0.3
    }
}
