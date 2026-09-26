package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.tomasbjerre.wisp.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies specs/heart-rate.md#setting and #display: the Home switch is off by default,
 * and a session with heart rate shows its maximum on Detail. Connecting to an actual
 * monitor needs hardware — [com.github.tomasbjerre.wisp.location.HeartRateMeasurementTest]
 * and HeartRateRecorderTest cover what it feeds.
 */
@RunWith(AndroidJUnit4::class)
class HeartRateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeOffersTheHeartRateSwitchOffByDefault() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Heart rate monitor").assertIsDisplayed()
        composeRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun detailShowsTheMaximumHeartRateOfASessionThatHasOne() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WispApplication
        runBlocking { seedRealSession(app, daysAgo = 0) }
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).onFirst().performClick()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText("Max heart rate: $REAL_SESSION_MAX_HEART_RATE bpm")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
