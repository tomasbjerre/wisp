package com.github.tomasbjerre.wisp.export

import com.github.tomasbjerre.wisp.data.Session
import com.github.tomasbjerre.wisp.data.TrackPoint
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** See specs/export.md#format. Pure string generation — no I/O, no Android types. */
object TrackPointCsvExporter {
    private const val HEADER = "session_started_at,timestamp,latitude,longitude,speed_kmh"

    private val TIMESTAMP_FORMAT = DateTimeFormatter.ISO_INSTANT

    fun toCsv(pointsBySession: List<Pair<Session, List<TrackPoint>>>): String {
        val rows =
            pointsBySession.joinToString(separator = "") { (session, points) ->
                points.joinToString(separator = "") { "${toRow(session, it)}\r\n" }
            }
        return "$HEADER\r\n$rows"
    }

    private fun toRow(
        session: Session,
        point: TrackPoint,
    ): String {
        val sessionStartedAt = format(session.startedAt)
        val timestamp = format(point.timestamp)
        val latitude = "%.6f".format(Locale.ROOT, point.latitude)
        val longitude = "%.6f".format(Locale.ROOT, point.longitude)
        val speedKmh = point.speedMps?.let { "%.1f".format(Locale.ROOT, it * 3.6) } ?: ""
        return listOf(sessionStartedAt, timestamp, latitude, longitude, speedKmh).joinToString(",")
    }

    private fun format(epochMillis: Long): String {
        val zonedTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC)
        return TIMESTAMP_FORMAT.format(zonedTime)
    }
}
