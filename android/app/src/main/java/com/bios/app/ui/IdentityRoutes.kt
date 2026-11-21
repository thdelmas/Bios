package com.bios.app.ui

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.bios.app.ui.reproductive.reproductiveCompletenessRoutes

/**
 * Navigation routes for the owner's records reached from the Self tab's
 * records card. Kept out of [MainActivity] so that file stays within the
 * 500-line bound. All routes here are simple back-only screens.
 */
internal fun NavGraphBuilder.identityRoutes(navController: NavController) {
    composable("headache_diary") {
        com.bios.app.ui.headache.HeadacheDiaryScreen(onBack = { navController.popBackStack() })
    }
    composable("traditional_medicine_context") {
        com.bios.app.ui.traditional.TraditionalMedicineContextScreen(onBack = { navController.popBackStack() })
    }
    composable("medications") {
        com.bios.app.ui.medications.MedicationsScreen(onBack = { navController.popBackStack() })
    }
    composable("immunisations") {
        com.bios.app.ui.immunisations.ImmunisationsScreen(onBack = { navController.popBackStack() })
    }
    composable("ecg_strips") {
        com.bios.app.ui.ecg.EcgStripsScreen(onBack = { navController.popBackStack() })
    }
    composable("intervention_events") {
        com.bios.app.ui.interventions.InterventionEventsScreen(onBack = { navController.popBackStack() })
    }
    composable("treatment_courses") {
        com.bios.app.ui.interventions.TreatmentCoursesScreen(onBack = { navController.popBackStack() })
    }
    composable("anthropometry") {
        com.bios.app.ui.anthropometry.AnthropometryScreen(onBack = { navController.popBackStack() })
    }
    reproductiveCompletenessRoutes(navController)
}
