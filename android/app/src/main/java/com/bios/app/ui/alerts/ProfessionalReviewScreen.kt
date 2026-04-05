package com.bios.app.ui.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.model.*
import com.bios.app.ui.AppViewModel

/**
 * Owner-initiated professional review flow.
 *
 * Shows a preview of exactly what will be shared, lets the owner
 * choose a share method, and records the professional's response.
 * No data leaves the device without explicit owner action at every step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalReviewScreen(
    anomalyId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val anomaly by viewModel.anomalyForReview.collectAsState()
    val existingReviews by viewModel.reviewsForAnomaly.collectAsState()

    LaunchedEffect(anomalyId) {
        viewModel.loadAnomalyForReview(anomalyId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Professional Review") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (anomaly == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Alert not found.")
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Privacy notice
            PrivacyNoticeCard()

            // Alert summary
            AlertSummaryCard(anomaly!!)

            // Existing reviews
            if (existingReviews.isNotEmpty()) {
                Text(
                    "Previous Reviews",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                existingReviews.forEach { review ->
                    ReviewHistoryCard(review)
                }
            }

            // New review flow
            NewReviewSection(anomaly!!, viewModel, onBack)
        }
    }
}

@Composable
private fun PrivacyNoticeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2196F3).copy(alpha = 0.08f)
        )
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Bios never sends data to any service. You control what to share, how to share it, and with whom. Nothing leaves this device without your explicit action.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlertSummaryCard(anomaly: Anomaly) {
    val tier = AlertTier.fromLevel(anomaly.severity)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Alert Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(anomaly.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Severity: ${tier.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(anomaly.explanation, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NewReviewSection(
    anomaly: Anomaly,
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    var windowDays by remember { mutableIntStateOf(14) }
    var includeExplanation by remember { mutableStateOf(true) }
    var includeBaselines by remember { mutableStateOf(false) }
    var selectedMethod by remember { mutableStateOf<ShareMethod?>(null) }
    var showPreview by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Get Professional Eyes On This",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Choose what to include and how to share it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Data window
            Text("Data window", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 14, 30).forEach { days ->
                    FilterChip(
                        selected = windowDays == days,
                        onClick = { windowDays = days },
                        label = { Text("${days}d") }
                    )
                }
            }

            // Include options
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeExplanation, onCheckedChange = { includeExplanation = it })
                Text("Include anomaly explanation", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeBaselines, onCheckedChange = { includeBaselines = it })
                Text("Include baseline context", style = MaterialTheme.typography.bodySmall)
            }

            // Share method
            Text("Share method", style = MaterialTheme.typography.labelMedium)
            ShareMethod.entries.forEach { method ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedMethod == method,
                        onClick = { selectedMethod = method }
                    )
                    Column {
                        Text(method.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Preview button
            OutlinedButton(
                onClick = { showPreview = !showPreview },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (showPreview) "Hide Preview" else "Preview What Will Be Shared")
            }

            if (showPreview) {
                SharePreviewCard(anomaly, windowDays, includeExplanation, includeBaselines)
            }

            // Create review
            Button(
                onClick = {
                    viewModel.createProfessionalReview(
                        anomalyId = anomaly.id,
                        shareMethod = selectedMethod,
                        sharedWindowDays = windowDays,
                        sharedExplanation = includeExplanation,
                        sharedBaselines = includeBaselines
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMethod != null
            ) {
                Text("Create Review Request")
            }
        }
    }
}

@Composable
private fun SharePreviewCard(
    anomaly: Anomaly,
    windowDays: Int,
    includeExplanation: Boolean,
    includeBaselines: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "What the professional will see:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2196F3)
            )

            Text("Alert: ${anomaly.title}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Severity: ${AlertTier.fromLevel(anomaly.severity).label}",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Data window: last $windowDays days", style = MaterialTheme.typography.bodySmall)
            Text("Metrics: ${anomaly.metricTypes}", style = MaterialTheme.typography.bodySmall)

            if (includeExplanation) {
                Text("Explanation: ${anomaly.explanation}", style = MaterialTheme.typography.bodySmall)
            }
            if (includeBaselines) {
                Text("Baseline data: included", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReviewHistoryCard(review: ProfessionalReview) {
    val status = ReviewStatus.fromLevel(review.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(status.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                review.shareMethod?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            review.professionalNotes?.let {
                Text("Notes: $it", style = MaterialTheme.typography.bodySmall)
            }
            review.recommendation?.let { key ->
                val rec = ProfessionalRecommendation.entries.find { it.key == key }
                rec?.let {
                    Text("Recommendation: ${it.label}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
