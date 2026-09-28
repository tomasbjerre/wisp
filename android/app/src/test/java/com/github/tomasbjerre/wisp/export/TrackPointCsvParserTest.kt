package com.github.tomasbjerre.wisp.export

import com.github.tomasbjerre.wisp.data.Session
import com.github.tomasbjerre.wisp.data.TrackPoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

/** Verifies specs/export.md#format's round trip: this parser reads exactly what [TrackPointCsvExporter] writes. */
class TrackPointCsvParserTest {
    @Test
    fun `parses a row with all fields present`() {
        val csv =
            "session_started_at,timestamp,latitude,longitude,speed_kmh\r\n" +
                "2026-09-26T07:14:26.910Z,2026-09-26T07:16:15.811Z,56.159508,15.582230,9.2\r\n"

        val rows = TrackPointCsvParser.parse(csv)

        assertThat(rows).hasSize(1)
        val row = rows.single()
        assertThat(row.sessionStartedAt).isEqualTo(Instant.parse("2026-09-26T07:14:26.910Z").toEpochMilli())
        assertThat(row.timestamp).isEqualTo(Instant.parse("2026-09-26T07:16:15.811Z").toEpochMilli())
        assertThat(row.latitude).isEqualTo(56.159508)
        assertThat(row.longitude).isEqualTo(15.582230)
        // 9.2 km/h -> m/s.
        assertThat(row.speedMps).isCloseTo(
            2.5556f,
            org.assertj.core.data.Offset
                .offset(0.001f),
        )
    }

    @Test
    fun `a blank speed column parses as a null speed, same as a live fix without one`() {
        val csv =
            "session_started_at,timestamp,latitude,longitude,speed_kmh\r\n" +
                "2026-09-26T07:14:26.910Z,2026-09-26T07:16:15.811Z,56.159508,15.582230,\r\n"

        assertThat(TrackPointCsvParser.parse(csv).single().speedMps).isNull()
    }

