package com.bios.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    onRequestPermissions: () -> Unit,
    permissionDenied: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            "Bios",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Early illness detection from your wearable data",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(48.dp))

        FeatureRow(
            icon = Icons.Default.Sensors,
            title = "Learns your baseline",
            description = "Bios builds a personal model of your normal vitals over 14 days, then watches for deviations that may signal illness."
        )

        Spacer(Modifier.height(24.dp))

        FeatureRow(
            icon = Icons.Default.Lock,
            title = "Private by design",
            description = "All data stays on this device. No accounts, no cloud. Encrypted at rest with AES-256."
        )

        Spacer(Modifier.height(24.dp))

        FeatureRow(
            icon = Icons.Default.Favorite,
            title = "Powered by Health Connect",
            description = "Bios reads heart rate, HRV, SpO2, sleep, temperature, and more from your wearable via Health Connect."
        )

        Spacer(Modifier.weight(1f))

        if (permissionDenied) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Health Connect permissions are required for Bios to work. Please grant access to continue.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                if (permissionDenied) "Grant Permissions" else "Get Started",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(32.dp)
                .padding(top = 2.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
