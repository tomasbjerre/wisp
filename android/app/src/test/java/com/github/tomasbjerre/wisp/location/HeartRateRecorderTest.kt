package com.github.tomasbjerre.wisp.location

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/heart-rate.md#recording. */
class HeartRateRecorderTest {
    @Test
    fun `there is no heart rate until a reading arrives`() {
        val recorder = HeartRateRecorder()

        assertThat(recorder.currentBpm(nowMillis = 0)).isNull()
        assertThat(recorder.maxBpm).isNull()
    }

    @Test
    fun `the current heart rate is the most recent reading`() {
        val recorder = HeartRateRecorder()
        recorder.onReading(120, nowMillis = 1_000)
        recorder.onReading(125, nowMillis = 2_000)

        assertThat(recorder.currentBpm(nowMillis = 2_500)).isEqualTo(125)
    }

    @Test
    fun `the maximum is the highest reading, not the latest`() {
        val recorder = HeartRateRecorder()
        recorder.onReading(120, nowMillis = 1_000)
        recorder.onReading(170, nowMillis = 2_000)
        recorder.onReading(140, nowMillis = 3_000)

        assertThat(recorder.maxBpm).isEqualTo(170)
    }

    @Test
    fun `a reading is lost after ten seconds without a new one but the maximum stays`() {
        val recorder = HeartRateRecorder()
        recorder.onReading(150, nowMillis = 1_000)

        assertThat(recorder.currentBpm(nowMillis = 11_000)).isEqualTo(150)
        assertThat(recorder.currentBpm(nowMillis = 11_001)).isNull()
        assertThat(recorder.maxBpm).isEqualTo(150)
    }

    @Test
    fun `readings while paused are ignored, including for the maximum`() {
        val recorder = HeartRateRecorder()
        recorder.onReading(130, nowMillis = 1_000)

        recorder.pause()
        recorder.onReading(190, nowMillis = 2_000)

        assertThat(recorder.maxBpm).isEqualTo(130)
    }

    @Test
    fun `resuming starts without a current heart rate until a fresh reading`() {
        val recorder = HeartRateRecorder()
        recorder.onReading(130, nowMillis = 1_000)
        recorder.pause()

        recorder.resume()
        assertThat(recorder.currentBpm(nowMillis = 2_000)).isNull()

        recorder.onReading(110, nowMillis = 3_000)
        assertThat(recorder.currentBpm(nowMillis = 3_000)).isEqualTo(110)
        assertThat(recorder.maxBpm).isEqualTo(130)
    }
}