    @Test
    fun `parses multiple rows in order`() {
        val csv =
            "session_started_at,timestamp,latitude,longitude,speed_kmh\r\n" +
                "2026-09-26T07:14:26.910Z,2026-09-26T07:16:15.811Z,56.159508,15.582230,9.2\r\n" +
                "2026-09-26T07:14:26.910Z,2026-09-26T07:16:17.957Z,56.159458,15.582235,9.1\r\n"

        val rows = TrackPointCsvParser.parse(csv)

        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.latitude }).containsExactly(56.159508, 56.159458)
    }

    @Test
    fun `a csv with only a header parses as no rows`() {
        assertThat(TrackPointCsvParser.parse("session_started_at,timestamp,latitude,longitude,speed_kmh\r\n"))
            .isEmpty()
    }

    @Test
    fun `rejects csv text with the wrong header`() {
        assertThatThrownBy { TrackPointCsvParser.parse("not,the,right,header\r\n") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a file exported before the is_noise column existed parses every point as non-noise`() {
        val csv =
            "session_started_at,timestamp,latitude,longitude,speed_kmh\r\n" +
                "2026-09-26T07:14:26.910Z,2026-09-26T07:16:15.811Z,56.159508,15.582230,9.2\r\n"

        assertThat(TrackPointCsvParser.parse(csv).single().isNoise).isFalse()
    }

    @Test
    fun `parses the is_noise column when present`() {
        val csv =
            "session_started_at,timestamp,latitude,longitude,speed_kmh,is_noise\r\n" +
                "2026-09-26T07:14:26.910Z,2026-09-26T07:16:15.811Z,56.159508,15.582230,9.2,true\r\n" +
                "2026-09-26T07:14:26.910Z,2026-09-26T07:16:17.957Z,56.159458,15.582235,9.1,false\r\n"

        val rows = TrackPointCsvParser.parse(csv)

        assertThat(rows.map { it.isNoise }).containsExactly(true, false)
    }

    @Test
    fun `a file without the troubleshooting columns leaves them null`() {
        val csv =
            "session_started_at,timestamp,latitude,longitude,speed_kmh,is_noise\r\n" +
                "2026-09-26T07:14:26.910Z,2026-09-26T07:16:15.811Z,56.159508,15.582230,9.2,true\r\n"

        val row = TrackPointCsvParser.parse(csv).single()

        assertThat(row.accuracyMeters).isNull()
        assertThat(row.noiseReason).isNull()
        assertThat(row.segmentStart).isNull()
        assertThat(row.pauseCause).isNull()
        assertThat(row.steps).isNull()
    }

    @Test
    fun `parses the troubleshooting columns when present, blank reason and cause as null`() {
        val csv =
            "session_started_at,timestamp,latitude,longitude,speed_kmh,is_noise," +
                "accuracy_m,noise_reason,segment_start,pause_cause,steps\r\n" +
                "2026-09-26T07:14:26.910Z,2026-09-26T07:16:15.811Z,56.159508,15.582230,9.2,true," +
                "45.0,poor_accuracy|min_movement,false,,12\r\n" +
                "2026-09-26T07:14:26.910Z,2026-09-26T07:16:17.957Z,56.159458,15.582235,9.1,false," +
                "5.5,,true,auto,1234\r\n"

        val rows = TrackPointCsvParser.parse(csv)

        assertThat(rows[0].accuracyMeters).isEqualTo(45.0f)
        assertThat(rows[0].noiseReason).isEqualTo("poor_accuracy|min_movement")
        assertThat(rows[0].segmentStart).isFalse()
        assertThat(rows[0].pauseCause).isNull()
        assertThat(rows[0].steps).isEqualTo(12L)
        assertThat(rows[1].noiseReason).isNull()
        assertThat(rows[1].segmentStart).isTrue()
        assertThat(rows[1].pauseCause).isEqualTo("auto")
        assertThat(rows[1].steps).isEqualTo(1234L)
    }

    @Test
    fun `reads back exactly what the exporter writes`() {
        val session = Session(id = 1, startedAt = 1790431380000L)
        val point =
            TrackPoint(
                sessionId = 1,
                sequence = 0,
                timestamp = 1790431385000L,
                latitude = 59.334591,
                longitude = 18.06324,
                accuracyMeters = 45f,
                speedMps = null,
                segmentStart = true,
                steps = 77,
                isNoise = true,
                noiseReason = "poor_accuracy|paused",
                pauseCause = "manual",
            )

        val row = TrackPointCsvParser.parse(TrackPointCsvExporter.toCsv(listOf(session to listOf(point)))).single()

        assertThat(row.latitude).isEqualTo(59.334591)
        assertThat(row.isNoise).isTrue()
        assertThat(row.accuracyMeters).isEqualTo(45f)
        assertThat(row.noiseReason).isEqualTo("poor_accuracy|paused")
        assertThat(row.segmentStart).isTrue()
        assertThat(row.pauseCause).isEqualTo("manual")
        assertThat(row.steps).isEqualTo(77L)
    }

    @Test
    fun `the real recorded activity attached to issue 121 parses in full, matching its own summary csv`() {
        val csv =
            javaClass.classLoader!!
                .getResourceAsStream("fixtures/real-running-activity-track-points.csv")!!
                .bufferedReader()
                .use { it.readText() }

        val rows = TrackPointCsvParser.parse(csv)

        // wisp-activity-2026-09-26_09-14-26-track-points.csv has 1335 data rows.
        assertThat(rows).hasSize(1_335)
        assertThat(rows.first().sessionStartedAt).isEqualTo(Instant.parse("2026-09-26T07:14:26.910Z").toEpochMilli())
        // Every row shares the same session_started_at - it's one activity.
        assertThat(rows.map { it.sessionStartedAt }.distinct()).hasSize(1)
        // Timestamps are non-decreasing, i.e. the rows are already in recording order.
        assertThat(rows.zipWithNext().all { (a, b) -> b.timestamp >= a.timestamp }).isTrue()
    }
}
