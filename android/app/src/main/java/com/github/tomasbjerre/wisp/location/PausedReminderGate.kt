package com.github.tomasbjerre.wisp.location

/**
 * Pure decision logic for specs/tracking.md#paused-session-reminder: the once-a-minute
 * pulse that says a session is still sitting paused, or still waiting for its first
 * movement. No Android framework types, no I/O, so it can be unit tested directly, like
 * [StationaryGate]/[PauseWatcher] — which it also matches in lifetime: one instance covers
 * exactly one paused-or-waiting stretch, constructed fresh when the session pauses or
 * starts waiting, so resuming/confirming movement and then pausing/waiting again starts a
 * fresh set of pulses.
 *
 * [suppressed] covers both ways the reminder shouldn't run: the person has the app on
 * screen (so there's nothing to remind them of — see the spec's "suppressed while the app
 * is on screen") or the setting is off. Either way the schedule *waits* rather than
 * running down or spending pulses unseen, which is why it re-anchors to "a full interval
 * from now" instead of holding its old deadline: leaving the app on screen for ten minutes
 * must not mean being pulsed on the very next second after putting it away.
 */
class PausedReminderGate(
    anchorElapsedRealtime: Long,
) {
    private var nextPulseAtElapsedRealtime = anchorElapsedRealtime + PULSE_INTERVAL_MILLIS
    private var pulsesGiven = 0

    /**
     * Returns true when a pulse is due at [nowElapsedRealtime] (a monotonic clock, e.g.
     * `SystemClock.elapsedRealtime()`), consuming one of the [MAX_PULSES] this pause is
     * allowed. Never returns true in the same call it was suppressed in.
     */
    fun onTick(
        nowElapsedRealtime: Long,
        suppressed: Boolean,
    ): Boolean {
        if (suppressed) {
            nextPulseAtElapsedRealtime = nowElapsedRealtime + PULSE_INTERVAL_MILLIS
            return false
        }
        if (pulsesGiven >= MAX_PULSES) return false
        if (nowElapsedRealtime < nextPulseAtElapsedRealtime) return false
        pulsesGiven++
        nextPulseAtElapsedRealtime = nowElapsedRealtime + PULSE_INTERVAL_MILLIS
        return true
    }

    /**
     * True once this paused stretch has used up every pulse it was allowed, so the caller can
     * stop checking entirely rather than tick for the rest of a session that was simply
     * forgotten — see specs/tracking.md#paused-session-reminder: "then it stops".
     */
    val isExhausted: Boolean get() = pulsesGiven >= MAX_PULSES

    /** How many pulses this paused stretch has used. */
    val pulses: Int get() = pulsesGiven

    companion object {
        /** See specs/tracking.md#paused-session-reminder: once a minute. */
        const val PULSE_INTERVAL_MILLIS = 60_000L

        /** See specs/tracking.md#paused-session-reminder: then it stops. */
        const val MAX_PULSES = 20
    }
}
