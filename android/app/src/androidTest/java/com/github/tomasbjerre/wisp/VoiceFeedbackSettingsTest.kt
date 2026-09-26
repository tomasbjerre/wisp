package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.tomasbjerre.wisp.ui.TestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies specs/ui-flows.md#2a-voice-feedback-settings and
 * specs/voice-feedback.md#settings: reachable from Tracking, every switch persists
 * across leaving and reopening the view, and back returns to Tracking. Against the
 * real SharedPreferences-backed VoiceFeedbackPreferences, not a mock — see AGENTS.md.
 */
@RunWith(AndroidJUnit4::class)
class VoiceFeedbackSettingsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun switchesPersistAcrossLeavingAndReopeningTheView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.ACCESS_FINE_LOCATION")

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }

        openVoiceFeedbackSettings()

        // Off by default (specs/voice-feedback.md#settings).
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ENABLED_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ANNOUNCE_STEPS_SWITCH).assertIsOn()

        // Flip it on, and flip one of the four sub-switches off, to verify both
        // directions persist.
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ENABLED_SWITCH).performClick()
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ANNOUNCE_STEPS_SWITCH).performClick()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }

        openVoiceFeedbackSettings()
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ENABLED_SWITCH).assertIsOn()
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ANNOUNCE_STEPS_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(TestTags.VOICE_FEEDBACK_ANNOUNCE_KM_SWITCH).assertIsOn()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Stop").performClick()
    }

    private fun openVoiceFeedbackSettings() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithContentDescription("Voice feedback settings")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Voice feedback settings").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Voice feedback settings").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val APP_PACKAGE = "com.github.tomasbjerre.wisp"
        const val TIMEOUT_MILLIS = 15_000L
    }
}
