package com.bios.app.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bios.app.alerts.AlertDetailContent
import com.bios.app.model.AlertTier
import com.bios.app.ui.theme.BiosTokens

/**
 * The alert's tier on the four-step scale (criterion 7): segments filled up
 * to the current tier in its color, the four names underneath with the
 * current one in that color, and one line saying what the tier means in
 * Bios's own behaviour. Replaces the bare badge as the screen's heading.
 */
@Composable
internal fun TierMeter(tier: AlertTier) {
    val color = BiosTokens.tierColor(tier)
    val empty = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = EMPTY_ALPHA)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AlertTier.entries.forEach { step ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(SEGMENT_HEIGHT)
                        .background(if (step <= tier) color else empty, RoundedCornerShape(SEGMENT_HEIGHT / 2)),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AlertTier.entries.forEach { step ->
                val current = step == tier
                Text(
                    step.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = LABEL_SIZE, letterSpacing = 0.sp),
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = if (current) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Text(
            AlertDetailContent.tierMeaning(tier),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val SEGMENT_HEIGHT = 6.dp
private val LABEL_SIZE = 10.sp
private const val EMPTY_ALPHA = 0.25f
