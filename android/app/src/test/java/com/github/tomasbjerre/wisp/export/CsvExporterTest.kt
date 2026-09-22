package com.github.tomasbjerre.wisp.export

import com.github.tomasbjerre.wisp.data.Session
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/export.md#format. */
class CsvExporterTest {
    @Test
    fun `an empty history is just the header row`() {
        assertThat(CsvExporter.toCsv(emptyList()))
            .isEqualTo("started_at,distance_km,duration_seconds,average_speed_kmh,max_speed_kmh\r\n")
    }

    @Test
    fun `a session becomes one row with the spec's exact columns and units`() {
        val session =
            Session(
                id = 1,
                // 2026-09-26T14:03:00Z
                startedAt = 1790431380000L,
                endedAt = 1790431980000L,
                distanceMeters = 1234.5,
                durationSeconds = 600,
                averageSpeedMps = 2.0,
                maxSpeedMps = 5.5,
            )

        val csv = CsvExporter.toCsv(listOf(session))

        assertThat(csv).isEqualTo(
            "started_at,distance_km,duration_seconds,average_speed_kmh,max_speed_kmh\r\n" +
                "2026-09-26T14:03:00Z,1.23,600,7.2,19.8\r\n",
        )
    }

    @Test
    fun `rows preserve the order sessions are passed in`() {
        val first = Session(id = 1, startedAt = 2_000)
        val second = Session(id = 2, startedAt = 1_000)

        val csv = CsvExporter.toCsv(listOf(first, second))

        val dataLines = csv.split("\r\n").drop(1).filter { it.isNotEmpty() }
        assertThat(dataLines).hasSize(2)
        assertThat(dataLines[0]).startsWith("1970-01-01T00:00:02Z")
        assertThat(dataLines[1]).startsWith("1970-01-01T00:00:01Z")
    }
}
