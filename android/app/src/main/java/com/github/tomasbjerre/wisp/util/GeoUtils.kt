package com.github.tomasbjerre.wisp.util

import com.github.tomasbjerre.wisp.data.TrackPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** See specs/tracking.md#distance-calculation. */
object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

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
}
