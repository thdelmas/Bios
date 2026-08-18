package com.bios.app.ui.period

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bios.app.model.CyclePhase
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month calendar painting each day by its derived [CyclePhase] (from the
 * reproductive DB's CYCLE_PHASE series), with onset markers and a today
 * outline. Read-only instrument surface: it renders what the derivations
 * produced, predicts nothing forward, and grades nothing.
 */
@Composable
fun CycleCalendar(
    month: YearMonth,
    phaseByDay: Map<LocalDate, CyclePhase>,
    onsetDays: Set<LocalDate>,
    selectedDay: LocalDate?,
    onSelectDay: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonthHeader(month = month, onMonthChange = onMonthChange)
        WeekdayRow()
        MonthGrid(
            month = month,
            phaseByDay = phaseByDay,
            onsetDays = onsetDays,
            selectedDay = selectedDay,
            onSelectDay = onSelectDay,
        )
        PhaseLegend()
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
            )
        }
        Text(
            "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(
            onClick = { onMonthChange(month.plusMonths(1)) },
            enabled = month < YearMonth.now(),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
            )
        }
    }
}

@Composable
private fun WeekdayRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        for (dayOfWeek in orderedWeekDays()) {
            Text(
                dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    phaseByDay: Map<LocalDate, CyclePhase>,
    onsetDays: Set<LocalDate>,
    selectedDay: LocalDate?,
    onSelectDay: (LocalDate) -> Unit,
) {
    val firstDay = month.atDay(1)
    val leading = orderedWeekDays().indexOf(firstDay.dayOfWeek)
    val cells: List<LocalDate?> =
        List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (day != null) {
                            DayCell(
                                day = day,
                                phase = phaseByDay[day],
                                isOnset = day in onsetDays,
                                isSelected = day == selectedDay,
                                onClick = { onSelectDay(day) },
                            )
                        }
                    }
                }
                repeat(DAYS_PER_WEEK - week.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    phase: CyclePhase?,
    isOnset: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background = phase?.let { phaseColor(it) }
        ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        day == LocalDate.now() -> MaterialTheme.colorScheme.outline
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(background, RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${day.dayOfMonth}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        if (isOnset) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
        }
    }
}

@Composable
private fun PhaseLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CyclePhase.entries.forEach { phase ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(phaseColor(phase), RoundedCornerShape(3.dp)),
                )
                Text(
                    phaseLabel(phase),
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun phaseColor(phase: CyclePhase): Color = when (phase) {
    CyclePhase.MENSTRUAL -> MaterialTheme.colorScheme.errorContainer
    CyclePhase.FOLLICULAR -> MaterialTheme.colorScheme.secondaryContainer
    CyclePhase.OVULATORY -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
    CyclePhase.LUTEAL -> MaterialTheme.colorScheme.tertiaryContainer
}

internal fun phaseLabel(phase: CyclePhase): String = when (phase) {
    CyclePhase.MENSTRUAL -> "Menstrual"
    CyclePhase.FOLLICULAR -> "Follicular"
    CyclePhase.OVULATORY -> "Ovulatory"
    CyclePhase.LUTEAL -> "Luteal"
}

private const val DAYS_PER_WEEK = 7

private fun orderedWeekDays(): List<java.time.DayOfWeek> {
    val first = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return (0L until DAYS_PER_WEEK).map { first.plus(it) }
}
