package com.github.tomasbjerre.wisp.location

/**
 * Pure decision logic for what to do with a fix that arrives while a session is paused
 * — see specs/tracking.md#session-lifecycle and #auto-pause. No Android framework types,
 * no I/O, so it can be unit tested directly, like [MovementGate] (which it wraps).
 *
 * A paused fix is never part of the track: either it's stored as noise ([Outcome.noiseReason]),
 * or — for an automatic pause only — it confirms real movement and the caller should resume
 * and treat that same fix as an ordinary one ([Outcome.shouldResume]).
 *
 * One instance covers exactly one pause — construct a fresh one each time the session pauses.
 * [autoResume] is true only for a pause the app started on its own; a manual pause never
 * ends by itself, only the person ends it.
 */
class PauseWatcher(
    autoResume: Boolean,
) {
    private val movementGate = if (autoResume) MovementGate(cumulativeDistanceOnly = true) else null

    data class Outcome(
        val shouldResume: Boolean,
        /** See specs/tracking.md#noise-reasons; null exactly when [shouldResume]. */
        val noiseReason: String?,
    )

    fun onFix(fix: LocationFix): Outcome {
        val isPoorAccuracy = fix.accuracyMeters > TrackRecorder.MAX_ACCEPTABLE_ACCURACY_METERS
        // Only trustworthy positions count toward "real movement": it's their positions that
        // get summed.
        if (!isPoorAccuracy && movementGate?.hasStartedMoving(fix) == true) {
            return Outcome(shouldResume = true, noiseReason = null)
        }
        val reasons = listOfNotNull(NoiseReason.POOR_ACCURACY.takeIf { isPoorAccuracy }, NoiseReason.PAUSED)
        return Outcome(shouldResume = false, noiseReason = NoiseReason.join(reasons))
    }
}
