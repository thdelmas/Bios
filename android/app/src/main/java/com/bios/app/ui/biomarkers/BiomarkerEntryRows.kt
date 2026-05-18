package com.bios.app.ui.biomarkers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiomarkerEntryRepo
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Row for a single recent biomarker entry. Fetches its
 * [com.bios.app.data.BiomarkerContext] lazily so the parent doesn't have
 * to materialise every reading's payload up front. When the entry has an
 * attached lab-report URI, a "View source" affordance opens it via
 * [Intent.ACTION_VIEW]; if the source app is gone or the file was
 * deleted, the fallback message states that — Bios is the index, not the
 * archive.
 *
 * Extracted from [BiomarkerEntryScreen] so the parent file stays under
 * the 500-line cap.
 */
@Composable
internal fun RecentBiomarkerRow(reading: MetricReading, repo: BiomarkerEntryRepo) {
    val metric = MetricType.fromKey(reading.metricType)
    val context = LocalContext.current
    var sourceUri by remember(reading.id) { mutableStateOf<String?>(null) }
    var openFailed by remember(reading.id) { mutableStateOf(false) }

    LaunchedEffect(reading.id) {
        sourceUri = repo.fetchContext(reading.id).sourceUri?.takeIf { it.isNotBlank() }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                metric?.readableName ?: reading.metricType,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatBiomarkerValue(reading.value)} ${metric?.unit?.symbol.orEmpty()}  ·  " +
                    formatBiomarkerDate(reading.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            reading.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    "“$it”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            sourceUri?.let { uri ->
                Spacer(Modifier.height(2.dp))
                if (openFailed) {
                    // Honest fallback: Bios is the index, not the archive.
                    // If the source file is gone, the row says so rather
                    // than claiming a working link.
                    Text(
                        "Source file not available — was it moved or deleted?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TextButton(
                        onClick = { openFailed = !openSourceUri(context, uri) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
                    ) { Text("View source") }
                }
            }
        }
    }
}

/**
 * Opens [uriString] in whichever app the device routes the MIME type to.
 * Returns `true` when the chooser actually fires; `false` for a missing
 * resolver or a revoked persistable permission (the source app could have
 * been uninstalled, or the file could have been moved off whichever
 * provider granted the URI).
 */
internal fun openSourceUri(context: Context, uriString: String): Boolean {
    val uri = try { Uri.parse(uriString) } catch (_: Exception) { return false }
    val mime = context.contentResolver.getType(uri) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private val biomarkerRowDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

internal fun formatBiomarkerDate(millis: Long): String = biomarkerRowDateFormat.format(Date(millis))

internal fun formatBiomarkerValue(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else "%.2f".format(value)
