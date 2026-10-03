package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.tomasbjerre.wisp.data.UnitSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies specs/ui-flows.md#1a-home-settings and specs/units.md: the Metric/Imperial choice
 * is reachable from Home's gear icon, defaults to Metric, and persists across leaving and
 * reopening the screen. Against the real SharedPreferences-backed
 * [com.github.tomasbjerre.wisp.data.UnitPreferences], not a mock — see AGENTS.md.
 */
@RunWith(AndroidJUnit4::class)
class HomeSettingsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .applicationContext as WispApplication

    @Test
    fun defaultsToMetricAndPersistsImperialAcrossLeavingAndReopeningTheScreen() {
        composeRule.waitForIdle()

        openSettings()
        composeRule.onNodeWithText("Metric").assertIsSelected()
        composeRule.onNodeWithText("Imperial").assertIsNotSelected()

        composeRule.onNodeWithText("Imperial").performClick()
        composeRule.onNodeWithText("Imperial").assertIsSelected()
        assert(app.unitPreferences.unit.value == UnitSystem.IMPERIAL)

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty()
        }

        openSettings()

        // Still Imperial: it persists, it isn't reset by leaving the screen.
        composeRule.onNodeWithText("Imperial").assertIsSelected()
    }

    private fun openSettings() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Metric").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
