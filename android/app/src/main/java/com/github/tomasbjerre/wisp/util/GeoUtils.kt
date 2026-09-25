package com.github.tomasbjerre.wisp.util

import com.github.tomasbjerre.wisp.data.TrackPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** See specs/tracking.md#distance-calculation. */
object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    // Standard Web Mercator/OSM tile scheme: meters-per-pixel at the equator at
    // zoom 0, for 256px tiles. See https://wiki.openstreetmap.org/wiki/Zoom_levels.
    private const val EQUATOR_METERS_PER_PIXEL_AT_ZOOM_0 = 156_543.03392804097

    /**
     * The (fractional) zoom level at which a map [widthPx] pixels wide shows
     * [widthMeters] meters of the world, at the equator — used to cap how far a
     * map can zoom out (see specs/ui-flows.md#2-tracking-active-recording).
     * Real-world width per pixel shrinks at higher latitudes, so this is a
     * conservative (never-wider-than-requested) cap everywhere off the equator.
     * Returns 0.0 (osmdroid's own minimum) if either input is non-positive —
     * e.g. before the map view has been measured.
     */
    fun minZoomForWidthMeters(
        widthPx: Int,
        widthMeters: Double,
    ): Double {
        if (widthPx <= 0 || widthMeters <= 0) return 0.0
        return ln(EQUATOR_METERS_PER_PIXEL_AT_ZOOM_0 * widthPx / widthMeters) / ln(2.0)
    }

    /** Great-circle distance between two points, in meters. */
    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    data class Summary(
        val distanceMeters: Double,
        val durationSeconds: Long,
        val averageSpeedMps: Double,
        val maxSpeedMps: Double,
    )

    /**
     * Recomputes aggregate stats from a session's persisted points — used for both
     * incremental live updates and recovering an interrupted session on relaunch.
     *
     * A point with `segmentStart = true` begins a new contiguous recording segment
     * (session start, or right after a resume). The pair (previous point, a
     * segment-start point) is never connected — see specs/data-model.md#trackpoint.
     */
    fun summarize(points: List<TrackPoint>): Summary {
        if (points.isEmpty()) return Summary(0.0, 0, 0.0, 0.0)

        var distance = 0.0
        var durationMillis = 0L
        var maxSpeed = 0.0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            if (curr.segmentStart) continue
            distance += haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            durationMillis += curr.timestamp - prev.timestamp
        }
        for (point in points) {
            val speed = point.speedMps?.toDouble() ?: 0.0
            if (speed > maxSpeed) maxSpeed = speed
        }
        val durationSeconds = (durationMillis / 1000).coerceAtLeast(0)
        val averageSpeed = if (durationSeconds > 0) distance / durationSeconds else 0.0
        return Summary(distance, durationSeconds, averageSpeed, maxSpeed)
    }

    private const val SPLIT_DISTANCE_METERS = 1_000.0

    // See specs/tracking.md#km-splits: a leftover shorter than this after the last
    // complete km (e.g. the few meters walked while reaching for Stop) isn't worth a row.
    private const val MIN_PARTIAL_SPLIT_METERS = 10.0

    /** The trailing, less-than-1-km stretch after a session's last complete kilometer. */
    data class PartialSplit(
        val distanceMeters: Double,
        val durationSeconds: Long,
    )

    data class KmSplits(
        val completeSeconds: List<Long>,
        val partial: PartialSplit?,
    )

    /** See [kmSplits] — just the complete kilometers. */
    fun kmSplitsSeconds(points: List<TrackPoint>): List<Long> = kmSplits(points).completeSeconds

    /**
     * See specs/tracking.md#km-splits: the time it took to cover each complete
     * kilometer, one entry per split, in recorded order, plus the trailing partial
     * kilometer after the last one (null if under [MIN_PARTIAL_SPLIT_METERS]). A
     * split's duration is interpolated linearly within whichever recorded segment
     * crosses that kilometer boundary (segments are a few meters at most — see
     * [com.github.tomasbjerre.wisp.location.TrackRecorder] — so linear
     * interpolation is indistinguishable from the true crossing point). Like
     * [summarize], a segmentStart point's incoming pair is skipped entirely, so
     * paused time/distance never counts toward a split.
     */
    fun kmSplits(points: List<TrackPoint>): KmSplits {
        val splits = mutableListOf<Long>()
        var cumulativeDistance = 0.0
        var cumulativeDurationMillis = 0.0
        var durationAtLastSplitMillis = 0.0
        var nextSplitDistance = SPLIT_DISTANCE_METERS

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            if (curr.segmentStart) continue

            val segmentDistance = haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            val segmentDurationMillis = (curr.timestamp - prev.timestamp).toDouble()
            val segmentStartDistance = cumulativeDistance
            val segmentEndDistance = segmentStartDistance + segmentDistance

            while (segmentEndDistance >= nextSplitDistance) {
                val fraction =
                    if (segmentDistance > 0) (nextSplitDistance - segmentStartDistance) / segmentDistance else 0.0
                val durationAtSplitMillis = cumulativeDurationMillis + segmentDurationMillis * fraction
                splits += ((durationAtSplitMillis - durationAtLastSplitMillis) / 1000).toLong()
                durationAtLastSplitMillis = durationAtSplitMillis
                nextSplitDistance += SPLIT_DISTANCE_METERS
            }

            cumulativeDistance = segmentEndDistance
            cumulativeDurationMillis += segmentDurationMillis
        }

        val partialDistance = cumulativeDistance - splits.size * SPLIT_DISTANCE_METERS
        val partial =
            if (partialDistance >= MIN_PARTIAL_SPLIT_METERS) {
                PartialSplit(partialDistance, ((cumulativeDurationMillis - durationAtLastSplitMillis) / 1000).toLong())
            } else {
                null
            }
        return KmSplits(splits, partial)
    }
}
