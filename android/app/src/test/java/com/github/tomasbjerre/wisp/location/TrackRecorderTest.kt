package com.github.tomasbjerre.wisp.location

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/** Verifies specs/tracking.md#location-sampling and #speed-calculation. */
class TrackRecorderTest {
    @Test
    fun `a fix with poor accuracy is discarded`() {
        val recorder = TrackRecorder()
        val fix = fix(lat = 59.0, lon = 18.0, accuracy = 45f, t = 0)

        assertThat(recorder.accept(fix)).isNull()
    }

    @Test
    fun `a fix within the accuracy threshold is accepted as the session's first point`() {
        val recorder = TrackRecorder()
        val fix = fix(lat = 59.0, lon = 18.0, accuracy = 10f, t = 0)

        val recorded = recorder.accept(fix)

        assertThat(recorded).isNotNull
        assertThat(recorded!!.segmentStart).isTrue()
    }

    @Test
    fun `gps jitter below the movement threshold is not recorded as a new point`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.00000, lon = 18.00000, accuracy = 5f, t = 0))

        // ~1 meter of latitude drift — GPS noise, not real movement.
        val jitter = recorder.accept(fix(lat = 59.00001, lon = 18.00000, accuracy = 5f, t = 1_000))

        assertThat(jitter).isNull()
    }

    @Test
    fun `real movement past the threshold is recorded`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.0000, lon = 18.0000, accuracy = 5f, t = 0))

        // ~11 meters of latitude movement.
        val moved = recorder.accept(fix(lat = 59.0001, lon = 18.0000, accuracy = 5f, t = 1_000))

        assertThat(moved).isNotNull
    }

    @Test
    fun `the first fix after a pause always starts a new segment even without movement`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.0, lon = 18.0, accuracy = 5f, t = 0))
        recorder.pause()

        // Same spot as before pausing — would normally be filtered as jitter,
        // but a resume must always produce a segment-start point.
        val resumed = recorder.accept(fix(lat = 59.0, lon = 18.0, accuracy = 5f, t = 60_000))

        assertThat(resumed).isNotNull
        assertThat(resumed!!.segmentStart).isTrue()
    }

    @Test
    fun `speed uses the platform-reported value when available`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.0, lon = 18.0, accuracy = 5f, t = 0, speedMps = 3f))

        assertThat(recorder.currentSpeedMps).isCloseTo(3.0, within(0.0001))
    }

    @Test
    fun `speed is derived from distance and time when the platform provides none`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.00000, lon = 18.00000, accuracy = 5f, t = 0, speedMps = null))
        // ~11.1m moved in 10s => ~1.11 m/s raw sample.
        recorder.accept(fix(lat = 59.0001, lon = 18.00000, accuracy = 5f, t = 10_000, speedMps = null))

        assertThat(recorder.currentSpeedMps).isPositive()
    }

    @Test
    fun `a segment start does not derive speed across the pause gap`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.0000, lon = 18.0000, accuracy = 5f, t = 0, speedMps = null))
        recorder.pause()

        // Resumes far away after a long gap — must not be read as a huge speed spike.
        recorder.accept(fix(lat = 59.5000, lon = 18.0000, accuracy = 5f, t = 3_600_000, speedMps = null))

        assertThat(recorder.currentSpeedMps).isCloseTo(0.0, within(0.0001))
    }

    private fun fix(
        lat: Double,
        lon: Double,
        accuracy: Float,
        t: Long,
        speedMps: Float? = null,
    ) = LocationFix(
        latitude = lat,
        longitude = lon,
        accuracyMeters = accuracy,
        speedMps = speedMps,
        timestampMillis = t,
    )
}
