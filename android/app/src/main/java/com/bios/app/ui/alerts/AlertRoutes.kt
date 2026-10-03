package com.bios.app.ui.alerts

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bios.app.ui.AppViewModel

private const val ALERT_DETAIL_ROUTE = "alert/{anomalyId}"

fun alertDetailRoute(anomalyId: String): String = "alert/$anomalyId"

/**
 * Alert detail route, reached from a notification tap (HandleDeepLinks) or
 * from an alert card on the Notice screen. Kept out of MainActivity, which
 * sits near its 500-line cap.
 */
internal fun NavGraphBuilder.alertRoutes(navController: NavController, viewModel: AppViewModel) {
    composable(
        route = ALERT_DETAIL_ROUTE,
        arguments = listOf(navArgument("anomalyId") { type = NavType.StringType })
    ) { entry ->
        AlertDetailScreen(
            anomalyId = entry.arguments?.getString("anomalyId").orEmpty(),
            viewModel = viewModel,
            onBack = { if (!navController.popBackStack()) navController.navigate("notice") },
            onOpenPattern = { navController.navigate("condition/$it") },
            onOpenTrend = { navController.navigate("trends?metric=$it") },
        )
    }
}
