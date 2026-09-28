package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.tomasbjerre.wisp.data.ActivityType
import com.github.tomasbjerre.wisp.data.UnitSystem
import com.github.tomasbjerre.wisp.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies specs/calories.md and specs/ui-flows.md#1a-weight against the real
 * SharedPreferences-backed preferences and database, not mocks — see AGENTS.md.
 */
@RunWith(AndroidJUnit4::class)
class CaloriesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WispApplication

    @Test
    fun theWeightIsEnteredOnItsOwnViewAndKeptAfterLeavingIt() {
        app.weightPreferences.setWeightKg(null)
        composeRule.waitForIdle()

        openWeight()
        composeRule.onNodeWithTag(TestTags.WEIGHT_FIELD).performTextInput("70.5")
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TestTags.WEIGHT_BUTTON).fetchSemanticsNodes().isNotEmpty()
        }

        assert(app.weightPreferences.weightKg.value == 70.5)

        openWeight()
        composeRule.onAllNodesWithText("70.5").onFirst().assertExists()
        composeRule.onNodeWithTag(TestTags.WEIGHT_FIELD).performTextClearance()

        assert(app.weightPreferences.weightKg.value == null)
    }

    @Test
    fun aWeightIsEnteredInPoundsUnderImperialAndStoredInKilograms() {
        app.unitPreferences.setUnit(UnitSystem.IMPERIAL)
        app.weightPreferences.setWeightKg(null)
        composeRule.waitForIdle()

        openWeight()
        composeRule.onNodeWithTag(TestTags.WEIGHT_FIELD).performTextInput("154.3")

        val stored = app.weightPreferences.weightKg.value!!
        assert(kotlin.math.abs(stored - 70.0) < 0.05) { "154.3 lb should be about 70 kg, was $stored" }
        app.unitPreferences.setUnit(UnitSystem.METRIC)
    }

    @Test
    fun homeOnlyShowsTheActivityTypeItDoesNotOfferToChooseIt() {
        composeRule.waitForIdle()

        ActivityType.entries.forEach {
            composeRule.onAllNodesWithTag(TestTags.activityTypeOption(it.id)).assertCountEquals(0)
        }
    }

    @Test
    fun aSessionWithAWeightShowsItsActivityTypeAndCaloriesOnHomeAndDetail() {
        runBlocking {
            // 5 km in an hour, walking, 80 kg: MET 3.5 * 80 kg * 1 h = 280 kcal.
            seedSession(
                app,
                daysAgo = 0,
                durationSeconds = 3_600,
                speedMps = 5.0 / 3.6,
                activityType = ActivityType.WALKING,
                weightKg = 80.0,
            )
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("kcal", substring = true).onFirst().assertExists()
        // See specs/calories.md#where-it-is-shown: the row says what kind of activity it was.
        composeRule.onAllNodesWithText("Walking", substring = true).onFirst().assertExists()

        composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).onFirst().performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Calories:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Activity: Walking").assertExists()
    }

    @Test
    fun aSessionRecordedWithoutAWeightShowsNoCalories() {
        runBlocking { seedSession(app, daysAgo = 0, durationSeconds = 3_600, speedMps = 1.4) }
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).fetchSemanticsNodes().isNotEmpty()
        }

        assert(composeRule.onAllNodesWithText("kcal", substring = true).fetchSemanticsNodes().isEmpty())
    }

    private fun openWeight() {
        composeRule.onNodeWithTag(TestTags.WEIGHT_BUTTON).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TestTags.WEIGHT_FIELD).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
