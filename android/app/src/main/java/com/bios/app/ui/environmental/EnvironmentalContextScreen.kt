package com.bios.app.ui.environmental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bios.app.config.ClimateZone
import com.bios.app.config.EnvironmentalContextProvider

/**
 * Settings → Identity → Environmental context (#197).
 *
 * Owner-set: home elevation, climate zone, travel-elevation override.
 * All three feed [EnvironmentalContextProvider] which the AnomalyDetector
 * reads to modulate the SpO2 hard-cutoff and the heat / cold / altitude /
 * SAAD screen patterns. Nothing is auto-detected; nothing requires Location
 * permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentalContextScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val provider = remember(context) { EnvironmentalContextProvider(context) }

    var homeElevation by remember {
        mutableStateOf(provider.homeElevationM()?.let { it.toInt().toString() } ?: "")
    }
    var travelElevation by remember {
        mutableStateOf(provider.travelElevationM()?.let { it.toInt().toString() } ?: "")
    }
    var ownerLatitude by remember {
        mutableStateOf(provider.ownerLatitudeDeg()?.let { String.format("%.1f", it) } ?: "")
    }
    var climateZone by remember {
        mutableStateOf(provider.ownerClimateZone() ?: provider.climateZone())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Environmental context") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WhyThisMattersCard()

            // Home elevation
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Home elevation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Metres above sea level. La Paz ≈ 3640. Lhasa ≈ 3650. Denver ≈ 1610. Most coastal cities ≈ 0. Leave blank to use the regional default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = homeElevation,
                        onValueChange = { homeElevation = it.filter { c -> c.isDigit() } },
                        label = { Text("Elevation (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val parsed = homeElevation.toDoubleOrNull()
                            provider.setHomeElevationM(parsed)
                        },
                    ) { Text("Save") }
                }
            }

            // Travel override
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current altitude (travel)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Temporary override for trips. Clears when you set it back to blank. The altitude-sickness screen reads this value.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = travelElevation,
                        onValueChange = { travelElevation = it.filter { c -> c.isDigit() } },
                        label = { Text("Travel elevation (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val parsed = travelElevation.toDoubleOrNull()
                            provider.setTravelElevationM(parsed)
                        },
                    ) { Text("Save") }
                }
            }

            // Climate zone
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Climate zone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sets default heat / cold thresholds. Owner override beats the regional default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    for (zone in ClimateZone.entries) {
                        OutlinedButton(
                            onClick = {
                                climateZone = zone
                                provider.setOwnerClimateZone(zone)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (climateZone == zone) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else ButtonDefaults.outlinedButtonColors(),
                        ) {
                            Text(if (climateZone == zone) "✓ ${zone.displayName}" else zone.displayName)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            // Latitude (photoperiod)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Latitude", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Degrees north (negative = southern hemisphere). Used to compute daylight hours for the photoperiod-related patterns. Country centroid is fine; precision below 1° doesn't matter for daylength math.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ownerLatitude,
                        onValueChange = { ownerLatitude = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                        label = { Text("Latitude (°N)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val parsed = ownerLatitude.toDoubleOrNull()
                                ?.coerceIn(-90.0, 90.0)
                            provider.setOwnerLatitudeDeg(parsed)
                        },
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun WhyThisMattersCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Why this matters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Bios's blood-oxygen, heart-rate, and temperature thresholds " +
                    "were baselined for a moderate temperate climate at sea " +
                    "level. At 4000 m in the Andes or on the Tibetan plateau, " +
                    "an acclimatised adult can sit at 84 % SpO2 without " +
                    "pathology — the same value at sea level is a clinical " +
                    "emergency. Heat and cold have the same problem: the " +
                    "absolute cutoffs need to know where you are. Setting these " +
                    "values lets Bios alert when it should and stay quiet " +
                    "when nothing is wrong. Nothing here is auto-detected, no " +
                    "Location permission is required, and everything is owner-set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
