package com.github.tomasbjerre.wisp.location

import com.github.tomasbjerre.wisp.util.GeoUtils

/**
 * Pure decision logic for specs/tracking.md#auto-pause: detects a sustained
 * lack of movement while actively recording. No Android framework types, no
 * I/O, so it can be unit tested directly, like [MovementGate] (which this
 * mirrors, but for a continuously-sliding anchor instead of a fixed one — see
 * [onFix]).
 *
 * One instance covers exactly one actively-recording stretch — construct a
 * fresh one whenever recording (re)starts (initial Start, or a manual/auto
 * Resume), so idle time never carries over across a pause.
 */
class StationaryGate {
    private var lastMovingFix: LocationFix? = null

    /**
     * Returns true once [fix] shows [IDLE_THRESHOLD_MILLIS] or more has passed since
     * the last fix that implied movement at or above [MovementGate.MIN_WALKING_SPEED_MPS]
     * — unlike [MovementGate], the anchor slides forward to the latest such fix each
     * time, so this measures *sustained* stillness since it was last seen moving, not
     * cumulative drift from wherever recording began.
     */
    fun onFix(fix: LocationFix): Boolean {
        val previous = lastMovingFix
        if (previous == null) {
            lastMovingFix = fix
            return false
        }
        val movedMeters = GeoUtils.haversineMeters(previous.latitude, previous.longitude, fix.latitude, fix.longitude)
        val elapsedSeconds = (fix.timestampMillis - previous.timestampMillis) / 1000.0
        val impliedSpeedMps = if (elapsedSeconds > 0) movedMeters / elapsedSeconds else 0.0
        val speedMps = fix.speedMps?.toDouble() ?: impliedSpeedMps
        if (speedMps >= MovementGate.MIN_WALKING_SPEED_MPS) {
            lastMovingFix = fix
            return false
        }
        return fix.timestampMillis - previous.timestampMillis >= IDLE_THRESHOLD_MILLIS
    }

    companion object {
        /** See specs/tracking.md#auto-pause. */
        const val IDLE_THRESHOLD_MILLIS = 15_000L
    }
}
