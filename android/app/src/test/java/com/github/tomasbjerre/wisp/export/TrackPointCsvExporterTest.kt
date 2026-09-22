package com.github.tomasbjerre.wisp.export

import com.github.tomasbjerre.wisp.data.Session
import com.github.tomasbjerre.wisp.data.TrackPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/export.md#format. */
class TrackPointCsvExporterTest {
    @Test
    fun `no sessions is just the header row`() {
        assertThat(TrackPointCsvExporter.toCsv(emptyList()))
            .isEqualTo("session_started_at,timestamp,latitude,longitude,speed_kmh\r\n")
    }

    @Test
    fun `a point becomes one row with the session's start time and the spec's exact columns`() {
        // 2026-09-26T14:03:00Z
        val session = Session(id = 1, startedAt = 1790431380000L)
        val point =
            TrackPoint(
                sessionId = 1,
                sequence = 0,
                // 2026-09-26T14:03:05Z
                timestamp = 1790431385000L,
                latitude = 59.334591,
                longitude = 18.06324,
                accuracyMeters = 5f,
                speedMps = 2.0f,
                segmentStart = true,
            )

        val csv = TrackPointCsvExporter.toCsv(listOf(session to listOf(point)))

        assertThat(csv).isEqualTo(
            "session_started_at,timestamp,latitude,longitude,speed_kmh\r\n" +
                "2026-09-26T14:03:00Z,2026-09-26T14:03:05Z,59.334591,18.063240,7.2\r\n",
        )
    }

    @Test
    fun `a point with no speed reading leaves the speed column blank`() {
        val session = Session(id = 1, startedAt = 0)
        val point =
            TrackPoint(
                sessionId = 1,
                sequence = 0,
                timestamp = 0,
                latitude = 0.0,
                longitude = 0.0,
                accuracyMeters = 5f,
                speedMps = null,
                segmentStart = true,
            )

        val csv = TrackPointCsvExporter.toCsv(listOf(session to listOf(point)))

        assertThat(csv).endsWith("0.000000,0.000000,\r\n")
    }

    @Test
    fun `points from multiple sessions are all included, each tagged with its own session`() {
        val first = Session(id = 1, startedAt = 1_000_000)
        val second = Session(id = 2, startedAt = 2_000_000)
        val firstPoint =
            TrackPoint(
                sessionId = 1,
                sequence = 0,
                timestamp = 1_000_000,
                latitude = 1.0,
                longitude = 1.0,
                accuracyMeters = 5f,
                speedMps = null,
                segmentStart = true,
            )
        val secondPoint =
            TrackPoint(
                sessionId = 2,
                sequence = 0,
                timestamp = 2_000_000,
                latitude = 2.0,
                longitude = 2.0,
                accuracyMeters = 5f,
                speedMps = null,
                segmentStart = true,
            )

        val csv = TrackPointCsvExporter.toCsv(listOf(first to listOf(firstPoint), second to listOf(secondPoint)))

        val dataLines = csv.split("\r\n").drop(1).filter { it.isNotEmpty() }
        assertThat(dataLines).hasSize(2)
        assertThat(dataLines[0]).startsWith("1970-01-01T00:16:40Z")
        assertThat(dataLines[1]).startsWith("1970-01-01T00:33:20Z")
    }
}
