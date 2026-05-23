package com.bios.app.ui

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Navigation routes for the "Self" surfaces wired through [SettingsScreen]'s
 * Identity card. Extracted from [MainActivity] to give new identity screens
 * a single, low-conflict landing zone — adding a new self-surface means:
 *   1. add a screen in `ui/<domain>/`,
 *   2. add a `composable("route_id")` here,
 *   3. add the nav callback + Identity-card row in the existing places.
 *
 * All routes here are simple back-only screens; no shared state with
 * MainActivity. Routes that need shared event-sheet state, deep-link
 * arguments, or other coupling stay in [MainActivity] for now.
 */
internal fun NavGraphBuilder.identityRoutes(navController: NavController) {
    composable("risk_profile") {
        com.bios.app.ui.risk.RiskProfileScreen(onBack = { navController.popBackStack() })
    }
    composable("physiology_state") {
        com.bios.app.ui.physiology.PhysiologyStateScreen(onBack = { navController.popBackStack() })
    }
    composable("frail_assessment") {
        com.bios.app.ui.physiology.FrailAssessmentScreen(onBack = { navController.popBackStack() })
    }
    composable("goals_of_care") {
        com.bios.app.ui.goals.GoalsOfCareScreen(onBack = { navController.popBackStack() })
    }
    composable("headache_diary") {
        com.bios.app.ui.headache.HeadacheDiaryScreen(onBack = { navController.popBackStack() })
    }
    composable("fast_stroke") {
        com.bios.app.ui.stroke.FastStrokeScreen(onBack = { navController.popBackStack() })
    }
    composable("esas_capture") {
        com.bios.app.ui.esas.EsasCaptureScreen(onBack = { navController.popBackStack() })
    }
    composable("traditional_medicine_context") {
        com.bios.app.ui.traditional.TraditionalMedicineContextScreen(onBack = { navController.popBackStack() })
    }
    composable("emergency_contacts") {
        com.bios.app.ui.emergency.EmergencyContactsScreen(onBack = { navController.popBackStack() })
    }
    composable("medications") {
        com.bios.app.ui.medications.MedicationsScreen(onBack = { navController.popBackStack() })
    }
    composable("immunisations") {
        com.bios.app.ui.immunisations.ImmunisationsScreen(onBack = { navController.popBackStack() })
    }
    composable("preventive_care") {
        com.bios.app.ui.screening.PreventiveCareScreen(onBack = { navController.popBackStack() })
    }
    composable("ecg_strips") {
        com.bios.app.ui.ecg.EcgStripsScreen(onBack = { navController.popBackStack() })
    }
    composable("surgical_recovery") {
        com.bios.app.ui.physiology.SurgicalRecoveryScreen(onBack = { navController.popBackStack() })
    }
}
