package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.tomasbjerre.wisp.data.ActivityType
import com.github.tomasbjerre.wisp.location.TrackingService
import com.github.tomasbjerre.wisp.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Verifies specs/calories.md#activity-type and specs/ui-flows.md#2-tracking-active-recording:
 * the activity type is chosen on Tracking, starts on the type of the last activity, and is
 * stored on the session as soon as it changes. Against the real service,
 * database and preferences, not mocks — see AGENTS.md.
 */
@RunWith(AndroidJUnit4::class)
class ActivityTypeOnTrackingTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Before the activity launches, notifications included so no permission dialog covers it.
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

    private val app =
        androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .applicationContext as WispApplication

    @Test
    fun theTypeStartsAsTheLastActivitysIsChosenOnTrackingAndStoredOnTheSession() {
        // The last activity was a run, so that is what a new one starts as.
        runBlocking { seedSession(app, daysAgo = 1, durationSeconds = 600, speedMps = 3.0, activityType = ActivityType.RUNNING) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Start").performClick()
        // The stats panel, with the choice, appears once a first position is known.
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TestTags.activityTypeOption("cycling")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.activityTypeOption("running")).assertIsSelected()

        composeRule.onNodeWithTag(TestTags.activityTypeOption("cycling")).performClick()

        composeRule.onNodeWithTag(TestTags.activityTypeOption("cycling")).assertIsSelected()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            val id = TrackingService.state.value.sessionId
            id != null && runBlocking { app.repository.getSession(id)?.activityType } == "cycling"
        }

        composeRule.onNodeWithText("Stop").performClick()
    }

    @Test
    fun withNoPreviousActivityItStartsAsWalking() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TestTags.activityTypeOption("walking")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(TestTags.activityTypeOption("walking")).assertIsSelected()
        composeRule.onNodeWithText("Stop").performClick()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
    }
}
