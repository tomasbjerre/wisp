package com.github.tomasbjerre.wisp.util

import com.github.tomasbjerre.wisp.data.TrackPoint
import com.github.tomasbjerre.wisp.export.TrackPointCsvParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/**
 * Verifies specs/tracking.md#distance-calculation and #speed-calculation against a real
 * recorded activity (attached to issue #121 as
 * wisp-activity-2026-09-26_09-14-26-track-points.csv), rather than only the synthetic
 * edge cases in [GeoUtilsTest] — [GeoUtils.summarize] run over these real, noisy GPS
 * points should reproduce the stats Wisp itself recorded for this same activity (see
 * wisp-activity-2026-09-26_09-14-26.csv, the matching summary export):
 * distance_km=7.85, duration_seconds=2937, average_speed_kmh=9.6, max_speed_kmh=10.9.
 */
class GeoUtilsRealActivityTest {
    private val points: List<TrackPoint> by lazy {
        val csv =
            javaClass.classLoader!!
                .getResourceAsStream("fixtures/real-activity-track-points.csv")!!
                .bufferedReader()
                .use { it.readText() }
        TrackPointCsvParser.parse(csv).mapIndexed { index, row ->
            TrackPoint(
                sessionId = 1,
                sequence = index,
                timestamp = row.timestamp,
                latitude = row.latitude,
                longitude = row.longitude,
                accuracyMeters = 5f,
                speedMps = row.speedMps,
                segmentStart = index == 0,
            )
        }
    }

    @Test
    fun `summarizing the real activity's points matches the distance its own export recorded`() {
        assertThat(GeoUtils.summarize(points).distanceMeters).isCloseTo(7_850.0, within(20.0))
    }

    @Test
    fun `summarizing the real activity's points matches the duration its own export recorded`() {
        // Duration is just last-point-minus-first-point here (one contiguous segment,
        // no pauses), so this is exact, not just close.
        assertThat(GeoUtils.summarize(points).durationSeconds).isEqualTo(2_937L)
    }

    @Test
    fun `summarizing the real activity's points matches the average speed its own export recorded`() {
        val averageSpeedKmh = GeoUtils.summarize(points).averageSpeedMps * 3.6
        assertThat(averageSpeedKmh).isCloseTo(9.6, within(0.2))
    }

    @Test
    fun `summarizing the real activity's points matches the max speed its own export recorded`() {
        val maxSpeedKmh = GeoUtils.summarize(points).maxSpeedMps * 3.6
        assertThat(maxSpeedKmh).isCloseTo(10.9, within(0.05))
    }

    @Test
    fun `the real activity has km splits worth comparing, unlike a constant-pace synthetic route`() {
        val splits = GeoUtils.kmSplitsSeconds(points)
        assertThat(splits).hasSizeGreaterThanOrEqualTo(6)
        // A real walk isn't a perfectly even pace - not every split takes the same time.
        assertThat(splits.distinct().size).isGreaterThan(1)
    }
}
