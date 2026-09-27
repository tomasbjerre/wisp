package com.github.tomasbjerre.wisp.location

import com.github.tomasbjerre.wisp.util.GeoUtils

/**
 * Pure decision logic for specs/tracking.md#start-gating: a freshly started
 * session shouldn't start its timer and track until the user is actually
 * moving, not just standing around right after tapping Start. No Android
 * framework types, no I/O, so it can be unit tested directly, like
 * [TrackRecorder].
 *
 * One instance covers exactly one "waiting for movement" wait — construct a
 * fresh one per session.
 */
class MovementGate {
    private var anchor: LocationFix? = null
    private var previousFix: LocationFix? = null
    private var cumulativeDistanceMeters = 0.0

    /**
     * Returns true the first time [fix] implies movement at or above
     * [MIN_WALKING_SPEED_MPS] relative to the first fix ever passed in (the
     * anchor), OR once enough real ground ([MIN_CUMULATIVE_DISTANCE_METERS])
     * has been covered fix-to-fix since then, regardless of direction. The
     * anchor-relative check alone misses a path that curves back toward the
     * start (pacing at a trailhead, walking around a parked car) — net
     * displacement from a fixed anchor can stay small even after real
     * walking, so the cumulative check catches that case (see #136).
     *
     * Deliberately always computed from position + time, never from
     * [LocationFix.speedMps] (the platform's own instantaneous speed
     * reading) — that field is a Doppler-based estimate that can spike well
     * above walking pace from multipath/signal noise while the device isn't
     * moving at all, which used to start a session with zero real
     * displacement (see specs/tracking.md#start-gating: movement is defined
     * relative to the anchor *position*, not a raw speed reading).
     */
    fun hasStartedMoving(fix: LocationFix): Boolean {
        val previous = anchor
        if (previous == null) {
            anchor = fix
            previousFix = fix
            return false
        }
        val movedFromAnchorMeters =
            GeoUtils.haversineMeters(previous.latitude, previous.longitude, fix.latitude, fix.longitude)
        val elapsedSeconds = (fix.timestampMillis - previous.timestampMillis) / 1000.0
        val impliedSpeedMps = if (elapsedSeconds > 0) movedFromAnchorMeters / elapsedSeconds else 0.0

        val lastFix = previousFix!!
        val movedFromPreviousMeters =
            GeoUtils.haversineMeters(lastFix.latitude, lastFix.longitude, fix.latitude, fix.longitude)
        // Ignore GPS-jitter-sized steps, same threshold TrackRecorder uses, so standing
        // still never accumulates into a false positive.
        if (movedFromPreviousMeters >= MIN_STEP_METERS) cumulativeDistanceMeters += movedFromPreviousMeters
        previousFix = fix

        return impliedSpeedMps >= MIN_WALKING_SPEED_MPS || cumulativeDistanceMeters >= MIN_CUMULATIVE_DISTANCE_METERS
    }

    companion object {
        /**
         * See specs/tracking.md#start-gating. Comfortably below an average
         * walking pace (~1.4 m/s / ~5 km/h), so a normal walk crosses it
         * quickly without requiring anything faster than walking.
         */
        const val MIN_WALKING_SPEED_MPS = 0.8

        /** Same jitter floor as [TrackRecorder.MIN_MOVEMENT_METERS]. */
        const val MIN_STEP_METERS = 3.0

        /** See specs/tracking.md#start-gating. */
        const val MIN_CUMULATIVE_DISTANCE_METERS = 30.0
    }
}
