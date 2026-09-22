package com.github.tomasbjerre.wisp.util

import com.github.tomasbjerre.wisp.data.TrackPoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/** Verifies specs/tracking.md#distance-calculation and #speed-calculation. */
class GeoUtilsTest {
    @Test
    fun `haversine distance between two known points matches the expected distance`() {
        // Stockholm Central Station to Uppsala Central Station, ~63 km apart.
        val meters = GeoUtils.haversineMeters(59.3300, 18.0590, 59.8586, 17.6453)
        assertThat(meters).isBetween(61_000.0, 65_000.0)
    }

    @Test
    fun `haversine distance between identical points is zero`() {
        assertThat(GeoUtils.haversineMeters(59.33, 18.06, 59.33, 18.06)).isCloseTo(0.0, within(0.0001))
    }

    @Test
    fun `summarize of no points has zero distance and duration`() {
        val summary = GeoUtils.summarize(emptyList())
        assertThat(summary.distanceMeters).isZero()
        assertThat(summary.durationSeconds).isZero()
        assertThat(summary.averageSpeedMps).isZero()
        assertThat(summary.maxSpeedMps).isZero()
    }

    @Test
    fun `summarize sums distance and duration across a single contiguous segment`() {
        val points =
            listOf(
                point(seq = 0, t = 0, lat = 59.0000, lon = 18.0000, segmentStart = true),
                point(seq = 1, t = 10_000, lat = 59.0010, lon = 18.0000, segmentStart = false),
                point(seq = 2, t = 20_000, lat = 59.0020, lon = 18.0000, segmentStart = false),
            )
        val summary = GeoUtils.summarize(points)

        val expectedDistance =
            GeoUtils.haversineMeters(59.0000, 18.0000, 59.0010, 18.0000) +
                GeoUtils.haversineMeters(59.0010, 18.0000, 59.0020, 18.0000)
        assertThat(summary.distanceMeters).isCloseTo(expectedDistance, within(0.01))
        assertThat(summary.durationSeconds).isEqualTo(20L)
    }

    @Test
    fun `a pause does not add its distance or duration to the session totals`() {
        // Requirement (specs/tracking.md#distance-calculation,
        // specs/data-model.md#trackpoint): a segmentStart point breaks the
        // track, so the gap across a pause contributes neither distance nor
        // duration, even though the points are far apart in time and space.
        val points =
            listOf(
                point(seq = 0, t = 0, lat = 59.0000, lon = 18.0000, segmentStart = true),
                point(seq = 1, t = 10_000, lat = 59.0010, lon = 18.0000, segmentStart = false),
                // Paused here for an hour and teleported 50km away on resume.
                point(seq = 2, t = 3_610_000, lat = 59.5000, lon = 18.0000, segmentStart = true),
                point(seq = 3, t = 3_620_000, lat = 59.5010, lon = 18.0000, segmentStart = false),
            )
        val summary = GeoUtils.summarize(points)

        val expectedDistance =
            GeoUtils.haversineMeters(59.0000, 18.0000, 59.0010, 18.0000) +
                GeoUtils.haversineMeters(59.5000, 18.0000, 59.5010, 18.0000)
        assertThat(summary.distanceMeters).isCloseTo(expectedDistance, within(0.01))
        // 10s before the pause + 10s after resuming, not the ~1h in between.
        assertThat(summary.durationSeconds).isEqualTo(20L)
    }

    @Test
    fun `max speed is the highest recorded speed sample regardless of segment`() {
        val points =
            listOf(
                point(seq = 0, t = 0, lat = 59.0, lon = 18.0, segmentStart = true, speedMps = 2f),
                point(seq = 1, t = 1_000, lat = 59.001, lon = 18.0, segmentStart = false, speedMps = 9f),
                point(seq = 2, t = 2_000, lat = 59.002, lon = 18.0, segmentStart = true, speedMps = 5f),
            )
        assertThat(GeoUtils.summarize(points).maxSpeedMps).isEqualTo(9.0)
    }

    private fun point(
        seq: Int,
        t: Long,
        lat: Double,
        lon: Double,
        segmentStart: Boolean,
        speedMps: Float? = null,
    ) = TrackPoint(
        sessionId = 1,
        sequence = seq,
        timestamp = t,
        latitude = lat,
        longitude = lon,
        accuracyMeters = 5f,
        speedMps = speedMps,
        segmentStart = segmentStart,
    )
}
