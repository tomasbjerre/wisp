package com.github.tomasbjerre.wisp.util

import com.github.tomasbjerre.wisp.data.TrackPoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/** Verifies specs/tracking.md#distance-calculation and #speed-calculation. */
class GeoUtilsTest {
    // Mirrors GeoUtils' own private constant — haversineMeters is exact along one
    // meridian (dLon = 0), so this lets test points be placed at an exact distance.
    private val earthRadiusMeters = 6_371_000.0

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
    fun `min zoom for a target width matches the standard web mercator scale`() {
        // See specs/ui-flows.md#2-tracking-active-recording. At zoom 0 a 256px-wide map
        // shows the whole ~40,075km equator; doubling widthPx or halving widthMeters both
        // require one more zoom level to keep the same on-screen width.
        val zoom = GeoUtils.minZoomForWidthMeters(widthPx = 256, widthMeters = 40_075_016.686)
        assertThat(zoom).isCloseTo(0.0, within(0.01))

        val zoomForDoubleWidthPx = GeoUtils.minZoomForWidthMeters(widthPx = 512, widthMeters = 40_075_016.686)
        assertThat(zoomForDoubleWidthPx).isCloseTo(zoom + 1.0, within(0.01))

        val zoomForHalfWidthMeters = GeoUtils.minZoomForWidthMeters(widthPx = 256, widthMeters = 40_075_016.686 / 2)
        assertThat(zoomForHalfWidthMeters).isCloseTo(zoom + 1.0, within(0.01))
    }

    @Test
    fun `min zoom for an unmeasured view (zero width) is the platform minimum`() {
        assertThat(GeoUtils.minZoomForWidthMeters(widthPx = 0, widthMeters = 100_000.0)).isZero()
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

    @Test
    fun `a session under 1km has no splits`() {
        val points =
            listOf(
                pointAtDistance(seq = 0, t = 0, distanceMeters = 0.0, segmentStart = true),
                pointAtDistance(seq = 1, t = 200_000, distanceMeters = 500.0, segmentStart = false),
            )
        assertThat(GeoUtils.kmSplitsSeconds(points)).isEmpty()
    }

    @Test
    fun `splits a constant-pace session into equal per-km durations, ignoring the trailing partial km`() {
        // ~300.5s per km pace, 2.5km total: two full splits, the trailing 500m dropped.
        // (300.5s, not an exact 300s, so truncation to whole seconds has clear margin
        // either side of the boundary — see pointAtDistance's meridian round-trip.)
        val points =
            listOf(
                pointAtDistance(seq = 0, t = 0, distanceMeters = 0.0, segmentStart = true),
                pointAtDistance(seq = 1, t = 751_250, distanceMeters = 2_500.0, segmentStart = false),
            )
        assertThat(GeoUtils.kmSplitsSeconds(points)).containsExactly(300L, 300L)
    }

    @Test
    fun `a pause between two km does not inflate that split's time`() {
        // See specs/tracking.md#km-splits: paused time/distance is excluded exactly like
        // specs/tracking.md#distance-calculation's session totals.
        val points =
            listOf(
                pointAtDistance(seq = 0, t = 0, distanceMeters = 0.0, segmentStart = true),
                pointAtDistance(seq = 1, t = 300_500, distanceMeters = 1_000.0, segmentStart = false),
                // Paused here for an hour, resuming at the same spot.
                pointAtDistance(seq = 2, t = 3_900_500, distanceMeters = 1_000.0, segmentStart = true),
                pointAtDistance(seq = 3, t = 4_201_000, distanceMeters = 2_000.0, segmentStart = false),
            )
        assertThat(GeoUtils.kmSplitsSeconds(points)).containsExactly(300L, 300L)
    }

    @Test
    fun `the trailing partial km after the last complete one is reported with its own distance and time`() {
        // Same 300.5s/km, 2.5km session as above: the 500m left after km 2 took ~150s.
        val points =
            listOf(
                pointAtDistance(seq = 0, t = 0, distanceMeters = 0.0, segmentStart = true),
                pointAtDistance(seq = 1, t = 751_250, distanceMeters = 2_500.0, segmentStart = false),
            )
        val partial = GeoUtils.kmSplits(points).partial

        assertThat(partial).isNotNull
        assertThat(partial!!.distanceMeters).isCloseTo(500.0, within(0.01))
        assertThat(partial.durationSeconds).isEqualTo(150L)
    }

    @Test
    fun `a session under 1km is all partial km`() {
        val points =
            listOf(
                pointAtDistance(seq = 0, t = 0, distanceMeters = 0.0, segmentStart = true),
                pointAtDistance(seq = 1, t = 200_000, distanceMeters = 500.0, segmentStart = false),
            )
        val splits = GeoUtils.kmSplits(points)

        assertThat(splits.completeSeconds).isEmpty()
        assertThat(splits.partial!!.distanceMeters).isCloseTo(500.0, within(0.01))
        assertThat(splits.partial!!.durationSeconds).isEqualTo(200L)
    }

    @Test
    fun `a few meters past the last complete km is not a partial km`() {
        val points =
            listOf(
                pointAtDistance(seq = 0, t = 0, distanceMeters = 0.0, segmentStart = true),
                pointAtDistance(seq = 1, t = 301_000, distanceMeters = 1_005.0, segmentStart = false),
            )
        val splits = GeoUtils.kmSplits(points)

        assertThat(splits.completeSeconds).hasSize(1)
        assertThat(splits.partial).isNull()
    }

    @Test
    fun `a pause does not inflate the partial km's time`() {
        val points =
            listOf(
                pointAtDistance(seq = 0, t = 0, distanceMeters = 0.0, segmentStart = true),
                pointAtDistance(seq = 1, t = 300_500, distanceMeters = 1_000.0, segmentStart = false),
                // Paused here for an hour, resuming at the same spot.
                pointAtDistance(seq = 2, t = 3_900_500, distanceMeters = 1_000.0, segmentStart = true),
                pointAtDistance(seq = 3, t = 4_050_750, distanceMeters = 1_500.0, segmentStart = false),
            )
        val partial = GeoUtils.kmSplits(points).partial!!

        assertThat(partial.distanceMeters).isCloseTo(500.0, within(0.01))
        assertThat(partial.durationSeconds).isEqualTo(150L)
    }

    /** A point [distanceMeters] north of a fixed origin, along one meridian (exact — see haversineMeters). */
    private fun pointAtDistance(
        seq: Int,
        t: Long,
        distanceMeters: Double,
        segmentStart: Boolean,
    ) = point(seq, t, lat = 59.0 + Math.toDegrees(distanceMeters / earthRadiusMeters), lon = 18.0, segmentStart)

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
