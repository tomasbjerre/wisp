package com.github.tomasbjerre.wisp.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.tomasbjerre.wisp.data.SessionRepository
import com.github.tomasbjerre.wisp.ui.detail.DetailScreen
import com.github.tomasbjerre.wisp.ui.home.HomeScreen
import com.github.tomasbjerre.wisp.ui.tracking.TrackingScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_TRACKING = "tracking"
private const val ROUTE_DETAIL = "detail/{sessionId}"
private const val ARG_SESSION_ID = "sessionId"

/** See specs/ui-flows.md#navigation. */
@Composable
fun WispApp(repository: SessionRepository) {
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
                onStart = { navController.navigate(ROUTE_TRACKING) },
                onOpenSession = { id -> navController.navigate("detail/$id") },
            )
        }
        composable(ROUTE_TRACKING) {
            TrackingScreen(
                onStopped = { sessionId ->
                    navController.navigate("detail/$sessionId") {
                        popUpTo(ROUTE_HOME)
                    }
                },
            )
        }
        composable(
            ROUTE_DETAIL,
            arguments = listOf(navArgument(ARG_SESSION_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong(ARG_SESSION_ID)
            if (sessionId != null) {
                DetailScreen(
                    repository = repository,
                    sessionId = sessionId,
                    onDeleted = {
                        navController.navigate(ROUTE_HOME) { popUpTo(ROUTE_HOME) { inclusive = true } }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
