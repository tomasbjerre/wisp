package com.github.tomasbjerre.wisp.location

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/** Verifies specs/tracking.md#location-sampling, #speed-calculation, and #noise. */
class TrackRecorderTest {
    @Test
    fun `a fix with poor accuracy is recorded as noise`() {
        val recorder = TrackRecorder()
        val fix = fix(lat = 59.0, lon = 18.0, accuracy = 45f, t = 0)

        assertThat(recorder.accept(fix).isNoise).isTrue()
    }

    @Test
    fun `a fix within the accuracy threshold is accepted as the session's first point`() {
        val recorder = TrackRecorder()
        val fix = fix(lat = 59.0, lon = 18.0, accuracy = 10f, t = 0)

        val recorded = recorder.accept(fix)

        assertThat(recorded.isNoise).isFalse()
        assertThat(recorded.segmentStart).isTrue()
    }

    @Test
    fun `gps jitter below the movement threshold is recorded as noise`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.00000, lon = 18.00000, accuracy = 5f, t = 0))

        // ~1 meter of latitude drift — GPS noise, not real movement.
        val jitter = recorder.accept(fix(lat = 59.00001, lon = 18.00000, accuracy = 5f, t = 1_000))

        assertThat(jitter.isNoise).isTrue()
    }

    @Test
    fun `real movement past the threshold is recorded`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.0000, lon = 18.0000, accuracy = 5f, t = 0))

        // ~11 meters of latitude movement.
        val moved = recorder.accept(fix(lat = 59.0001, lon = 18.0000, accuracy = 5f, t = 1_000))

        assertThat(moved.isNoise).isFalse()
    }

    @Test
    fun `a gps jump implying an impossible speed is recorded as noise`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.0000, lon = 18.0000, accuracy = 5f, t = 0))

        // ~500m in 1s => ~500 m/s — a GPS glitch, not real movement.
        val jump = recorder.accept(fix(lat = 59.0045, lon = 18.0000, accuracy = 5f, t = 1_000))

        assertThat(jump.isNoise).isTrue()
    }

    @Test
    fun `a noise point never becomes the baseline for judging the next fix`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.0000, lon = 18.0000, accuracy = 5f, t = 0))
        // A GPS glitch far away - noise, must not become the new "last known good" point.
        recorder.accept(fix(lat = 59.0045, lon = 18.0000, accuracy = 5f, t = 1_000))

        // Real movement from the *original* point, one second after it (not the glitch).
        val moved = recorder.accept(fix(lat = 59.0001, lon = 18.0000, accuracy = 5f, t = 2_000))

        assertThat(moved.isNoise).isFalse()
    }

    @Test
    fun `fast but plausible movement is still recorded`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.0000, lon = 18.0000, accuracy = 5f, t = 0))

        // ~50m in 1s => ~50 m/s (180 km/h) — fast, but within the generous cap.
        val moved = recorder.accept(fix(lat = 59.00045, lon = 18.0000, accuracy = 5f, t = 1_000))

        assertThat(moved.isNoise).isFalse()
    }

    @Test
    fun `a resume far from where recording paused is not treated as an impossible jump`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.0000, lon = 18.0000, accuracy = 5f, t = 0))
        recorder.pause()

        // Resumes 50km away after an hour — a real gap while paused, not a GPS glitch.
        val resumed = recorder.accept(fix(lat = 59.5000, lon = 18.0000, accuracy = 5f, t = 3_600_000))

        assertThat(resumed.isNoise).isFalse()
        assertThat(resumed.segmentStart).isTrue()
    }

    @Test
    fun `the first fix after a pause always starts a new segment even without movement`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.0, lon = 18.0, accuracy = 5f, t = 0))
        recorder.pause()

        // Same spot as before pausing — would normally be filtered as jitter,
        // but a resume must always produce a segment-start point.
        val resumed = recorder.accept(fix(lat = 59.0, lon = 18.0, accuracy = 5f, t = 60_000))

        assertThat(resumed.isNoise).isFalse()
        assertThat(resumed.segmentStart).isTrue()
    }

    @Test
    fun `a point that isn't noise has no noise reason`() {
        val recorder = TrackRecorder()

        assertThat(recorder.accept(fix(lat = 59.0, lon = 18.0, accuracy = 5f, t = 0)).noiseReason).isNull()
    }

    @Test
    fun `poor accuracy is recorded as the reason`() {
        val recorder = TrackRecorder()

        val recorded = recorder.accept(fix(lat = 59.0, lon = 18.0, accuracy = 45f, t = 0))

        assertThat(recorded.noiseReason).isEqualTo("poor_accuracy")
    }

    @Test
    fun `an implausible jump is recorded as the reason`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.0000, lon = 18.0000, accuracy = 5f, t = 0))

        val jump = recorder.accept(fix(lat = 59.0045, lon = 18.0000, accuracy = 5f, t = 1_000))

        assertThat(jump.noiseReason).isEqualTo("implausible_jump")
    }

    @Test
    fun `gps jitter is recorded as min_movement`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.00000, lon = 18.00000, accuracy = 5f, t = 0))

        val jitter = recorder.accept(fix(lat = 59.00001, lon = 18.00000, accuracy = 5f, t = 1_000))

        assertThat(jitter.noiseReason).isEqualTo("min_movement")
    }

    @Test
    fun `a fix failing several checks lists every reason in a fixed order`() {
        val recorder = TrackRecorder()
        recorder.accept(fix(lat = 59.00000, lon = 18.00000, accuracy = 5f, t = 0))

        // Poor accuracy *and* under a meter from the previous point.
        val both = recorder.accept(fix(lat = 59.00001, lon = 18.00000, accuracy = 45f, t = 1_000))

        assertThat(both.noiseReason).isEqualTo("poor_accuracy|min_movement")
    }

    @Test
    fun `NoiseReason join orders by declaration, drops duplicates, and is null when empty`() {
        val unordered = listOf(NoiseReason.BEFORE_MOVEMENT, NoiseReason.POOR_ACCURACY, NoiseReason.POOR_ACCURACY)

        assertThat(NoiseReason.join(unordered)).isEqualTo("poor_accuracy|before_movement")
        assertThat(NoiseReason.join(emptyList())).isNull()
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
