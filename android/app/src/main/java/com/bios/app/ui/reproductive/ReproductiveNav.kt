package com.bios.app.ui.reproductive

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Nav-graph extension registering the cycle dashboard and the
 * contraception record. Kept out of [com.bios.app.ui.MainActivity] so the
 * host file stays within the 500-line bound.
 */
fun NavGraphBuilder.reproductiveCompletenessRoutes(navController: NavController) {
    composable("cycle_dashboard") {
        com.bios.app.ui.period.CycleDashboardScreen(onBack = { navController.popBackStack() })
    }
    composable("contraception") {
        ContraceptionScreen(onBack = { navController.popBackStack() })
    }
}
