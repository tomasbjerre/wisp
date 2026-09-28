package com.github.tomasbjerre.wisp.location

/**
 * Pure decision logic for turning the device's cumulative step-counter sensor
 * readings into a session's step count, excluding paused time — mirrors how
 * [TrackRecorder] excludes paused distance/duration. No Android framework
 * types, no I/O, so it can be unit tested directly against
 * specs/tracking.md#step-count without mocks.
 *
 * One instance covers exactly one recording session. [initialSteps] is the count a
 * session had already reached when it is continued after the app was killed — see
 * specs/tracking.md#what-must-survive-interruption.
 */
class StepRecorder(
    initialSteps: Long = 0,
) {
    private var lastTotalSteps: Long? = null
    private var isPaused = false
    private var sessionSteps = initialSteps

    val steps: Long get() = sessionSteps

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    /**
     * [totalStepsSinceBoot] is the device's step-count sensor reading (Android's
     * TYPE_STEP_COUNTER): a running total that only ever increases, except across a
     * device reboot. The first call after construction (or after a reboot resets the
     * sensor lower than [lastTotalSteps]) just anchors the baseline — it never adds
     * to [steps] itself, since there's no prior reading to measure a delta from.
     */
    fun onStepCounterChanged(totalStepsSinceBoot: Long) {
        val previous = lastTotalSteps
        if (previous != null && !isPaused) {
            val delta = totalStepsSinceBoot - previous
            if (delta > 0) sessionSteps += delta
        }
        lastTotalSteps = totalStepsSinceBoot
    }
}
