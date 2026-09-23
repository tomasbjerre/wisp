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

    /**
     * Returns true the first time [fix] implies movement at or above
     * [MIN_WALKING_SPEED_MPS] relative to the first fix ever passed in (the
     * anchor). The anchor is fixed, not the immediately preceding fix, so a
     * short burst of GPS jitter across a couple of samples can't accumulate
     * into a false positive the way comparing only consecutive fixes could.
     */
    fun hasStartedMoving(fix: LocationFix): Boolean {
        val previous = anchor
        if (previous == null) {
            anchor = fix
            return false
        }
        val movedMeters = GeoUtils.haversineMeters(previous.latitude, previous.longitude, fix.latitude, fix.longitude)
        val elapsedSeconds = (fix.timestampMillis - previous.timestampMillis) / 1000.0
        val impliedSpeedMps = if (elapsedSeconds > 0) movedMeters / elapsedSeconds else 0.0
        val speedMps = fix.speedMps?.toDouble() ?: impliedSpeedMps
        return speedMps >= MIN_WALKING_SPEED_MPS
    }

    companion object {
        /**
         * See specs/tracking.md#start-gating. Comfortably below an average
         * walking pace (~1.4 m/s / ~5 km/h), so a normal walk crosses it
         * quickly without requiring anything faster than walking.
         */
        const val MIN_WALKING_SPEED_MPS = 0.8
    }
}
