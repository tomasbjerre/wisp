package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.github.tomasbjerre.wisp.location.TrackingService
import com.github.tomasbjerre.wisp.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a correctness test — drives the real app against seeded (not live-GPS) data, plus
 * a live Tracking session fed by a slow simulated walk (see
 * android/README.md#running-it-against-a-local-emulator), to capture one screenshot per
 * screen/state in specs/ui-flows.md for the Play Store listing and README. Output lands
 * under /sdcard/wisp-screenshots (not the app's own storage, which
 * `connectedAndroidTest` wipes by uninstalling the app when the run finishes) — the CI
 * workflow pulls it from there via `adb pull`.
 *
 * When you add a screen or a state to a screen, add a capture for it here in the same
 * change — see ../../../../../../../AGENTS.md.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun captureScreenshots() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Granted up front, like InstructionVideoTest, so Tracking below shows the real
        // screen instead of the system permission dialog.
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.ACCESS_FINE_LOCATION")
        instrumentation.uiAutomation.grantRuntimePermission(
            APP_PACKAGE,
            "android.permission.ACCESS_BACKGROUND_LOCATION",
        )

        dismissSystemAnrIfPresent()
        composeRule.waitForIdle()
        screenshot("1-home-empty")

        val app = instrumentation.targetContext.applicationContext as WispApplication
        runBlocking {
            // Varying pace, so Detail's km splits (see captureKmSplits) have something to
            // compare.
            seedSessionWithVaryingPace(app, daysAgo = 1)
            seedSession(app, daysAgo = 4, durationSeconds = 3_120, speedMps = 4.0)
        }
        composeRule.waitForIdle()
        screenshot("2-home-history")

        composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).onFirst().performClick()
        composeRule.waitForIdle()
        // osmdroid tiles load asynchronously over the network; give them a moment to
        // arrive before capturing, since Compose idling doesn't know about that.
        Thread.sleep(MAP_TILE_SETTLE_MILLIS)
        screenshot("7-detail")

        captureDetailSatellite()
        captureDeleteConfirm()
        captureKmSplits()

        // Back to Home, then into a live Tracking session — see
        // specs/ui-flows.md#2-tracking-active-recording.
        composeRule.onNodeWithText("Back").performClick()
        composeRule.waitUntil(timeoutMillis = LOCATE_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitForIdle()

        captureTrackingStates()
    }

    /**
     * See specs/ui-flows.md#3-detail. Unnumbered, same reasoning as captureKmSplits: the
     * Play listing slots are already spent by "7-detail".
     */
    private fun captureDetailSatellite() {
        composeRule.onNodeWithText("Satellite").performClick()
        composeRule.waitForIdle()
        Thread.sleep(MAP_TILE_SETTLE_MILLIS)
        screenshot("detail-satellite")
        composeRule.onNodeWithText("Map").performClick()
        composeRule.waitForIdle()
    }

    private fun captureDeleteConfirm() {
        // Two "Delete" nodes exist once the dialog is up (this row's own action, behind
        // the dialog, and the dialog's confirm button) — captured before either is
        // clicked, so which one the screencap picks up doesn't matter.
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitUntil(timeoutMillis = LOCATE_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Delete this activity?").fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("8-detail-delete-confirm")
        // Cancel, not confirm — this session is still needed for the Detail capture
        // above to make sense, and Home's history capture already ran.
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
    }

    /**
     * See specs/ui-flows.md#4-km-splits. Unnumbered on purpose: the release workflow only
     * puts the numbered captures in the Play listing (capped at 8 phone screenshots),
     * the rest go to docs/screenshots only.
     */
    private fun captureKmSplits() {
        composeRule.onNodeWithText("Km splits", substring = true).performClick()
        composeRule.waitUntil(timeoutMillis = LOCATE_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Fastest", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("detail-km-splits")
        device.pressBack()
        composeRule.waitForIdle()
    }

    /**
     * See specs/ui-flows.md#2a-voice-feedback-settings. Unnumbered, same reasoning as
     * captureKmSplits: the Play listing slots are already spent by "4-tracking-recording".
     */
    private fun captureVoiceFeedbackSettings() {
        composeRule.onNodeWithContentDescription("Voice feedback settings").performClick()
        composeRule.waitForIdle()
        screenshot("tracking-voice-feedback-settings")
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
    }

    private fun captureTrackingStates() {
        // isRecording is true for the whole life of a session (see TrackingService.start)
        // — isLocating and isWaitingForMovement are what actually distinguish these
        // states on screen (see TrackingScreen: isLocating gates the whole screen before
        // anything else renders).
        composeRule.waitUntil(timeoutMillis = LOCATE_TIMEOUT_MILLIS) {
            !TrackingService.state.value.isLocating
        }
        // "Waiting for movement" (specs/tracking.md#start-gating): the first fix has
        // arrived but no subsequent fix has implied movement yet. Whether this window is
        // wide enough to catch depends on how quickly the simulated walk supplies a
        // second, moved fix — if it's already missed, the recording capture below still
        // succeeds regardless.
        if (TrackingService.state.value.isWaitingForMovement) {
            composeRule.waitForIdle()
            screenshot("3-tracking-waiting")
        }

        // Recording, with a real route on the map (specs/tracking.md#start-gating).
        composeRule.waitUntil(timeoutMillis = RECORDING_TIMEOUT_MILLIS) {
            val state = TrackingService.state.value
            !state.isLocating && !state.isWaitingForMovement && state.route.size >= 3
        }
        composeRule.waitForIdle()
        screenshot("4-tracking-recording")

        captureVoiceFeedbackSettings()

        // Satellite map toggle (specs/ui-flows.md#2-tracking-active-recording).
        composeRule.onNodeWithText("Satellite").performClick()
        composeRule.waitForIdle()
        Thread.sleep(MAP_TILE_SETTLE_MILLIS)
        screenshot("6-tracking-satellite")

        // Pause/Continue (specs/ui-flows.md#2-tracking-active-recording).
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.waitUntil(timeoutMillis = LOCATE_TIMEOUT_MILLIS) {
            TrackingService.state.value.isPaused
        }
        composeRule.waitForIdle()
        screenshot("5-tracking-paused")

        composeRule.onNodeWithText("Stop").performClick()
        composeRule.waitForIdle()
    }

    // Via the shell, not app-code File I/O: scoped storage silently blocks the app
    // process itself from writing raw /sdcard paths, but the shell (uiautomator's
    // executeShellCommand) isn't subject to that.
    private fun screenshot(name: String) {
        dismissSystemAnrIfPresent()
        device.executeShellCommand("mkdir -p $SCREENSHOT_DIR")
        device.executeShellCommand("screencap -p $SCREENSHOT_DIR/$name.png")
    }

    /** Occasionally a "System UI isn't responding" dialog covers the screen on a loaded
     * (e.g. software-rendered) emulator — dismiss it rather than capture it by accident. */
    private fun dismissSystemAnrIfPresent() {
        val waitButton = device.findObject(UiSelector().textContains("Wait"))
        if (waitButton.exists()) {
            waitButton.click()
            device.waitForIdle()
        }
    }

    private companion object {
        const val APP_PACKAGE = "com.github.tomasbjerre.wisp"
        const val SCREENSHOT_DIR = "/sdcard/wisp-screenshots"
        const val MAP_TILE_SETTLE_MILLIS = 3_000L
        const val LOCATE_TIMEOUT_MILLIS = 15_000L
        const val RECORDING_TIMEOUT_MILLIS = 45_000L
    }
}
