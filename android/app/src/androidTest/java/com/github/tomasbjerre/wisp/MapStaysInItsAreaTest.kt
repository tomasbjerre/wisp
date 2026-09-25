package com.github.tomasbjerre.wisp

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.github.tomasbjerre.wisp.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies specs/accessibility.md#text-contrast: panning the map never draws map pixels
 * over the solid panel next to it (see #85 — dragging Detail's map left tiles drawn over
 * the summary text below it). Compares a strip of the panel just below the map before
 * and after a drag: it's the panel's own padding, so the only thing that could change
 * it is the map spilling out of its bounds.
 */
@RunWith(AndroidJUnit4::class)
class MapStaysInItsAreaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun draggingDetailsMapDoesNotDrawOverTheSummaryPanel() {
        val app = instrumentation.targetContext.applicationContext as WispApplication
        runBlocking { seedSession(app, daysAgo = 0, durationSeconds = 1_200, speedMps = 3.0) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).onFirst().performClick()
        composeRule.waitForIdle()
        Thread.sleep(MAP_SETTLE_MILLIS)

        val map = composeRule.onNodeWithTag(TestTags.ROUTE_MAP).fetchSemanticsNode().boundsInWindow
        val before = panelStripBelow(map)

        // Drag the map's content down (and a little sideways), towards the panel.
        val centerX = map.center.x.toInt()
        val startY = (map.top + map.height * 0.2f).toInt()
        val endY = (map.bottom - map.height * 0.1f).toInt()
        device.swipe(centerX, startY, centerX + map.width.toInt() / 4, endY, SWIPE_STEPS)
        Thread.sleep(MAP_SETTLE_MILLIS)

        val after = panelStripBelow(map)
        val changed = before.indices.count { before[it] != after[it] }
        assertTrue(
            "$changed of ${before.size} pixels of the panel just below the map changed after dragging the map",
            changed == 0,
        )
    }

    /** Pixels of the band [STRIP_HEIGHT_PX] tall immediately below [map], full map width. */
    private fun panelStripBelow(map: Rect): IntArray {
        val screenshot: Bitmap = instrumentation.uiAutomation.takeScreenshot()
        val left = map.left.toInt()
        val top = map.bottom.toInt() + 1
        val width = map.width.toInt()
        val pixels = IntArray(width * STRIP_HEIGHT_PX)
        screenshot.getPixels(pixels, 0, width, left, top, width, STRIP_HEIGHT_PX)
        return pixels
    }

    private companion object {
        // Well inside the panel's own 16dp top padding on any density, so no panel text
        // falls in it.
        const val STRIP_HEIGHT_PX = 20
        const val SWIPE_STEPS = 40
        const val MAP_SETTLE_MILLIS = 2_000L
    }
}
