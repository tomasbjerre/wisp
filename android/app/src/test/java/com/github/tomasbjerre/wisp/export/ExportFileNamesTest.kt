package com.github.tomasbjerre.wisp.export

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId

/** Verifies specs/export.md#file-names. */
class ExportFileNamesTest {
    // 2026-09-25T04:51:12Z — 06:51:12 in Stockholm (CEST, UTC+2).
    private val epochMillis = 1790311872000L
    private val stockholm = ZoneId.of("Europe/Stockholm")

    @Test
    fun `history CSV files are stamped with the export time in local time`() {
        val names = ExportFileNames.historyCsv(epochMillis, stockholm)

        assertThat(names.sessions).isEqualTo("wisp-history-2026-09-25_06-51-12.csv")
        assertThat(names.trackPoints).isEqualTo("wisp-history-2026-09-25_06-51-12-track-points.csv")
    }

    @Test
    fun `single activity CSV files are stamped with the activity's start time`() {
        val names = ExportFileNames.activityCsv(epochMillis, stockholm)

        assertThat(names.sessions).isEqualTo("wisp-activity-2026-09-25_06-51-12.csv")
        assertThat(names.trackPoints).isEqualTo("wisp-activity-2026-09-25_06-51-12-track-points.csv")
    }

    @Test
    fun `single activity image is stamped with the activity's start time`() {
        assertThat(ExportFileNames.activityImage(epochMillis, stockholm))
            .isEqualTo("wisp-activity-2026-09-25_06-51-12.png")
    }

    @Test
    fun `sorting file names by name sorts exports chronologically`() {
        val earlier = ExportFileNames.historyCsv(epochMillis, stockholm).sessions
        // 1 minute, 1 hour and 1 day later — each crossing a different timestamp field.
        val later =
            listOf(60_000L, 3_600_000L, 86_400_000L).map {
                ExportFileNames.historyCsv(epochMillis + it, stockholm).sessions
            }

        later.forEach { assertThat(it).isGreaterThan(earlier) }
    }

    @Test
    fun `file names contain no characters that are illegal on common platforms`() {
        val names = ExportFileNames.activityCsv(epochMillis, stockholm)

        listOf(names.sessions, names.trackPoints, ExportFileNames.activityImage(epochMillis, stockholm))
            .forEach { assertThat(it).doesNotContainPattern("""[\\/:*?"<>| ]""") }
    }
}
