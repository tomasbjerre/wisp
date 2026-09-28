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
        // The troubleshooting columns (see specs/export.md#format) — null when the file
        // was exported before they existed, since there's nothing to read them from.
        val accuracyMeters: Float? = null,
        val noiseReason: String? = null,
        val segmentStart: Boolean? = null,
        val pauseCause: String? = null,
        val steps: Long? = null,
    )

    private const val HEADER_WITHOUT_NOISE = "session_started_at,timestamp,latitude,longitude,speed_kmh"
    private const val HEADER_WITH_NOISE = "$HEADER_WITHOUT_NOISE,is_noise"
    private const val HEADER_WITH_DIAGNOSTICS =
        "$HEADER_WITH_NOISE,accuracy_m,noise_reason,segment_start,pause_cause,steps"

    private const val COLUMNS_WITHOUT_NOISE = 5
    private const val COLUMNS_WITH_NOISE = 6
    private const val COLUMNS_WITH_DIAGNOSTICS = 11

    /**
     * @throws IllegalArgumentException if [csv] doesn't start with a header this
     *     recognizes — the current one (with the troubleshooting columns, see
     *     specs/export.md#format), the one before those existed (with `is_noise`), or
     *     the one every export used before that column existed (see [Row.isNoise]).
     */
    fun parse(csv: String): List<Row> {
        val lines = csv.split("\r\n", "\n").filter { it.isNotBlank() }
        val header = lines.firstOrNull()
        val columnCount =
            when (header) {
                HEADER_WITH_DIAGNOSTICS -> COLUMNS_WITH_DIAGNOSTICS
                HEADER_WITH_NOISE -> COLUMNS_WITH_NOISE
                HEADER_WITHOUT_NOISE -> COLUMNS_WITHOUT_NOISE
                else -> throw IllegalArgumentException(
                    "Expected header \"$HEADER_WITH_DIAGNOSTICS\", \"$HEADER_WITH_NOISE\" or " +
                        "\"$HEADER_WITHOUT_NOISE\", got \"$header\"",
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
        val hasDiagnostics = columnCount == COLUMNS_WITH_DIAGNOSTICS
        return Row(
            sessionStartedAt = Instant.parse(columns[0]).toEpochMilli(),
            timestamp = Instant.parse(columns[1]).toEpochMilli(),
            latitude = columns[2].toDouble(),
            longitude = columns[3].toDouble(),
            speedMps = if (speedKmh.isBlank()) null else speedKmh.toFloat() / 3.6f,
            // A file exported before this column existed only ever held non-noise
            // points (see specs/export.md#format) — false is the correct read, not a
            // guess.
            isNoise = if (columnCount >= COLUMNS_WITH_NOISE) columns[5].toBoolean() else false,
            accuracyMeters = if (hasDiagnostics) columns[6].toFloat() else null,
            noiseReason = if (hasDiagnostics) columns[7].ifBlank { null } else null,
            segmentStart = if (hasDiagnostics) columns[8].toBoolean() else null,
            pauseCause = if (hasDiagnostics) columns[9].ifBlank { null } else null,
            steps = if (hasDiagnostics) columns[10].toLong() else null,
        )
    }
}
