package com.github.tomasbjerre.wisp.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.tomasbjerre.wisp.data.SessionRepository
import com.github.tomasbjerre.wisp.data.UnitPreferences
import com.github.tomasbjerre.wisp.data.VoiceFeedbackPreferences
import com.github.tomasbjerre.wisp.ui.detail.DetailScreen
import com.github.tomasbjerre.wisp.ui.home.HomeScreen
import com.github.tomasbjerre.wisp.ui.splits.KmSplitsScreen
import com.github.tomasbjerre.wisp.ui.tracking.TrackingScreen
import com.github.tomasbjerre.wisp.ui.tracking.VoiceFeedbackSettingsScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_TRACKING = "tracking"
private const val ROUTE_VOICE_FEEDBACK_SETTINGS = "tracking/voice-feedback"
private const val ROUTE_DETAIL = "detail/{sessionId}"
private const val ROUTE_KM_SPLITS = "detail/{sessionId}/splits"
private const val ARG_SESSION_ID = "sessionId"

/** See specs/ui-flows.md#navigation. */
@Composable
fun WispApp(
    repository: SessionRepository,
    voiceFeedbackPreferences: VoiceFeedbackPreferences,
    unitPreferences: UnitPreferences,
) {
    val navController = rememberNavController()

    // Every screen's map/panel boundary sits at a different height (see
    // specs/ui-flows.md), so the library's default crossfade briefly composes both
    // screens on top of each other and that boundary visibly jumps between the two
    // positions. Cutting the transition avoids that flicker; nothing in the spec
    // asks for an animated transition anyway.
    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(ROUTE_HOME) {
            HomeScreen(
                repository = repository,
                unitPreferences = unitPreferences,
                onStart = { navController.navigate(ROUTE_TRACKING) },
                onOpenSession = { id -> navController.navigate("detail/$id") },
            )
        }
        composable(ROUTE_TRACKING) {
            TrackingScreen(
                unitPreferences = unitPreferences,
                onStopped = { sessionId ->
                    navController.navigate("detail/$sessionId") {
                        popUpTo(ROUTE_HOME)
                    }
                },
                onCancelled = { navController.popBackStack() },
                onOpenVoiceFeedbackSettings = { navController.navigate(ROUTE_VOICE_FEEDBACK_SETTINGS) },
            )
        }
        composable(ROUTE_VOICE_FEEDBACK_SETTINGS) {
            VoiceFeedbackSettingsScreen(
                preferences = voiceFeedbackPreferences,
                onBack = { navController.popBackStack() },
            )
        }
        detailDestination(navController, repository, unitPreferences)
        kmSplitsDestination(navController, repository, unitPreferences)
    }
}

private fun NavGraphBuilder.detailDestination(
    navController: NavController,
    repository: SessionRepository,
    unitPreferences: UnitPreferences,
) {
    composable(
        ROUTE_DETAIL,
        arguments = listOf(navArgument(ARG_SESSION_ID) { type = NavType.LongType }),
    ) { backStackEntry ->
        val sessionId = backStackEntry.arguments?.getLong(ARG_SESSION_ID)
        if (sessionId != null) {
            DetailScreen(
                repository = repository,
                sessionId = sessionId,
                unitPreferences = unitPreferences,
                onDeleted = {
                    navController.navigate(ROUTE_HOME) { popUpTo(ROUTE_HOME) { inclusive = true } }
                },
                onBack = { navController.popBackStack() },
                onOpenKmSplits = { navController.navigate("detail/$sessionId/splits") },
            )
        }
    }
}

private fun NavGraphBuilder.kmSplitsDestination(
    navController: NavController,
    repository: SessionRepository,
    unitPreferences: UnitPreferences,
) {
    composable(
        ROUTE_KM_SPLITS,
        arguments = listOf(navArgument(ARG_SESSION_ID) { type = NavType.LongType }),
    ) { backStackEntry ->
        val sessionId = backStackEntry.arguments?.getLong(ARG_SESSION_ID)
        if (sessionId != null) {
            KmSplitsScreen(
                repository = repository,
                sessionId = sessionId,
                unitPreferences = unitPreferences,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
