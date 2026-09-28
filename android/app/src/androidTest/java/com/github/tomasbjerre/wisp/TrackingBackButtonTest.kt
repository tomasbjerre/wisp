package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies specs/ui-flows.md#2-tracking-active-recording: back asks for confirmation first,
 * Keep recording changes nothing, and confirming Stop behaves like Stop, not like leaving the
 * screen. Only exercises the "never moved" half of that (confirming before movement is
 * confirmed discards the session and returns to Home) — the "moved, Stop finalizes to Detail"
 * half shares the exact same TrackingService.stop() call the Stop button already uses (see
 * ScreenshotTest/InstructionVideoTest), so it isn't a distinct code path worth a second,
 * movement-dependent test here.
 */
@RunWith(AndroidJUnit4::class)
class TrackingBackButtonTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun backBeforeMovementIsConfirmedDiscardsTheSessionAndReturnsToHome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.ACCESS_FINE_LOCATION")
        // Granted up front too, so its system dialog (see TrackingScreen) never covers the app.
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.POST_NOTIFICATIONS")

        composeRule.waitForIdle()
        // Not assumed to be zero, even though every test now starts from a cleared app
        // (see clearPackageData in app/build.gradle.kts) — this only needs the count to
        // be unchanged, not empty, so it doesn't depend on that.
        val historyCountBefore = composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).fetchSemanticsNodes().size
        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitForIdle()

        // Tracking is up (past the Locating spinner, which has no BackHandler of its
        // own text to wait on — Stop appearing is the reliable signal either state).
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }

        val device = UiDevice.getInstance(instrumentation)
        device.pressBack()

        // Asked first, and it says what will happen (nothing has been recorded yet).
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop recording?").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Nothing has been recorded yet", substring = true).assertExists()
        assertTrue(TrackingService.state.value.isRecording)

        // Keep recording: nothing changes, still on Tracking with the session running.
        composeRule.onNodeWithText("Keep recording").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop recording?").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("Stop").assertExists()
        assertTrue(TrackingService.state.value.isRecording)

        // Back again, and this time confirm.
        device.pressBack()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop recording?").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("Stop") and hasAnyAncestor(isDialog())).performClick()

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
        // The debug build has an application id suffix (see app/build.gradle.kts), so read the
        // id of the app under test instead of hard-coding it.
        val APP_PACKAGE: String get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        const val TIMEOUT_MILLIS = 15_000L
    }
}
