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
import androidx.compose.material.icons.automirrored.filled.ShowChart
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
import com.bios.app.ui.alerts.AlertsScreen
import com.bios.app.ui.home.HomeScreen
import com.bios.app.ui.onboarding.OnboardingScreen
import com.bios.app.ui.settings.SettingsScreen
import com.bios.app.model.HealthEventType
import com.bios.app.ui.journal.HealthEventSheet
import com.bios.app.ui.diagnostics.ConditionDetailScreen
import com.bios.app.ui.diagnostics.DiagnosticsScreen
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

    // Deep-link from CompanionAccessNotifier — opens Settings → Companion Apps.
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val intent = activity.intent ?: return@LaunchedEffect
        if (intent.getBooleanExtra(
                com.bios.app.provider.CompanionAccessNotifier.EXTRA_NAVIGATE_TO_COMPANIONS,
                false
            )
        ) {
            intent.removeExtra(com.bios.app.provider.CompanionAccessNotifier.EXTRA_NAVIGATE_TO_COMPANIONS)
            navController.navigate("companions")
        }
    }

    // Deep-link from DisconnectNotifier — opens Data Coverage so the
    // owner can reconnect the failing source.
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val intent = activity.intent ?: return@LaunchedEffect
        if (intent.getBooleanExtra(
                com.bios.app.alerts.DisconnectNotifier.EXTRA_NAVIGATE_TO_DATA_COVERAGE,
                false
            )
        ) {
            intent.removeExtra(com.bios.app.alerts.DisconnectNotifier.EXTRA_NAVIGATE_TO_DATA_COVERAGE)
            navController.navigate("data_coverage")
        }
    }

    // Deep-link from a companion (e.g. W2F "take a snapshot" CTA): bios://capture/ppg
    // lands directly on the PPG capture screen. popUpTo home/inclusive empties
    // the in-app back stack so pressing back from capture finishes the
    // activity and returns to the calling companion, not Bios Home.
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val data = activity.intent?.data ?: return@LaunchedEffect
        if (data.scheme == "bios" && data.host == "capture" && data.path == "/ppg") {
            activity.intent.data = null
            navController.navigate("ppg_capture") {
                popUpTo("home") { inclusive = true }
            }
        }
    }

    // Show errors as snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    val tabs = listOf(
        Triple("home", "Home", Icons.Default.FavoriteBorder),
        Triple("trends", "Trends", Icons.AutoMirrored.Filled.ShowChart),
        Triple("alerts", "Alerts", Icons.Default.Notifications),
        Triple("timeline", "Journal", Icons.AutoMirrored.Filled.MenuBook),
        Triple("settings", "Settings", Icons.Default.Settings)
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
                    val alertCount = if (route == "alerts") unacknowledgedAlerts.size else 0
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
                LinearProgressIndicator(
                    progress = { syncProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.weight(1f)
            ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToDiagnostics = { navController.navigate("diagnostics") },
                    onNavigateToPpgCapture = { navController.navigate("ppg_capture") },
                    onNavigateToCompanions = { navController.navigate("companions") },
                    onNavigateToMetric = { metric ->
                        selectedTab = tabs.indexOfFirst { it.first == "trends" }
                        navController.navigate("trends?metric=${metric.key}") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("ppg_capture") {
                PpgCaptureScreen(onBack = {
                    if (!navController.popBackStack()) {
                        (context as? android.app.Activity)?.finish()
                    }
                })
            }
            composable("diagnostics") {
                DiagnosticsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { patternId ->
                        navController.navigate("condition/$patternId")
                    },
                    onNavigateToReference = {
                        navController.navigate("longevity_reference")
                    }
                )
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
            composable("alerts") { AlertsScreen(viewModel) }
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
                    onNavigateToBiomarkerEntry = { navController.navigate("biomarker_entry") },
                    onNavigateToBbtEntry = { navController.navigate("bbt_entry") },
                    onNavigateToPeriodEntry = { navController.navigate("period_entry") },
                    onNavigateToSleepEntry = { navController.navigate("sleep_entry") },
                    onNavigateToDataCoverage = { navController.navigate("data_coverage") },
                    onNavigateToBlePair = { navController.navigate("ble_pair") }
                )
            }
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
                    onBack = { navController.popBackStack() }
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
