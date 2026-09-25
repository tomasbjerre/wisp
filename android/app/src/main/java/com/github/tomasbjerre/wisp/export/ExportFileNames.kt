package com.github.tomasbjerre.wisp.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** See specs/export.md#file-names. */
object ExportFileNames {
    data class CsvPair(
        val sessions: String,
        val trackPoints: String,
    )

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    /** Stamped with [exportedAtMillis] — a later history export is the more complete one. */
    fun historyCsv(
        exportedAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CsvPair = csvPair("wisp-history-${timestamp(exportedAtMillis, zone)}")

    /** Stamped with the activity's own start time, so one activity always gets one name. */
    fun activityCsv(
        startedAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CsvPair = csvPair(activityPrefix(startedAtMillis, zone))

    fun activityImage(
        startedAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = "${activityPrefix(startedAtMillis, zone)}.png"

    private fun activityPrefix(
        startedAtMillis: Long,
        zone: ZoneId,
    ) = "wisp-activity-${timestamp(startedAtMillis, zone)}"

    private fun csvPair(prefix: String) = CsvPair("$prefix.csv", "$prefix-track-points.csv")

    private fun timestamp(
        epochMillis: Long,
        zone: ZoneId,
    ): String = timestampFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}
