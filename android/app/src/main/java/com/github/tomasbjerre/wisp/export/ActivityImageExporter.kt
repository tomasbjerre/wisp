package com.github.tomasbjerre.wisp.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.github.tomasbjerre.wisp.data.Session
import com.github.tomasbjerre.wisp.data.UnitSystem
import com.github.tomasbjerre.wisp.ui.Formatting
import org.osmdroid.views.MapView

/**
 * Renders one activity as a single shareable image: its map (as already
 * drawn on screen, route and markers included) with a solid-background
 * stats panel below it — never text laid directly over the map, per
 * specs/accessibility.md#text-contrast. See specs/export.md#single-activity-as-an-image.
 */
object ActivityImageExporter {
    private const val PANEL_BACKGROUND = 0xFF12261F.toInt() // near-black, brand-tinted green
    private const val PANEL_TEXT_COLOR = Color.WHITE
    private const val PANEL_PADDING_DP = 20f
    private const val TITLE_TEXT_SIZE_DP = 20f
    private const val BODY_TEXT_SIZE_DP = 16f
    private const val LINE_SPACING_DP = 10f

    fun compose(
        mapView: MapView,
        session: Session,
        unit: UnitSystem,
    ): Bitmap {
        val density = mapView.resources.displayMetrics.density
        val mapBitmap = captureMap(mapView)
        val panelBitmap = statsPanel(mapBitmap.width, density, session, unit)

        val result =
            Bitmap.createBitmap(mapBitmap.width, mapBitmap.height + panelBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(mapBitmap, 0f, 0f, null)
        canvas.drawBitmap(panelBitmap, 0f, mapBitmap.height.toFloat(), null)
        return result
    }

    /** Captures the MapView exactly as currently rendered — same route/markers as on screen. */
    private fun captureMap(mapView: MapView): Bitmap {
        val bitmap = Bitmap.createBitmap(mapView.width, mapView.height, Bitmap.Config.ARGB_8888)
        mapView.draw(Canvas(bitmap))
        return bitmap
    }

    private fun statsPanel(
        width: Int,
        density: Float,
        session: Session,
        unit: UnitSystem,
    ): Bitmap {
        val padding = PANEL_PADDING_DP * density
        val lineSpacing = LINE_SPACING_DP * density
        val titlePaint = textPaint(TITLE_TEXT_SIZE_DP * density, bold = true)
        val bodyPaint = textPaint(BODY_TEXT_SIZE_DP * density, bold = false)

        val dateLine = Formatting.dateTime(session.startedAt)
        val statsLine =
            "${Formatting.distance(session.distanceMeters, unit)} · ${Formatting.duration(session.durationSeconds)}"
        val speedLine =
            "Avg ${Formatting.speed(session.averageSpeedMps, unit)} · " +
                "Max ${Formatting.speed(session.maxSpeedMps, unit)}"

        val height = (padding * 2 + titlePaint.textSize + lineSpacing * 2 + bodyPaint.textSize * 2).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PANEL_BACKGROUND)

        var baseline = padding + titlePaint.textSize
        canvas.drawText(dateLine, padding, baseline, titlePaint)
        baseline += lineSpacing + bodyPaint.textSize
        canvas.drawText(statsLine, padding, baseline, bodyPaint)
        baseline += lineSpacing + bodyPaint.textSize
        canvas.drawText(speedLine, padding, baseline, bodyPaint)

        return bitmap
    }

    private fun textPaint(
        size: Float,
        bold: Boolean,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PANEL_TEXT_COLOR
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
