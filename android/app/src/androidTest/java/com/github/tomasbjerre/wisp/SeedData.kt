package com.github.tomasbjerre.wisp

import kotlin.math.PI
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

private const val MILLIS_PER_DAY = 86_400_000L
private const val BASE_LATITUDE = 59.3293
private const val BASE_LONGITUDE = 18.0686
