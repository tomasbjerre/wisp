package com.github.tomasbjerre.wisp

import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.tomasbjerre.wisp.location.TrackingService
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Verifies specs/permissions-and-privacy.md#required-access for the recording notification:
 * tapping it opens Tracking on the session being recorded, and does nothing when nothing is
 * being recorded. Also verifies specs/ui-flows.md#navigation: a plain (re)launch of the app
 * behaves the same way when a session is already recording. Against the real activity and
 * service, not mocks — see AGENTS.md.
 */
@RunWith(AndroidJUnit4::class)
class RecordingNotificationTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Granted before the activity launches (granting a running app's permissions can restart it),
    // notifications included so no permission dialog covers the screen — see
    // NotificationPermissionTest for that dialog.
    @get:Rule
    val rules: RuleChain =
        RuleChain
            .outerRule(
                GrantPermissionsBeforeLaunch(
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_BACKGROUND_LOCATION",
                    "android.permission.POST_NOTIFICATIONS",
                ),
            ).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun tappingTheNotificationOpensTrackingInsteadOfHome() {
        composeRule.waitForIdle()
        // Home, with a recording that is running in the service.
        TrackingService.start(context)
        composeRule.waitUntil(TIMEOUT_MILLIS) { TrackingService.state.value.isRecording }
        assert(composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty())

        tapNotification()

        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Finding your location…").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }
        // Still the one session: opening Tracking from the notification must not start another.
        assert(TrackingService.state.value.isRecording)

        TrackingService.stop(context)
    }

    @Test
    fun tappingTheNotificationWithNothingRecordedStaysOnHome() {
        composeRule.waitForIdle()

        tapNotification()
        composeRule.waitForIdle()

        assert(composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty())
        assert(!TrackingService.state.value.isRecording)
    }

    @Test
    fun relaunchingTheAppWhileRecordingOpensTrackingInsteadOfHome() {
        composeRule.waitForIdle()
        // A recording running in the service, with the activity that started it gone — e.g. the
        // task was swiped away in recents — so reopening the app is a plain cold launch, not a
        // notification tap.
        TrackingService.start(context)
        composeRule.waitUntil(TIMEOUT_MILLIS) { TrackingService.state.value.isRecording }
        composeRule.runOnUiThread { composeRule.activity.finish() }

        composeRule.runOnUiThread {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Finding your location…").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }
        assert(TrackingService.state.value.isRecording)

        TrackingService.stop(context)
    }

    // What the notification's content intent does — see TrackingService.buildNotification.
    private fun tapNotification() {
        composeRule.runOnUiThread {
            composeRule.activity.startActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_TRACKING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
