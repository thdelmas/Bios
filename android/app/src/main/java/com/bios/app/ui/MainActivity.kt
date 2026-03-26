package com.bios.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.bios.app.ui.theme.BiosTheme
import com.bios.app.ui.timeline.TimelineScreen
import com.bios.app.ui.trends.TrendsScreen

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
    val isInitialized by viewModel.isInitialized.collectAsState()
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

    if (!checkedInitialPermissions || (hasPermissions && !isInitialized)) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    "Loading your health data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEventSheet by remember { mutableStateOf(false) }
    var eventSheetParentId by remember { mutableStateOf<String?>(null) }
    var eventSheetDefaultType by remember { mutableStateOf<HealthEventType?>(null) }

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
        Triple("trends", "Trends", Icons.Default.ShowChart),
        Triple("alerts", "Alerts", Icons.Default.Notifications),
        Triple("timeline", "Journal", Icons.Default.MenuBook),
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
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable("home") { HomeScreen(viewModel) }
            composable("trends") { TrendsScreen(viewModel) }
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
            composable("settings") { SettingsScreen(viewModel) }
        }
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
