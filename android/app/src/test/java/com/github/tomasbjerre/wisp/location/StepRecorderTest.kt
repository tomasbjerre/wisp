package com.github.tomasbjerre.wisp.location

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/tracking.md#step-count. */
class StepRecorderTest {
    @Test
    fun `the first reading only anchors the baseline, it is not counted itself`() {
        val recorder = StepRecorder()

        recorder.onStepCounterChanged(1_000)

        assertThat(recorder.steps).isZero()
    }

    @Test
    fun `steps accumulate as the delta between consecutive readings`() {
        val recorder = StepRecorder()
        recorder.onStepCounterChanged(1_000)

        recorder.onStepCounterChanged(1_050)
        recorder.onStepCounterChanged(1_120)

        assertThat(recorder.steps).isEqualTo(120L)
    }

    @Test
    fun `steps taken while paused are not counted`() {
        val recorder = StepRecorder()
        recorder.onStepCounterChanged(1_000)
        recorder.onStepCounterChanged(1_050)

        recorder.pause()
        // 200 steps taken while paused (e.g. walking around during a break).
        recorder.onStepCounterChanged(1_250)
        recorder.resume()

        recorder.onStepCounterChanged(1_280)

        // 50 before the pause + 30 after resuming, not the 200 in between.
        assertThat(recorder.steps).isEqualTo(80L)
    }

    @Test
    fun `a lower reading than before, like a device reboot, does not subtract steps`() {
        val recorder = StepRecorder()
        recorder.onStepCounterChanged(1_000)
        recorder.onStepCounterChanged(1_050)

        // Sensor reset to a lower value — reboot mid-session.
        recorder.onStepCounterChanged(10)
        recorder.onStepCounterChanged(40)

        // 50 before the reset, 30 after re-anchoring — never goes negative.
        assertThat(recorder.steps).isEqualTo(80L)
    }
}
