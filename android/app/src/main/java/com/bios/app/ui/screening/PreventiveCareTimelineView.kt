package com.bios.app.ui.screening

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.screening.CadenceKind
import com.bios.app.screening.PreventiveCareTimeline
import com.bios.app.screening.PreventiveCareTimeline.Section
import com.bios.app.screening.ScreeningCatalogEntry
import com.bios.app.screening.ScreeningStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a [PreventiveCareTimeline] result as a single chronological axis:
 * upcoming items above a TODAY anchor, done-and-current items below it, then
 * the undated "not yet recorded" and (collapsed) "not applicable" sections.
 *
 * A `LazyListScope` extension rather than its own `LazyColumn` so the
 * Preventive Care screen owns one scroll container (demographics + manifesto
 * cards scroll with the timeline). Pure presentation — every placement
 * decision was already made by [PreventiveCareTimeline]; this only draws it.
 */
fun LazyListScope.preventiveCareTimeline(
    items: List<PreventiveCareTimeline.Item>,
    now: Long,
    notApplicableExpanded: Boolean,
    onToggleNotApplicable: () -> Unit,
    onRecord: (ScreeningCatalogEntry) -> Unit,
) {
    val upcoming = items.filter { it.section == Section.UPCOMING }
    val past = items.filter { it.section == Section.PAST }
    val notRecorded = items.filter { it.section == Section.NOT_RECORDED }
    val notApplicable = items.filter { it.section == Section.NOT_APPLICABLE }

    if (upcoming.isNotEmpty()) {
        item(key = "header_upcoming") { SectionLabel("Upcoming") }
        items(upcoming, key = { it.entry.key }) { item ->
            TimelineNode(status = item.status) {
                ScreeningCard(item = item, now = now, onRecord = { onRecord(item.entry) })
            }
        }
    }

    item(key = "today_anchor") { TodayAnchor(now) }

    if (past.isNotEmpty()) {
        item(key = "header_past") { SectionLabel("Done & current") }
        items(past, key = { it.entry.key }) { item ->
            TimelineNode(status = item.status) {
                ScreeningCard(item = item, now = now, onRecord = { onRecord(item.entry) })
            }
        }
    }

    if (notRecorded.isNotEmpty()) {
        item(key = "header_not_recorded") { SectionLabel("Not yet recorded · ${notRecorded.size}") }
        items(notRecorded, key = { it.entry.key }) { item ->
            TimelineNode(status = item.status) {
                ScreeningCard(item = item, now = now, onRecord = { onRecord(item.entry) })
            }
        }
    }

    if (notApplicable.isNotEmpty()) {
        item(key = "header_not_applicable") {
            NotApplicableHeader(
                count = notApplicable.size,
                expanded = notApplicableExpanded,
                onToggle = onToggleNotApplicable,
            )
        }
        if (notApplicableExpanded) {
            items(notApplicable, key = { it.entry.key }) { item ->
                NotApplicableRow(item)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun TodayAnchor(now: Long) {
    val accent = MaterialTheme.colorScheme.tertiary
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(16.dp)
                .drawBehind {
                    drawCircle(accent, radius = 7.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
                },
        )
        Text(
            "TODAY · ${monthYear(now)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(color = accent, modifier = Modifier.weight(1f))
    }
}

/**
 * Leading timeline gutter: a vertical connector line through the row with a
 * dot whose colour/fill reflects the screening's status. Filled = needs or
 * has attention; outlined = neutral / not yet started.
 */
@Composable
private fun TimelineNode(
    status: ScreeningStatus,
    content: @Composable () -> Unit,
) {
    val color = statusColor(status)
    val filled = when (status) {
        is ScreeningStatus.DueNow, is ScreeningStatus.IntervalElapsed, is ScreeningStatus.Current -> true
        else -> false
    }
    val line = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .drawBehind {
                    val cx = size.width / 2
                    val cy = 18.dp.toPx()
                    val r = 6.dp.toPx()
                    drawLine(line, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.dp.toPx())
                    if (filled) {
                        drawCircle(color, r, Offset(cx, cy))
                    } else {
                        drawCircle(surface, r, Offset(cx, cy))
                        drawCircle(color, r, Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
                    }
                },
        )
        Box(modifier = Modifier.weight(1f).padding(start = 4.dp, bottom = 10.dp)) { content() }
    }
}

@Composable
private fun ScreeningCard(
    item: PreventiveCareTimeline.Item,
    now: Long,
    onRecord: () -> Unit,
) {
    val entry = item.entry
    val status = item.status
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            dateLabel(item, now)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor(status),
                )
            }
            Text(entry.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(statusText(entry, status), style = MaterialTheme.typography.bodySmall, color = statusColor(status))
            Text(
                "${cadenceDescriptor(entry)} • ${ageLabel(entry)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRecord, modifier = Modifier.fillMaxWidth()) { Text("Record date") }
        }
    }
}

@Composable
private fun NotApplicableHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "NOT APPLICABLE · $count",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotApplicableRow(item: PreventiveCareTimeline.Item) {
    val reason = (item.status as? ScreeningStatus.NotEligible)?.reason ?: ""
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.entry.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (reason.isNotBlank()) {
                Text(
                    "Doesn't apply: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Date chip above the card: when it's next due (upcoming) or was done (past). */
private fun dateLabel(item: PreventiveCareTimeline.Item, now: Long): String? {
    val date = item.anchorDate ?: return null
    return when (item.section) {
        Section.UPCOMING ->
            if (date <= now) "Was due ${monthYear(date)}" else "Due ${monthYear(date)}"
        Section.PAST -> "Done ${monthYear(date)}"
        else -> null
    }
}

private fun statusText(entry: ScreeningCatalogEntry, status: ScreeningStatus): String {
    // One-time tests (Lp(a), HIV / hepatitis serology, AAA) carry a sentinel
    // Int.MAX_VALUE cadence; once recorded they're current effectively forever.
    val oneTime = entry.cadenceMonths == Int.MAX_VALUE
    return when (status) {
        is ScreeningStatus.NotEligible -> status.reason
        ScreeningStatus.NoRecord ->
            if (oneTime) "Recommended once — no record yet" else "No record yet"
        is ScreeningStatus.Current ->
            if (oneTime) "Recorded ${status.monthsSinceLast} mo ago — one-time, no repeat needed"
            else "Last ${status.monthsSinceLast} mo ago — next in ${status.monthsUntilDue} mo"
        is ScreeningStatus.DueNow ->
            if (status.monthsOverdue > 0) "Overdue by ${status.monthsOverdue} mo"
            else "Due now (last ${status.monthsSinceLast} mo ago)"
        // Minimum-interval (routine checkup) states — neutral, never "overdue".
        is ScreeningStatus.WithinInterval ->
            "Recommended again in ${status.monthsUntilEligible} mo (last ${status.monthsSinceLast} mo ago)"
        is ScreeningStatus.IntervalElapsed ->
            "Eligible again (last ${status.monthsSinceLast} mo ago)"
    }
}

@Composable
private fun statusColor(status: ScreeningStatus): Color = when (status) {
    is ScreeningStatus.DueNow -> MaterialTheme.colorScheme.error
    ScreeningStatus.NoRecord -> MaterialTheme.colorScheme.secondary
    is ScreeningStatus.Current -> MaterialTheme.colorScheme.primary
    is ScreeningStatus.WithinInterval -> MaterialTheme.colorScheme.primary
    is ScreeningStatus.IntervalElapsed -> MaterialTheme.colorScheme.secondary
    is ScreeningStatus.NotEligible -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun cadenceLabel(months: Int): String = when {
    months == Int.MAX_VALUE -> "one-time"
    months % 12 == 0 -> "${months / 12} yr"
    else -> "$months mo"
}

/**
 * Cadence wording by [CadenceKind]. Recurring entries read "every X";
 * minimum-interval checkups read "recommended delay: X" so the owner sees
 * delay-since-last advice, not a clock to fail.
 */
private fun cadenceDescriptor(entry: ScreeningCatalogEntry): String =
    when (entry.cadenceKind) {
        CadenceKind.RECURRING -> "Cadence: every ${cadenceLabel(entry.cadenceMonths)}"
        CadenceKind.MIN_INTERVAL_SINCE_LAST ->
            "Recommended delay: ${cadenceLabel(entry.cadenceMonths)} since last"
    }

private fun ageLabel(entry: ScreeningCatalogEntry): String =
    if (entry.maxAge != null) "ages ${entry.minAge}–${entry.maxAge}"
    else "from age ${entry.minAge}"

private val monthYearFormat = SimpleDateFormat("MMM yyyy", Locale.US)
private fun monthYear(millis: Long): String = monthYearFormat.format(Date(millis))
