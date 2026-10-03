package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.tomasbjerre.wisp.ui.TestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies specs/ui-flows.md#2a-settings and specs/voice-feedback.md#settings: the voice
 * feedback switches are reachable from Tracking via the Settings screen, every switch
 * persists across leaving and reopening it, and back returns to Tracking. Against the real
 * SharedPreferences-backed VoiceFeedbackPreferences, not a mock — see AGENTS.md.
 */
@RunWith(AndroidJUnit4::class)
class VoiceFeedbackSettingsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun switchesPersistAcrossLeavingAndReopeningTheView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.ACCESS_FINE_LOCATION")
        // Granted up front too, so its system dialog (see TrackingScreen) never covers the app.
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.POST_NOTIFICATIONS")

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }

        openSettings()

        // Off by default (specs/voice-feedback.md#settings).
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ENABLED_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ANNOUNCE_STEPS_SWITCH).assertIsOn()
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ANNOUNCE_KM_ELAPSED_TIME_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ANNOUNCE_ELAPSED_TIME_SWITCH).assertIsOn()
        composeRule.onNodeWithText("Elapsed time per kilometer").assertExists()
        composeRule.onNodeWithText("Total elapsed time").assertExists()

        // Flip it on, and flip one of the four sub-switches off, to verify both
        // directions persist.
        clickSwitch(TestTags.VOICE_FEEDBACK_ENABLED_SWITCH)
        clickSwitch(TestTags.VOICE_FEEDBACK_ANNOUNCE_STEPS_SWITCH)
        clickSwitch(TestTags.VOICE_FEEDBACK_ANNOUNCE_KM_ELAPSED_TIME_SWITCH)

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }

        openSettings()
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ENABLED_SWITCH).assertIsOn()
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ANNOUNCE_STEPS_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ANNOUNCE_KM_ELAPSED_TIME_SWITCH).assertIsOn()
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ANNOUNCE_KM_SWITCH).assertIsOn()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Stop").performClick()
    }

    /**
     * The Settings list scrolls (specs/ui-flows.md#2a-settings), and on a small screen — CI's
     * emulator is 320x640 — the lower switches start out below the window, where a click would
     * be injected past the edge of the display and never reach them. Scroll each into view
     * first; a no-op for the ones already visible.
     */
    private fun clickSwitch(testTag: String) {
        composeRule.onNodeWithTag(TestTags.SETTINGS_LIST).performScrollToNode(hasTestTag(testTag))
        composeRule.onNodeWithTag(testTag).performClick()
    }

    private fun openSettings() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        // The debug build has an application id suffix (see app/build.gradle.kts), so read the
        // id of the app under test instead of hard-coding it.
        val APP_PACKAGE: String get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        const val TIMEOUT_MILLIS = 15_000L
    }
}
