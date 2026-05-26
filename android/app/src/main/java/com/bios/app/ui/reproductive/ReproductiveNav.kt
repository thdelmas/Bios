package com.bios.app.ui.reproductive

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Nav-graph extension registering the four reproductive-completeness
 * routes added in #209: contraception, menopause stage, gender-affirming
 * care, and PCOS / endometriosis context. Extracted from
 * [com.bios.app.ui.MainActivity] so the host file stays within the
 * 500-line code-quality bound.
 */
fun NavGraphBuilder.reproductiveCompletenessRoutes(navController: NavController) {
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
