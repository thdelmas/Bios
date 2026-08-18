package com.bios.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType

/**
 * Label + icon for a vitals tile. The historical eight keep their curated
 * short labels and icons; every other metric falls back to the contract's
 * [MetricType.readableName] and a per-domain icon, so any key the owner
 * pins can sit on the grid without a bespoke mapping. Maps rather than
 * `when` blocks keep detekt's complexity gate quiet as domains grow.
 */
private val TILE_LABEL_OVERRIDES: Map<MetricType, String> = mapOf(
    MetricType.SLEEP_DURATION to "Sleep",
    MetricType.SLEEP_EFFICIENCY to "Sleep Eff.",
    MetricType.HEART_RATE to "Heart Rate",
    MetricType.HEART_RATE_VARIABILITY to "HRV",
    MetricType.BLOOD_OXYGEN to "SpO2",
    MetricType.RESPIRATORY_RATE to "Resp. Rate",
    MetricType.STEPS to "Steps",
    MetricType.SKIN_TEMPERATURE_DEVIATION to "Skin Temp",
)

private val TILE_ICON_OVERRIDES: Map<MetricType, ImageVector> = mapOf(
    MetricType.SLEEP_DURATION to Icons.Default.Bedtime,
    MetricType.SLEEP_EFFICIENCY to Icons.Default.Percent,
    MetricType.HEART_RATE to Icons.Default.Favorite,
    MetricType.HEART_RATE_VARIABILITY to Icons.AutoMirrored.Filled.ShowChart,
    MetricType.BLOOD_OXYGEN to Icons.Default.Air,
    MetricType.RESPIRATORY_RATE to Icons.Default.Air,
    MetricType.STEPS to Icons.AutoMirrored.Filled.DirectionsWalk,
    MetricType.SKIN_TEMPERATURE_DEVIATION to Icons.Default.Thermostat,
)

private val DOMAIN_ICONS: Map<MetricDomain, ImageVector> = mapOf(
    MetricDomain.CARDIOVASCULAR to Icons.Default.Favorite,
    MetricDomain.RESPIRATORY to Icons.Default.Air,
    MetricDomain.TEMPERATURE to Icons.Default.Thermostat,
    MetricDomain.SLEEP to Icons.Default.Bedtime,
    MetricDomain.ACTIVITY to Icons.AutoMirrored.Filled.DirectionsWalk,
    MetricDomain.METABOLIC to Icons.Default.Bolt,
    MetricDomain.RECOVERY to Icons.Default.SelfImprovement,
    MetricDomain.WOMENS_HEALTH to Icons.Default.WaterDrop,
    MetricDomain.MENTAL_HEALTH to Icons.Default.Mood,
    MetricDomain.NEUROLOGICAL to Icons.Default.Psychology,
    MetricDomain.INTAKE to Icons.Default.Medication,
    MetricDomain.SAFETY to Icons.Default.Shield,
    MetricDomain.ENVIRONMENT to Icons.Default.Eco,
    MetricDomain.BIOMARKER to Icons.Default.Science,
    MetricDomain.ANTHROPOMETRY to Icons.Default.Straighten,
    MetricDomain.BODY_COMPOSITION to Icons.Default.MonitorWeight,
)

internal fun vitalsTileLabel(metric: MetricType): String =
    TILE_LABEL_OVERRIDES[metric] ?: metric.readableName

internal fun vitalsTileIcon(metric: MetricType): ImageVector =
    TILE_ICON_OVERRIDES[metric]
        ?: DOMAIN_ICONS[metric.domain]
        ?: Icons.Default.Science
