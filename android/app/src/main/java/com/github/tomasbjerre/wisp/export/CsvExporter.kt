package com.github.tomasbjerre.wisp.export

import com.github.tomasbjerre.wisp.data.Session
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** See specs/export.md#format. Pure string generation — no I/O, no Android types. */
object CsvExporter {
    private const val HEADER = "started_at,distance_km,duration_seconds,average_speed_kmh,max_speed_kmh"

    private val TIMESTAMP_FORMAT = DateTimeFormatter.ISO_INSTANT

    fun toCsv(sessions: List<Session>): String {
        val rows = sessions.joinToString(separator = "") { "${toRow(it)}\r\n" }
        return "$HEADER\r\n$rows"
    }

    private fun toRow(session: Session): String {
        val startedAt = TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(session.startedAt).atZone(ZoneOffset.UTC))
        val distanceKm = "%.2f".format(Locale.ROOT, session.distanceMeters / 1000.0)
        val averageSpeedKmh = "%.1f".format(Locale.ROOT, session.averageSpeedMps * 3.6)
        val maxSpeedKmh = "%.1f".format(Locale.ROOT, session.maxSpeedMps * 3.6)
        return listOf(startedAt, distanceKm, session.durationSeconds.toString(), averageSpeedKmh, maxSpeedKmh)
            .joinToString(",")
    }
}
