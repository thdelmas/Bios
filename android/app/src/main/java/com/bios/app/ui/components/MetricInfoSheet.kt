package com.bios.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.contracts.MetricType

/**
 * Pull-shaped explanation surface. Opens when the owner taps the info
 * affordance on a metric card. Pure pedagogy — what the metric is, what
 * shifts it, how Bios uses it. No prescription.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricInfoSheet(
    metricType: MetricType,
    onDismiss: () -> Unit,
) {
    val explanation = MetricExplanations.forMetric(metricType) ?: run {
        onDismiss()
        return
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                explanation.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            ExplanationSection("What it measures", explanation.whatItMeasures)
            ExplanationSection("Why it varies", explanation.whyItVaries)
            ExplanationSection("How Bios reads it", explanation.howBiosUsesIt)

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ExplanationSection(label: String, body: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
