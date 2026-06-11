package com.bios.app.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.bios.app.ui.biomarkers.BiomarkerEntryScreen
import com.bios.app.ui.clinical.ClinicalReadingEntryScreen
import com.bios.app.ui.clinical.MeasurementPreset
import com.bios.app.ui.vessel.VesselWatchScreen

/**
 * Owner-as-source entry routes plus the Vessel Watch readout, split out of
 * [MainActivity]'s nav graph to keep that file within the 500-line limit — a
 * first slice of the decomposition tracked in #382. Each route just wires a
 * screen to the shared [navController] / [viewModel].
 */
fun NavGraphBuilder.entryAndReadoutRoutes(
    navController: NavHostController,
    viewModel: AppViewModel,
) {
    composable("biomarker_entry") {
        BiomarkerEntryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
    }
    composable("clinical_entry") {
        ClinicalReadingEntryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
    }
    composable("vessel_watch") {
        VesselWatchScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
    }
    composable("blood_pressure_entry") {
        ClinicalReadingEntryScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            initialPreset = MeasurementPreset.BLOOD_PRESSURE,
        )
    }
}
