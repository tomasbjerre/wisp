package com.github.tomasbjerre.wisp.export

import java.time.Instant

/**
 * The inverse of [TrackPointCsvExporter]: parses CSV text in that exact format (see
 * specs/export.md#format) back into structured rows. Pure — no I/O, no Android types —
 * so it's unit-testable on its own.
 *
 * Currently only used by androidTest fixtures that seed a real recorded activity (see
 * SeedData.kt's `seedRealSession`, fed from a CSV exported from Wisp itself and attached
 * to issue #121) rather than by any in-app import feature.
 */
object TrackPointCsvParser {
    data class Row(
        val sessionStartedAt: Long,
        val timestamp: Long,
        val latitude: Double,
        val longitude: Double,
        val speedMps: Float?,
        val isNoise: Boolean,
    )

    private const val HEADER_WITHOUT_NOISE = "session_started_at,timestamp,latitude,longitude,speed_kmh"
    private const val HEADER_WITH_NOISE = "$HEADER_WITHOUT_NOISE,is_noise"

    /**
     * @throws IllegalArgumentException if [csv] doesn't start with a header this
     *     recognizes — either the current one (with `is_noise`, see
     *     specs/export.md#format) or the one every export used before that column
     *     existed (see [Row.isNoise]).
     */
    fun parse(csv: String): List<Row> {
        val lines = csv.split("\r\n", "\n").filter { it.isNotBlank() }
        val header = lines.firstOrNull()
        val columnCount =
            when (header) {
                HEADER_WITH_NOISE -> 6
                HEADER_WITHOUT_NOISE -> 5
                else -> throw IllegalArgumentException(
                    "Expected header \"$HEADER_WITH_NOISE\" or \"$HEADER_WITHOUT_NOISE\", got \"$header\"",
                )
            }
        return lines.drop(1).map { parseRow(it, columnCount) }
    }

    private fun parseRow(
        line: String,
        columnCount: Int,
    ): Row {
        val columns = line.split(",")
        require(columns.size == columnCount) { "Expected $columnCount columns, got ${columns.size}: \"$line\"" }
        val speedKmh = columns[4]
        return Row(
            sessionStartedAt = Instant.parse(columns[0]).toEpochMilli(),
            timestamp = Instant.parse(columns[1]).toEpochMilli(),
            latitude = columns[2].toDouble(),
            longitude = columns[3].toDouble(),
            speedMps = if (speedKmh.isBlank()) null else speedKmh.toFloat() / 3.6f,
            // A file exported before this column existed only ever held non-noise
            // points (see specs/export.md#format) — false is the correct read, not a
            // guess.
            isNoise = if (columnCount == 6) columns[5].toBoolean() else false,
        )
    }
}
