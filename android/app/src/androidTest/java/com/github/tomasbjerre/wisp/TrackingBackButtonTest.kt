package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.github.tomasbjerre.wisp.location.TrackingService
import com.github.tomasbjerre.wisp.ui.TestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies specs/ui-flows.md#2-tracking-active-recording: back behaves like Stop, not
 * like leaving the screen. Only exercises the "never moved" half of that (back before
 * movement is confirmed discards the session and returns to Home) — the "moved, back
 * finalizes to Detail" half shares the exact same TrackingService.stop() call the Stop
 * button already uses (see ScreenshotTest/InstructionVideoTest), so it isn't a distinct
 * code path worth a second, movement-dependent test here.
 */
@RunWith(AndroidJUnit4::class)
class TrackingBackButtonTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun backBeforeMovementIsConfirmedDiscardsTheSessionAndReturnsToHome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.ACCESS_FINE_LOCATION")

        composeRule.waitForIdle()
        // Not assumed to be zero: connectedDebugAndroidTest runs every instrumented test
        // class against the same app install/database, and e.g. ScreenshotTest seeds
        // sessions of its own — this only needs the count to be unchanged, not empty.
        val historyCountBefore = composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).fetchSemanticsNodes().size
        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitForIdle()

        // Tracking is up (past the Locating spinner, which has no BackHandler of its
        // own text to wait on — Stop appearing is the reliable signal either state).
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }

        UiDevice.getInstance(instrumentation).pressBack()

        // Back on Home, not stuck on Tracking — Start visible again.
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            !TrackingService.state.value.isRecording
        }
        // No phantom entry (see #56/#59): same history count as before this test ever
        // touched Start, not "zero" — see the comment on historyCountBefore above.
        composeRule.waitForIdle()
        val historyCountAfter = composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).fetchSemanticsNodes().size
        assertEquals(historyCountBefore, historyCountAfter)
    }

    private companion object {
        const val APP_PACKAGE = "com.github.tomasbjerre.wisp"
        const val TIMEOUT_MILLIS = 15_000L
    }
}
