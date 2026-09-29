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
 * Verifies specs/ui-flows.md#2-tracking-active-recording: back stops immediately with no
 * confirmation while still waiting for movement (nothing recorded yet to lose), but asks first
 * once recording has actually started (Force start, or movement confirmed naturally), where
 * Keep recording changes nothing and confirming Stop behaves exactly like the Stop button.
 */
@RunWith(AndroidJUnit4::class)
class TrackingBackButtonTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * Deliberately doesn't assume the session is still "waiting for movement" by the time back
     * is pressed — the suite-wide replayed walk (see the CI workflow) plays continuously in the
     * background at real running pace with no per-test way to pause it, so movement can confirm
     * during this test's own UI interaction. Both outcomes are asserted for, rather than picking
     * one and risking a flake if the other happens — the "already confirmed" branch exercises
     * the same dialog flow as [backAfterForceStartAsksFirstAndConfirmingStopMatchesStopButton].
     */
    @Test
    fun backStopsImmediatelyWithoutAskingWhileStillWaitingForMovementButAsksFirstOnceStarted() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.ACCESS_FINE_LOCATION")
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.POST_NOTIFICATIONS")

        composeRule.waitForIdle()
        val historyCountBefore = historyRowCount()
        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }

        // What the app's own BackHandler will see — captured before pressing back, since
        // TrackingService.stop() (which the "still waiting" branch below triggers directly)
        // updates this state asynchronously afterward.
        val wasWaitingForMovement = TrackingService.state.value.isWaitingForMovement
        val device = UiDevice.getInstance(instrumentation)
        device.pressBack()

        if (wasWaitingForMovement) {
            // See specs/ui-flows.md#2-tracking-active-recording: no confirmation step, straight
            // back to Home, and nothing saved.
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) { !TrackingService.state.value.isRecording }
            composeRule.waitForIdle()
            assertEquals(historyCountBefore, historyRowCount())
            return
        }

        assertDialogAsksFirstThenKeepRecordingThenConfirmingStopMatchesStopButton(device, historyCountBefore)
    }

    @Test
    fun backAfterForceStartAsksFirstAndConfirmingStopMatchesStopButton() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.ACCESS_FINE_LOCATION")
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.POST_NOTIFICATIONS")

        composeRule.waitForIdle()
        val historyCountBefore = historyRowCount()
        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitForIdle()

        // See specs/tracking.md#force-start: only shown while waiting for movement — tapping it
        // deterministically reaches the "recording has actually started" state this test needs,
        // without depending on the background replay walk's timing.
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Force start").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Force start").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) { !TrackingService.state.value.isWaitingForMovement }

        val device = UiDevice.getInstance(instrumentation)
        device.pressBack()

        assertDialogAsksFirstThenKeepRecordingThenConfirmingStopMatchesStopButton(device, historyCountBefore)
    }

    private fun assertDialogAsksFirstThenKeepRecordingThenConfirmingStopMatchesStopButton(
        device: UiDevice,
        historyCountBefore: Int,
    ) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop recording?").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("The activity will be saved", substring = true).assertExists()
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

        // Stopped — same as tapping Stop directly — landing on Home (never any real data,
        // discarded — see specs/tracking.md#start-gating) or Detail (finalized), whichever
        // actually happened, since Force start alone doesn't guarantee a point was recorded
        // by the time Stop is confirmed.
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Export CSV").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) { !TrackingService.state.value.isRecording }
        composeRule.waitForIdle()
        val onHome = composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty()
        val historyCountAfter =
            if (onHome) {
                historyRowCount()
            } else {
                // Detail doesn't list history rows itself — back to Home to count it there.
                composeRule.onNodeWithText("Back").performClick()
                composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                    composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.waitForIdle()
                historyRowCount()
            }
        // No phantom entry either way (see #56/#59/#169): discarded means the same count as
        // before this test ever touched Start; finalized means exactly one new row, this
        // session's own.
        assertEquals(if (onHome) historyCountBefore else historyCountBefore + 1, historyCountAfter)
    }

    // Not assumed to be zero, even though every test now starts from a cleared app (see
    // clearPackageData in app/build.gradle.kts) — callers only need the count unchanged-or-plus-one,
    // not empty, so this doesn't depend on that.
    private fun historyRowCount() = composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).fetchSemanticsNodes().size

    private companion object {
        // The debug build has an application id suffix (see app/build.gradle.kts), so read the
        // id of the app under test instead of hard-coding it.
        val APP_PACKAGE: String get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName

        // Longer than the single-step 15s used elsewhere in this suite: the dialog-flow tests do
        // two full back → dialog → decision round trips plus the initial Start → Tracking wait,
        // real wall-clock work that can occasionally run past 15s under CI load.
        const val TIMEOUT_MILLIS = 30_000L
    }
}
