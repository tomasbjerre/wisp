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
    /** See specs/tracking.md#noise. */
    val isNoise: Boolean,
    /** See specs/tracking.md#noise-reasons. Null on a non-noise point. */
    val noiseReason: String? = null,
)

/** See specs/tracking.md#noise-reasons. Declaration order is the order reasons are listed in. */
enum class NoiseReason(
    val token: String,
) {
    POOR_ACCURACY("poor_accuracy"),
    IMPLAUSIBLE_JUMP("implausible_jump"),
    MIN_MOVEMENT("min_movement"),
    BEFORE_MOVEMENT("before_movement"),
    PAUSED("paused"),
    ;

    companion object {
        /** The stored form of [reasons]: their tokens `|`-separated, or null if there are none. */
        fun join(reasons: Collection<NoiseReason>): String? =
            reasons
                .distinct()
                .sortedBy { it.ordinal }
                .joinToString("|") { it.token }
                .ifEmpty { null }
    }
}

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

    /**
     * Always returns a point for [fix] — see specs/tracking.md#noise: what used to be
     * silently dropped is now stored, flagged [RecordedPoint.isNoise], instead. A noise
     * point never becomes the baseline ([lastAccepted]/[currentSpeedMps]) for judging
     * later fixes — exactly as if it had never been recorded, same as before this only
     * stored what wasn't noise.
     */
    fun accept(fix: LocationFix): RecordedPoint {
        val previous = lastAccepted
        val isSegmentStart = segmentPending
        val movedMeters =
            if (previous == null) {
                Double.MAX_VALUE
            } else {
                GeoUtils.haversineMeters(previous.latitude, previous.longitude, fix.latitude, fix.longitude)
            }
        val elapsedSeconds = if (previous != null) (fix.timestampMillis - previous.timestampMillis) / 1000.0 else 0.0

        val reasons = noiseReasons(fix, isSegmentStart, movedMeters, elapsedSeconds)
        val isNoise = reasons.isNotEmpty()

        if (!isNoise) {
            segmentPending = false

            val rawSpeed =
                fix.speedMps?.toDouble()
                    ?: if (previous != null && !isSegmentStart) {
                        if (elapsedSeconds > 0) movedMeters / elapsedSeconds else 0.0
                    } else {
                        0.0
                    }
            smoothedSpeedMps =
                if (isSegmentStart) rawSpeed else smoothedSpeedMps * (1 - SPEED_SMOOTHING) + rawSpeed * SPEED_SMOOTHING
        }

        val recorded =
            RecordedPoint(
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyMeters,
                speedMps = fix.speedMps,
                timestampMillis = fix.timestampMillis,
                segmentStart = isSegmentStart,
                isNoise = isNoise,
                noiseReason = NoiseReason.join(reasons),
            )
        if (!isNoise) lastAccepted = recorded
        return recorded
    }

    /**
     * Every check [fix] fails — see specs/tracking.md#noise-reasons; empty means it's not
     * noise. The jump and minimum-movement checks only compare against the previous point
     * within the same segment, so a segment's first point is exempt from both.
     */
    private fun noiseReasons(
        fix: LocationFix,
        isSegmentStart: Boolean,
        movedMeters: Double,
        elapsedSeconds: Double,
    ): List<NoiseReason> =
        buildList {
            if (fix.accuracyMeters > MAX_ACCEPTABLE_ACCURACY_METERS) add(NoiseReason.POOR_ACCURACY)
            if (!isSegmentStart && isImplausibleJump(movedMeters, elapsedSeconds)) add(NoiseReason.IMPLAUSIBLE_JUMP)
            if (!isSegmentStart && movedMeters < MIN_MOVEMENT_METERS) add(NoiseReason.MIN_MOVEMENT)
        }

    /**
     * See specs/tracking.md#location-sampling: a jump this fast is a GPS glitch, not real
     * movement. Only ever checked against the previous point within the same segment (the
     * caller guards on `!isSegmentStart`) — a teleport-on-resume across a pause is expected
     * and handled separately, not a glitch.
     */
    private fun isImplausibleJump(
        movedMeters: Double,
        elapsedSeconds: Double,
    ): Boolean {
        val impliedSpeedMps = if (elapsedSeconds > 0) movedMeters / elapsedSeconds else Double.MAX_VALUE
        return impliedSpeedMps > MAX_PLAUSIBLE_SPEED_MPS
    }

    companion object {
        /** See specs/tracking.md#location-sampling. */
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 30f
        const val MIN_MOVEMENT_METERS = 3.0
        const val SPEED_SMOOTHING = 0.3

        /** ~200 km/h — generous on purpose, see specs/tracking.md#location-sampling. */
        const val MAX_PLAUSIBLE_SPEED_MPS = 55.0
    }
}
