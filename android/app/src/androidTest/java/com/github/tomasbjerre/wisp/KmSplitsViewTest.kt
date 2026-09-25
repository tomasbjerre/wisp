package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Verifies specs/ui-flows.md#4-km-splits end to end: reachable from Detail, one row per
 * complete km plus the trailing partial one, fastest/slowest called out, and back returns
 * to Detail. The split math itself is covered by GeoUtilsTest/KmSplitRowsTest.
 */
@RunWith(AndroidJUnit4::class)
class KmSplitsViewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun detailOpensTheKmSplitsViewAndBackReturnsToDetail() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WispApplication
        // ~5.4 km: 5 complete km (5:40, 5:25, 5:50, 5:10, 6:05) + a partial one.
        runBlocking { seedSessionWithVaryingPace(app, daysAgo = 0) }
        // Seeded after the activity started — wait for Home's history Flow to pick it up.
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).onFirst().performClick()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Km splits (5)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Km splits (5)").performClick()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Fastest", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Fastest: km 4", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Slowest: km 5", substring = true).assertIsDisplayed()
        listOf("1", "2", "3", "4", "5").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText("+", substring = true).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Export CSV").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
