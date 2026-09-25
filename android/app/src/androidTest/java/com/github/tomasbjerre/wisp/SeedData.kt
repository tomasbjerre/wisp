package com.github.tomasbjerre.wisp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Inserts one finished session with a gently curved multi-point route, for
 * deterministic screenshots — see [ScreenshotTest]. Distance/duration/speed
 * stats are then computed for real from these points, the same as a live
 * recording would (see GeoUtils.summarize), not hardcoded.
 */
suspend fun seedSession(
    app: WispApplication,
    daysAgo: Int,
    durationSeconds: Long,
    speedMps: Double,
) {
    val repository = app.repository
    val startedAt = System.currentTimeMillis() - daysAgo * MILLIS_PER_DAY - durationSeconds * 1_000
    val sessionId = repository.startSession(startedAt)

    val pointCount = 8
    for (i in 0 until pointCount) {
        val fraction = i.toDouble() / (pointCount - 1)
        val latitude = BASE_LATITUDE + fraction * 0.01 + sin(fraction * PI * 2) * 0.002
        val longitude = BASE_LONGITUDE + fraction * 0.015
        val timestamp = startedAt + (fraction * durationSeconds * 1_000).toLong()
        repository.appendPoint(
            sessionId = sessionId,
            sequence = i,
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = 5f,
            speedMps = (speedMps * (0.5 + 0.5 * fraction)).toFloat(),
            segmentStart = i == 0,
        )
    }

    repository.finishSession(sessionId, startedAt + durationSeconds * 1_000)
}

/**
 * Like [seedSession], but a longer run (~5.4 km) whose pace varies from one kilometer to
 * the next — for screenshots of specs/ui-flows.md#4-km-splits, where equal splits would
 * show nothing worth comparing. Points every [SPLIT_SEED_STEP_METERS] along a gently
 * meandering route, same as a real recording's few-meters-apart points in effect.
 */
suspend fun seedSessionWithVaryingPace(
    app: WispApplication,
    daysAgo: Int,
) {
    val repository = app.repository
    val totalMeters = 5_400.0
    val pointCount = (totalMeters / SPLIT_SEED_STEP_METERS).toInt() + 1
    val durationMillis =
        (1 until pointCount).sumOf { secondsPerKmAt(it) * SPLIT_SEED_STEP_METERS }.toLong()
    val startedAt = System.currentTimeMillis() - daysAgo * MILLIS_PER_DAY - durationMillis
    val sessionId = repository.startSession(startedAt)

    var latitude = BASE_LATITUDE
    var longitude = BASE_LONGITUDE
    var timestamp = startedAt
    for (i in 0 until pointCount) {
        if (i > 0) {
            val bearing = 0.6 + sin(i / 15.0) * 0.8
            latitude += SPLIT_SEED_STEP_METERS * cos(bearing) / METERS_PER_DEGREE_LATITUDE
            longitude += SPLIT_SEED_STEP_METERS * sin(bearing) /
                (METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(latitude)))
            timestamp += (secondsPerKmAt(i) * SPLIT_SEED_STEP_METERS).toLong()
        }
        repository.appendPoint(
            sessionId = sessionId,
            sequence = i,
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = 5f,
            speedMps = (1_000.0 / secondsPerKmAt(i)).toFloat(),
            segmentStart = i == 0,
        )
    }

    repository.finishSession(sessionId, timestamp)
}

/** Seconds per km (i.e. milliseconds per meter) for the step ending at point [i]. */
private fun secondsPerKmAt(i: Int): Double {
    val km = ((i - 1).coerceAtLeast(0) * SPLIT_SEED_STEP_METERS / 1_000).toInt()
    return SPLIT_SEED_PACES_SECONDS_PER_KM[km.coerceAtMost(SPLIT_SEED_PACES_SECONDS_PER_KM.lastIndex)]
}

// 5:40, 5:25, 5:50, 5:10, 6:05 per km, then the partial km back at 5:30.
private val SPLIT_SEED_PACES_SECONDS_PER_KM = listOf(340.0, 325.0, 350.0, 310.0, 365.0, 330.0)
private const val SPLIT_SEED_STEP_METERS = 50.0

// Matches GeoUtils.haversineMeters' earth radius, so a step is 50 m by its measure too.
private const val METERS_PER_DEGREE_LATITUDE = 6_371_000.0 * PI / 180
private const val MILLIS_PER_DAY = 86_400_000L
private const val BASE_LATITUDE = 59.3293
private const val BASE_LONGITUDE = 18.0686
