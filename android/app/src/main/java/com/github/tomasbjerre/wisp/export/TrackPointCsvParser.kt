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
    )

    private const val HEADER = "session_started_at,timestamp,latitude,longitude,speed_kmh"
    private const val COLUMN_COUNT = 5

    /** @throws IllegalArgumentException if [csv] doesn't start with the expected header. */
    fun parse(csv: String): List<Row> {
        val lines = csv.split("\r\n", "\n").filter { it.isNotBlank() }
        require(lines.isNotEmpty() && lines.first() == HEADER) {
            "Expected header \"$HEADER\", got \"${lines.firstOrNull()}\""
        }
        return lines.drop(1).map(::parseRow)
    }

    private fun parseRow(line: String): Row {
        val columns = line.split(",")
        require(columns.size == COLUMN_COUNT) { "Expected $COLUMN_COUNT columns, got ${columns.size}: \"$line\"" }
        val speedKmh = columns[4]
        return Row(
            sessionStartedAt = Instant.parse(columns[0]).toEpochMilli(),
            timestamp = Instant.parse(columns[1]).toEpochMilli(),
            latitude = columns[2].toDouble(),
            longitude = columns[3].toDouble(),
            speedMps = if (speedKmh.isBlank()) null else speedKmh.toFloat() / 3.6f,
        )
    }
}
