package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies specs/ui-flows.md#feedback-and-support. */
@RunWith(AndroidJUnit4::class)
class InformationDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun infoIconShowsVersionAndLinksThenCloses() {
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("App version, feedback, and the user manual").performClick()
        composeRule.waitForIdle()

        // The real installed version, not a placeholder — see HomeScreen.appVersionName.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        composeRule.onNode(hasText("Version $versionName", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Report a problem or request a feature").assertIsDisplayed()
        composeRule.onNodeWithText("User manual").assertIsDisplayed()

        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Close").fetchSemanticsNodes().isEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
