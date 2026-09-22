package com.github.tomasbjerre.wisp.ui

import java.text.SimpleDateFormat
import java.util.Locale

object Formatting {
    fun distance(meters: Double): String =
        if (meters >= 1000) {
            "%.2f km".format(meters / 1000)
        } else {
            "%.0f m".format(meters)
        }

    fun speedKmh(metersPerSecond: Double): String = "%.1f km/h".format(metersPerSecond * 3.6)

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private val dateTimeFormat = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())

    fun dateTime(epochMillis: Long): String = dateTimeFormat.format(epochMillis)
}
