package com.bios.app.ui.reproductive

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Nav-graph extension registering the reproductive routes: the four
 * completeness surfaces added in #209 (contraception, menopause stage,
 * gender-affirming care, PCOS / endometriosis context) plus the cycle
 * dashboard. Extracted from [com.bios.app.ui.MainActivity] so the host
 * file stays within the 500-line code-quality bound.
 */
fun NavGraphBuilder.reproductiveCompletenessRoutes(navController: NavController) {
    composable("cycle_dashboard") {
        com.bios.app.ui.period.CycleDashboardScreen(onBack = { navController.popBackStack() })
    }
    composable("contraception") {
        ContraceptionScreen(onBack = { navController.popBackStack() })
    }
    composable("menopause_stage") {
        MenopauseStageScreen(onBack = { navController.popBackStack() })
    }
    composable("gender_affirming_care") {
        GenderAffirmingCareScreen(onBack = { navController.popBackStack() })
    }
    composable("pcos_endometriosis") {
        PcosEndometriosisScreen(onBack = { navController.popBackStack() })
    }
}
