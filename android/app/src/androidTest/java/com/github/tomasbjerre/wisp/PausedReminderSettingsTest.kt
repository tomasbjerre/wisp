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
 * Verifies specs/tracking.md#paused-session-reminder and specs/ui-flows.md#2a-settings: the
 * "Vibrate while paused" switch is reachable from Tracking via Settings, is on by default,
 * and persists across leaving and reopening that screen. Against the real
 * SharedPreferences-backed [com.github.tomasbjerre.wisp.data.PausedReminderPreferences], not
 * a mock — see AGENTS.md.
 */
@RunWith(AndroidJUnit4::class)
class PausedReminderSettingsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun isOnByDefaultAndPersistsAcrossLeavingAndReopeningTheView() {
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

        // On by default — the one setting in Wisp that is, per specs/overview.md#design-principle.
        composeRule.onNodeWithTag(TestTags.PAUSED_REMINDER_SWITCH).assertIsOn()
        composeRule.onNodeWithText("Vibrate while paused").assertExists()

        composeRule.onNodeWithTag(TestTags.PAUSED_REMINDER_SWITCH).performClick()
        composeRule.onNodeWithTag(TestTags.PAUSED_REMINDER_SWITCH).assertIsOff()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }

        openSettings()

        // Still off: it persists, it isn't a per-screen choice.
        composeRule.onNodeWithTag(TestTags.PAUSED_REMINDER_SWITCH).assertIsOff()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Stop").performClick()
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
