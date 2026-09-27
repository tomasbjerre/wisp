package com.github.tomasbjerre.wisp.util

import com.github.tomasbjerre.wisp.data.TrackPoint
import com.github.tomasbjerre.wisp.data.UnitSystem
import com.github.tomasbjerre.wisp.export.TrackPointCsvParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/**
 * Verifies specs/tracking.md#distance-calculation and #speed-calculation against a real
 * recorded walking activity (attached to issue #140 as
 * wisp-activity-2026-09-27_12-20-55-track-points.csv) — a slower, noisier profile than
 * [GeoUtilsRealRunningActivityTest]'s running data, worth covering separately since
 * Wisp doesn't distinguish activity types and must summarize both the same way.
 * [GeoUtils.summarize] run over these points should reproduce the stats Wisp itself
 * recorded for this same activity (see wisp-activity-2026-09-27_12-20-55.csv, the
 * matching summary export): distance_km=1.08, duration_seconds=851,
 * average_speed_kmh=4.6, max_speed_kmh=6.2.
 */
class GeoUtilsRealWalkingActivityTest {
    private val points: List<TrackPoint> by lazy {
        val csv =
            javaClass.classLoader!!
                .getResourceAsStream("fixtures/real-walking-activity-track-points.csv")!!
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
    fun `summarizing the real walking activity's points matches the distance its own export recorded`() {
        assertThat(GeoUtils.summarize(points).distanceMeters).isCloseTo(1_080.0, within(20.0))
    }

    @Test
    fun `summarizing the real walking activity's points matches the duration its own export recorded`() {
        // Duration is just last-point-minus-first-point here (one contiguous segment,
        // no pauses), so this is exact, not just close.
        assertThat(GeoUtils.summarize(points).durationSeconds).isEqualTo(851L)
    }

    @Test
    fun `summarizing the real walking activity's points matches the average speed its own export recorded`() {
        val averageSpeedKmh = GeoUtils.summarize(points).averageSpeedMps * 3.6
        assertThat(averageSpeedKmh).isCloseTo(4.6, within(0.2))
    }

    @Test
    fun `summarizing the real walking activity's points matches the max speed its own export recorded`() {
        val maxSpeedKmh = GeoUtils.summarize(points).maxSpeedMps * 3.6
        assertThat(maxSpeedKmh).isCloseTo(6.2, within(0.2))
    }

    @Test
    fun `the real walking activity has km splits worth comparing, unlike a constant-pace synthetic route`() {
        val splits = GeoUtils.kmSplitsSeconds(points, UnitSystem.METRIC)
        assertThat(splits).hasSizeGreaterThanOrEqualTo(1)
    }
}
