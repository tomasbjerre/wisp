package com.github.tomasbjerre.wisp

import androidx.test.platform.app.InstrumentationRegistry
import com.github.tomasbjerre.wisp.export.TrackPointCsvParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
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
    var steps = 0.0
    for (i in 0 until pointCount) {
        if (i > 0) {
            val bearing = 0.6 + sin(i / 15.0) * 0.8
            latitude += SPLIT_SEED_STEP_METERS * cos(bearing) / METERS_PER_DEGREE_LATITUDE
            longitude += SPLIT_SEED_STEP_METERS * sin(bearing) /
                (METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(latitude)))
            timestamp += (secondsPerKmAt(i) * SPLIT_SEED_STEP_METERS).toLong()
            // A steady cadence, so slower kilometers also take more steps.
            steps += SPLIT_SEED_CADENCE_STEPS_PER_MINUTE / 60 * secondsPerKmAt(i) * SPLIT_SEED_STEP_METERS / 1_000
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
            steps = steps.roundToLong(),
        )
    }

    repository.finishSession(sessionId, timestamp, steps = steps.roundToLong())
}

/**
 * Inserts one finished session from a real recorded activity (a walk, ~7.85 km /
 * ~49 min) instead of procedurally-generated points — see issue #121. The fixture is
 * `real-activity-track-points.csv` (an androidTest asset), byte-for-byte what
 * `TrackPointCsvExporter` would produce for this same activity, exported from Wisp
 * itself and attached to that issue. Used in place of [seedSessionWithVaryingPace] for
 * the richer Detail/Km-splits screenshots ([ScreenshotTest]), since real GPS noise and
 * pace variation makes for more realistic screenshots than a sine-curve route.
 *
 * The CSV has no per-point step count (only a session-total in the matching summary
 * export, 8 501 steps for this activity) — steps are synthesized the same way
 * [seedSessionWithVaryingPace] does it: a steady cadence, here picked so the running
 * total lands on that same real total by the last point.
 */
suspend fun seedRealSession(
    app: WispApplication,
    daysAgo: Int,
) {
    val repository = app.repository
    val csv =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open(ASSET_NAME)
            .bufferedReader()
            .use { it.readText() }
    val rows = TrackPointCsvParser.parse(csv)
    check(rows.isNotEmpty()) { "$ASSET_NAME has no track points" }

    val firstTimestamp = rows.first().timestamp
    val durationMillis = rows.last().timestamp - firstTimestamp
    val startedAt = System.currentTimeMillis() - daysAgo * MILLIS_PER_DAY - durationMillis
    val sessionId = repository.startSession(startedAt)

    // A steady cadence for this activity's total steps over its total time, so the
    // running count lands exactly on REAL_SESSION_TOTAL_STEPS by the last point.
    val stepsPerMilli = REAL_SESSION_TOTAL_STEPS.toDouble() / durationMillis
    var steps = 0.0
    rows.forEachIndexed { index, row ->
        if (index > 0) steps += stepsPerMilli * (row.timestamp - rows[index - 1].timestamp)
        repository.appendPoint(
            sessionId = sessionId,
            sequence = index,
            timestamp = startedAt + (row.timestamp - firstTimestamp),
            latitude = row.latitude,
            longitude = row.longitude,
            accuracyMeters = 5f,
            speedMps = row.speedMps,
            segmentStart = index == 0,
            steps = steps.roundToLong(),
            // Synthesized like steps: a heart rate warming up steadily over the activity,
            // ending on REAL_SESSION_MAX_HEART_RATE — the session's max is derived from it.
            heartRateBpm =
                REAL_SESSION_MIN_HEART_RATE +
                    (REAL_SESSION_MAX_HEART_RATE - REAL_SESSION_MIN_HEART_RATE) * index / (rows.size - 1),
        )
    }

    repository.finishSession(sessionId, startedAt + durationMillis, steps = steps.roundToLong())
}

/** Seconds per km (i.e. milliseconds per meter) for the step ending at point [i]. */
private fun secondsPerKmAt(i: Int): Double {
    val km = ((i - 1).coerceAtLeast(0) * SPLIT_SEED_STEP_METERS / 1_000).toInt()
    return SPLIT_SEED_PACES_SECONDS_PER_KM[km.coerceAtMost(SPLIT_SEED_PACES_SECONDS_PER_KM.lastIndex)]
}

// 5:40, 5:25, 5:50, 5:10, 6:05 per km, then the partial km back at 5:30.
private val SPLIT_SEED_PACES_SECONDS_PER_KM = listOf(340.0, 325.0, 350.0, 310.0, 365.0, 330.0)
private const val SPLIT_SEED_STEP_METERS = 50.0
private const val SPLIT_SEED_CADENCE_STEPS_PER_MINUTE = 170.0

// Matches GeoUtils.haversineMeters' earth radius, so a step is 50 m by its measure too.
private const val METERS_PER_DEGREE_LATITUDE = 6_371_000.0 * PI / 180
private const val MILLIS_PER_DAY = 86_400_000L
private const val BASE_LATITUDE = 59.3293
private const val BASE_LONGITUDE = 18.0686

// See seedRealSession: from wisp-activity-2026-09-26_09-14-26.csv, the summary export
// matching real-activity-track-points.csv's track points (issue #121).
private const val ASSET_NAME = "real-activity-track-points.csv"
private const val REAL_SESSION_TOTAL_STEPS = 8_501L

/** Synthesized heart rate range for [seedRealSession] — see the comment where it is used. */
const val REAL_SESSION_MIN_HEART_RATE = 110
const val REAL_SESSION_MAX_HEART_RATE = 165
