package com.bios.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
        val alreadyGranted = viewModel.checkPermissions()
        if (alreadyGranted) {
            viewModel.initialize()
        }
        checkedInitialPermissions = true
    }

    if (!checkedInitialPermissions) {
        // Still checking — show nothing to avoid flash
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

    val tabs = listOf(
        Triple("home", "Home", Icons.Default.FavoriteBorder),
        Triple("trends", "Trends", Icons.Default.ShowChart),
        Triple("alerts", "Alerts", Icons.Default.Notifications),
        Triple("timeline", "Journal", Icons.Default.MenuBook),
        Triple("settings", "Settings", Icons.Default.Settings)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, (route, label, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
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
            composable("timeline") { TimelineScreen(viewModel) }
            composable("settings") { SettingsScreen(viewModel) }
        }
    }
}
