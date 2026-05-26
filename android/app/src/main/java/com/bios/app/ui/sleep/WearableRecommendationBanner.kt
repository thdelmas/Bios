package com.bios.app.ui.sleep

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One-time dismissable banner for the sleep dashboard (#245).
 *
 * Surface contract — manifesto framing:
 *   - States a fact (phone-only sleep tracking is unreliable).
 *   - Offers an option (look at recommended wearables).
 *   - Does not push the owner toward buying anything.
 *
 * Honest expectations carried in copy: even a wearable gives reliable
 * total sleep time and onset/offset, not reliable stage classification.
 * That matches Gadgetbridge's own conclusion after a decade of work.
 */
@Composable
internal fun WearableRecommendationBanner(
    onSeeRecommendations: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Phone-only sleep tracking is unreliable",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Bios's phone-only inference has produced only low-confidence " +
                    "or missing rows for the past week. A wearable gives reliable " +
                    "total sleep time and onset/offset (stage classification stays " +
                    "approximate even on premium hardware).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                Spacer(Modifier.height(0.dp))
                TextButton(onClick = onSeeRecommendations) {
                    Text("See recommendations")
                }
            }
        }
    }
}

/**
 * SharedPreferences-backed store for the banner's dismissed-at
 * timestamp. The Compose call site reads on first render and writes
 * on dismissal. Lifted out as `object` functions so the test side
 * doesn't need a fake Context.
 */
internal object WearableRecommendationStore {

    private const val PREFS_NAME = "bios_settings"
    private const val KEY_DISMISSED_AT_MS = "wearable_recommendation_dismissed_at_ms"

    fun getDismissedAtMs(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getLong(KEY_DISMISSED_AT_MS, 0L)
        return if (raw > 0L) raw else null
    }

    fun markDismissedNow(context: Context, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DISMISSED_AT_MS, now)
            .apply()
    }
}
