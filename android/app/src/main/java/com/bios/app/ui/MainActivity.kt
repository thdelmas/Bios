package com.bios.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bios.app.ui.home.HomeScreen
import com.bios.app.ui.log.LogScreen
import com.bios.app.ui.notice.NoticeScreen
import com.bios.app.ui.onboarding.OnboardingScreen
import com.bios.app.ui.settings.SettingsScreen
import com.bios.app.model.HealthEventType
import com.bios.app.ui.journal.HealthEventSheet
import com.bios.app.ui.diagnostics.ConditionDetailScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.bios.app.ui.ppg.PpgCaptureScreen
import com.bios.app.alerts.BiomarkerReferences
import com.bios.app.ui.reference.LongevityReferenceScreen
import com.bios.contracts.MetricType
import com.bios.app.ui.support.MonthlyAskPopup
import com.bios.app.ui.support.MonthlyAskScheduler
import com.bios.app.ui.theme.BiosTheme
import com.bios.app.ui.timeline.TimelineScreen
import com.bios.app.ui.trends.TrendsScreen
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BiosTheme {
                BiosRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiosRoot(viewModel: AppViewModel = viewModel()) {
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    var permissionDenied by remember { mutableStateOf(false) }
    var checkedInitialPermissions by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(viewModel.healthConnect.permissions)) {
            viewModel.onPermissionsGranted()
        } else {
            permissionDenied = true
        }
    }

    MaybeRequestActivityRecognition(hasPermissions)

    // Check permissions on first composition
    LaunchedEffect(Unit) {
        try {
            val alreadyGranted = viewModel.checkPermissions()
            if (alreadyGranted) {
                viewModel.initialize()
            }
        } catch (_: Exception) {
            // Proceed to onboarding if permission check fails
        } finally {
            checkedInitialPermissions = true
        }
    }

    if (!checkedInitialPermissions) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (!hasPermissions) {
        OnboardingScreen(
            onRequestPermissions = {
                permissionDenied = false
                permissionLauncher.launch(viewModel.healthConnect.permissions)
            },
            permissionDenied = permissionDenied
        )
    } else {
        BiosApp(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiosApp(viewModel: AppViewModel) {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }
    val unacknowledgedAlerts by viewModel.unacknowledgedAlerts.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncProgress by viewModel.initProgress.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEventSheet by remember { mutableStateOf(false) }
    var eventSheetParentId by remember { mutableStateOf<String?>(null) }
    var eventSheetDefaultType by remember { mutableStateOf<HealthEventType?>(null) }

    val context = LocalContext.current
    val monthlyAskScheduler = remember { MonthlyAskScheduler(context) }
    var showMonthlyAsk by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (monthlyAskScheduler.shouldShow()) {
            showMonthlyAsk = true
        }
    }

    HandleDeepLinks(navController)

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long)
            viewModel.clearError()
        }
    }

    val tabs = listOf(
        Triple("home", "Read", Icons.Default.FavoriteBorder),
        Triple("log", "Log", Icons.Default.EditNote),
        Triple("notice", "Notice", Icons.Default.Notifications),
        Triple("timeline", "Journal", Icons.AutoMirrored.Filled.MenuBook),
        Triple("settings", "Self", Icons.Default.AccountCircle)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTab == 3) { // Journal tab
                FloatingActionButton(onClick = {
                    eventSheetParentId = null
                    eventSheetDefaultType = null
                    showEventSheet = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Log health event")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, (route, label, icon) ->
                    val alertCount = if (route == "notice") unacknowledgedAlerts.size else 0
                    NavigationBarItem(
                        icon = {
                            if (alertCount > 0) {
                                BadgedBox(badge = { Badge { Text("$alertCount") } }) {
                                    Icon(icon, contentDescription = label)
                                }
                            } else {
                                Icon(icon, contentDescription = label)
                            }
                        },
                        label = { Text(label) },
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            navController.navigate(route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isSyncing) {
                LinearProgressIndicator(progress = { syncProgress }, modifier = Modifier.fillMaxWidth())
            }
            NavHost(navController = navController, startDestination = "home", modifier = Modifier.weight(1f)) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToActiveSubstances = { navController.navigate("active_substances") },
                    onNavigateToBodyLevels = { navController.navigate("body_levels") },
                    onNavigateToMetric = { metric ->
                        // SLEEP_DURATION has a dedicated dashboard richer
                        // than the generic trends view.
                        if (metric == MetricType.SLEEP_DURATION) {
                            navController.navigate("sleep_dashboard")
                        } else {
                            navController.navigate("trends?metric=${metric.key}") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
            composable("log") {
                LogScreen(
                    viewModel = viewModel,
                    onNavigateToPpgCapture = { navController.navigate("ppg_capture") },
                    onNavigateToBiomarkerEntry = { navController.navigate("biomarker_entry") },
                    onNavigateToClinicalEntry = { navController.navigate("clinical_entry") },
                    onNavigateToSleepEntry = { navController.navigate("sleep_entry") },
                    onNavigateToBbtEntry = { navController.navigate("bbt_entry") },
                    onNavigateToPeriodEntry = { navController.navigate("period_entry") },
                )
            }
            composable("ppg_capture") {
                PpgCaptureScreen(onBack = {
                    if (!navController.popBackStack()) {
                        (context as? android.app.Activity)?.finish()
                    }
                })
            }
            composable(
                route = "condition/{patternId}",
                arguments = listOf(navArgument("patternId") { type = NavType.StringType })
            ) { backStackEntry ->
                ConditionDetailScreen(
                    patternId = backStackEntry.arguments?.getString("patternId") ?: "",
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "trends?metric={metric}",
                arguments = listOf(
                    navArgument("metric") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val metricKey = backStackEntry.arguments?.getString("metric")
                TrendsScreen(
                    viewModel = viewModel,
                    initialMetric = metricKey?.let { com.bios.contracts.MetricType.fromKey(it) }
                )
            }
            composable("notice") {
                NoticeScreen(
                    viewModel = viewModel,
                    onNavigateToCondition = { patternId ->
                        navController.navigate("condition/$patternId")
                    },
                    onNavigateToCompanions = { navController.navigate("companions") },
                    onNavigateToReference = { navController.navigate("longevity_reference") },
                )
            }
            composable("timeline") {
                TimelineScreen(
                    viewModel = viewModel,
                    onRequestEventSheet = { parentId ->
                        eventSheetParentId = parentId
                        eventSheetDefaultType = null
                        showEventSheet = true
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToPrivacy = { navController.navigate("privacy") },
                    onNavigateToCompanions = { navController.navigate("companions") },
                    onNavigateToDataCoverage = { navController.navigate("data_coverage") },
                    onNavigateToBlePair = { navController.navigate("ble_pair") },
                    onNavigateToMedications = { navController.navigate("medications") },
                    onNavigateToImmunisations = { navController.navigate("immunisations") },
                    onNavigateToPreventiveCare = { navController.navigate("preventive_care") },
                    onNavigateToRiskProfile = { navController.navigate("risk_profile") },
                    onNavigateToPhysiologyState = { navController.navigate("physiology_state") },
                    onNavigateToFrailAssessment = { navController.navigate("frail_assessment") },
                    onNavigateToGoalsOfCare = { navController.navigate("goals_of_care") },
                    onNavigateToHeadacheDiary = { navController.navigate("headache_diary") },
                    onNavigateToFastStroke = { navController.navigate("fast_stroke") },
                    onNavigateToEsasCapture = { navController.navigate("esas_capture") },
                    onNavigateToTraditionalMedicine = { navController.navigate("traditional_medicine_context") },
                    onNavigateToEnvironmentalContext = { navController.navigate("environmental_context") },
                    onNavigateToEmergencyContacts = { navController.navigate("emergency_contacts") },
                    onNavigateToEcgStrips = { navController.navigate("ecg_strips") },
                    onNavigateToSurgicalRecovery = { navController.navigate("surgical_recovery") },
                )
            }
            // Self-surface routes — extracted to IdentityRoutes.kt to keep this file
            // under the 500-line cap and give new self-screens a single landing zone.
            identityRoutes(navController)
            composable("ble_pair") {
                val bleVm = remember(viewModel) {
                    com.bios.app.ui.ble.BleAirQualityPairViewModel(
                        adapter = viewModel.bleAirQualityAdapter,
                        ingestManager = viewModel.ingestManager,
                    )
                }
                com.bios.app.ui.ble.BleAirQualityPairScreen(
                    viewModel = bleVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("data_coverage") {
                val coverageVm = remember(viewModel) {
                    com.bios.app.ui.coverage.DataCoverageViewModel(viewModel)
                }
                com.bios.app.ui.coverage.DataCoverageScreen(
                    viewModel = coverageVm,
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable("biomarker_entry") {
                com.bios.app.ui.biomarkers.BiomarkerEntryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("clinical_entry") {
                com.bios.app.ui.clinical.ClinicalReadingEntryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("bbt_entry") {
                com.bios.app.ui.bbt.BbtEntryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("period_entry") {
                com.bios.app.ui.period.PeriodEntryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("sleep_entry") {
                com.bios.app.ui.sleep.SleepEntryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenDashboard = { navController.navigate("sleep_dashboard") }
                )
            }
            composable("sleep_dashboard") {
                com.bios.app.ui.sleep.SleepDashboardScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("active_substances") {
                com.bios.app.ui.intake.ActiveSubstancesScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("body_levels") {
                com.bios.app.ui.biomarkers.BiomarkerDashboardScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToMetricTrend = { metric ->
                        navController.navigate("trends?metric=${metric.key}") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("privacy") {
                com.bios.app.ui.privacy.PrivacyDashboardScreen(viewModel)
            }
            composable("companions") {
                com.bios.app.ui.companions.CompanionsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("longevity_reference") {
                val trackedMetrics by viewModel.trackedMetricTypes.collectAsState()
                var latestDirectValues by remember { mutableStateOf<Map<MetricType, Double>>(emptyMap()) }
                LaunchedEffect(Unit) {
                    val dao = viewModel.db.metricReadingDao()
                    val directKeys = BiomarkerReferences.all.mapNotNull { it.directMetric }
                    latestDirectValues = directKeys.mapNotNull { mt ->
                        dao.fetchLatest(mt.key, 1).firstOrNull()?.let { mt to it.value }
                    }.toMap()
                }
                LongevityReferenceScreen(
                    trackedMetrics = trackedMetrics,
                    latestDirectBiomarkerValues = latestDirectValues,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        } // Column
    }

    if (showMonthlyAsk) {
        MonthlyAskPopup(onClose = {
            monthlyAskScheduler.markShown()
            showMonthlyAsk = false
        })
    }

    if (showEventSheet) {
        HealthEventSheet(
            onDismiss = { showEventSheet = false },
            onSave = { input ->
                viewModel.createHealthEvent(
                    type = input.type,
                    title = input.title,
                    description = input.description,
                    parentEventId = input.parentEventId,
                    initialActionItems = input.initialActionItems
                )
                showEventSheet = false
            },
            parentEventId = eventSheetParentId,
            defaultType = eventSheetDefaultType
        )
    }
}
