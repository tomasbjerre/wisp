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
 * Verifies specs/ui-flows.md#2-tracking-active-recording: back asks for confirmation first
 * (never stops on its own), the dialog says what will happen, Keep recording changes nothing,
 * and confirming Stop behaves exactly like the Stop button. Deliberately doesn't assume the
 * session is still "waiting for movement" by the time it checks — the suite-wide replayed walk
 * (see the CI workflow) plays continuously in the background at real running pace with no
 * per-test way to pause it, so movement can confirm during this test's own UI interaction; both
 * outcomes are asserted for, rather than picking one and risking a flake if the other happens.
 */
@RunWith(AndroidJUnit4::class)
class TrackingBackButtonTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun backAsksFirstAndConfirmingStopFinalizesOrDiscardsExactlyLikeTheStopButton() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.ACCESS_FINE_LOCATION")
        // Granted up front too, so its system dialog (see TrackingScreen) never covers the app.
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.POST_NOTIFICATIONS")

        composeRule.waitForIdle()
        // Not assumed to be zero, even though every test now starts from a cleared app
        // (see clearPackageData in app/build.gradle.kts) — this only needs the count to
        // be unchanged-or-plus-one, not empty, so it doesn't depend on that.
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

        // Asked first, and it says what will happen — whichever is true right now, since
        // movement may or may not have confirmed already (see the class doc comment).
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop recording?").fetchSemanticsNodes().isNotEmpty()
        }
        val expectedText =
            if (TrackingService.state.value.isWaitingForMovement) {
                "Nothing has been recorded yet"
            } else {
                "The activity will be saved"
            }
        composeRule.onNodeWithText(expectedText, substring = true).assertExists()
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

        // Stopped — same as tapping Stop directly — landing on Home (never moved, discarded) or
        // Detail (moved, finalized), whichever the same race above resolved to.
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Export CSV").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            !TrackingService.state.value.isRecording
        }
        composeRule.waitForIdle()
        val onHome = composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty()
        val historyCountAfter =
            if (onHome) {
                composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).fetchSemanticsNodes().size
            } else {
                // Detail doesn't list history rows itself — back to Home to count it there.
                composeRule.onNodeWithText("Back").performClick()
                composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                    composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.waitForIdle()
                composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).fetchSemanticsNodes().size
            }
        // No phantom entry either way (see #56/#59): discarded means the same count as before
        // this test ever touched Start; finalized means exactly one new row, this session's own.
        assertEquals(if (onHome) historyCountBefore else historyCountBefore + 1, historyCountAfter)
    }

    private companion object {
        // The debug build has an application id suffix (see app/build.gradle.kts), so read the
        // id of the app under test instead of hard-coding it.
        val APP_PACKAGE: String get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName

        // Longer than the single-step 15s used elsewhere in this suite: this test does two full
        // back → dialog → decision round trips plus the initial Start → Tracking wait, real
        // wall-clock work that can occasionally run past 15s under CI load.
        const val TIMEOUT_MILLIS = 30_000L
    }
}
