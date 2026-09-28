package com.github.tomasbjerre.wisp.location

import com.github.tomasbjerre.wisp.data.TrackPoint
import com.github.tomasbjerre.wisp.util.GeoUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/tracking.md#what-must-survive-interruption. */
class RecordingResumeTest {
    private fun point(
        sequence: Int,
        timestamp: Long,
        latitude: Double,
        segmentStart: Boolean = false,
        steps: Long = 0,
        heartRateBpm: Int? = null,
        isNoise: Boolean = false,
        pauseCause: String? = null,
    ) = TrackPoint(
        sessionId = 1,
        sequence = sequence,
        timestamp = timestamp,
        latitude = latitude,
        longitude = 18.0,
        accuracyMeters = 5f,
        speedMps = null,
        segmentStart = segmentStart,
        steps = steps,
        heartRateBpm = heartRateBpm,
        isNoise = isNoise,
        pauseCause = pauseCause,
    )

    @Test
    fun `a session with no points has nothing to continue`() {
        assertThat(RecordingResume.plan(emptyList())).isNull()
    }

    @Test
    fun `a session with only noise points has nothing to continue`() {
        // Movement was never confirmed — see specs/tracking.md#start-gating.
        val points = listOf(point(0, 0, 59.0, segmentStart = true, isNoise = true))

        assertThat(RecordingResume.plan(points)).isNull()
    }

    @Test
    fun `the session continues from what was already recorded`() {
        val points =
            listOf(
                point(0, 0, 59.000, segmentStart = true, steps = 0, heartRateBpm = 120),
                point(1, 10_000, 59.001, steps = 12, heartRateBpm = 165),
                point(2, 20_000, 59.002, steps = 25, heartRateBpm = 150),
            )

        val plan = RecordingResume.plan(points)!!

        assertThat(plan.nextSequence).isEqualTo(3)
        assertThat(plan.elapsedSeconds).isEqualTo(20)
        assertThat(plan.distanceMeters).isEqualTo(GeoUtils.summarize(points).distanceMeters)
        assertThat(plan.steps).isEqualTo(25)
        assertThat(plan.maxHeartRateBpm).isEqualTo(165)
    }

    @Test
    fun `noise points do not count towards distance or time, but do use up sequence numbers`() {
        val points =
            listOf(
                point(0, 0, 59.000, segmentStart = true),
                point(1, 10_000, 59.001),
                point(2, 15_000, 59.500, isNoise = true, steps = 30),
            )

        val plan = RecordingResume.plan(points)!!

        assertThat(plan.elapsedSeconds).isEqualTo(10)
        assertThat(plan.nextSequence).isEqualTo(3)
        assertThat(plan.steps).isEqualTo(30)
    }

    @Test
    fun `the first point after a resume starts a new segment marked as interrupted`() {
        val before =
            listOf(
                point(0, 0, 59.000, segmentStart = true),
                point(1, 10_000, 59.001),
            )
        val plan = RecordingResume.plan(before)!!

        // What TrackingService does on resume: a fresh recorder, and the cause held back
        // until the first accepted point of the new segment.
        val recorder = TrackRecorder()
        val resumed =
            recorder.accept(
                LocationFix(
                    latitude = 59.010,
                    longitude = 18.0,
                    accuracyMeters = 5f,
                    speedMps = null,
                    timestampMillis = 600_000,
                ),
            )
        val stored =
            point(
                plan.nextSequence,
                resumed.timestampMillis,
                resumed.latitude,
                segmentStart = resumed.segmentStart,
                pauseCause = RecordingResume.PAUSE_CAUSE_INTERRUPTED.takeIf { resumed.segmentStart },
            )

        assertThat(stored.segmentStart).isTrue()
        assertThat(stored.pauseCause).isEqualTo("interrupted")
        // The ten minutes and the kilometer between the last point and this one are never counted.
        val summary = GeoUtils.summarize(before + stored)
        assertThat(summary.durationSeconds).isEqualTo(10)
        assertThat(summary.distanceMeters).isEqualTo(GeoUtils.summarize(before).distanceMeters)
    }
}
