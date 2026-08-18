package com.bios.app.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.model.SleepStage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Per-night stage timeline. Lanes follow clinical hypnogram order
 * (awake on top, deep at the bottom) and only the stages actually
 * recorded get a lane — phone-inferred nights carry AWAKE/LIGHT only by
 * design, and a fixed 4-lane chart would misread as broken data there.
 * Colors are hoisted outside the Canvas lambda (DrawScope isn't
 * composable), following MetricChartCard.
 */
@Composable
fun HypnogramCard(bands: List<HypnogramBands.StageBand>, nightLabel: String) {
    if (bands.isEmpty()) return
    val lanes = HypnogramBands.stagesPresent(bands)
    val laneColors = lanes.associateWith { stageColor(it) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Stages · $nightLabel",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Row {
                LaneLabels(lanes)
                HypnogramCanvas(bands, lanes, laneColors)
            }
            TimeAxisRow(startMs = bands.first().startMs, endMs = bands.last().endMs)
        }
    }
}

@Composable
private fun LaneLabels(lanes: List<SleepStage>) {
    Column(modifier = Modifier.width(44.dp)) {
        lanes.forEach { stage ->
            Box(modifier = Modifier.height(LANE_HEIGHT_DP.dp)) {
                Text(
                    stageLabel(stage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
    }
}

@Composable
private fun HypnogramCanvas(
    bands: List<HypnogramBands.StageBand>,
    lanes: List<SleepStage>,
    laneColors: Map<SleepStage, Color>,
) {
    val chartStart = bands.first().startMs
    val chartEnd = bands.last().endMs
    val span = remember(chartStart, chartEnd) { (chartEnd - chartStart).coerceAtLeast(1L) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height((lanes.size * LANE_HEIGHT_DP).dp)
    ) {
        val laneHeightPx = size.height / lanes.size
        val bandHeightPx = laneHeightPx * BAND_FILL_FRACTION
        bands.forEach { band ->
            val lane = lanes.indexOf(band.stage)
            if (lane < 0) return@forEach
            val x0 = (band.startMs - chartStart).toFloat() / span * size.width
            val x1 = (band.endMs - chartStart).toFloat() / span * size.width
            drawRect(
                color = laneColors.getValue(band.stage),
                topLeft = Offset(x0, lane * laneHeightPx + (laneHeightPx - bandHeightPx) / 2f),
                size = Size((x1 - x0).coerceAtLeast(MIN_BAND_PX), bandHeightPx),
            )
        }
    }
}

@Composable
private fun TimeAxisRow(startMs: Long, endMs: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            formatTime(startMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatTime(endMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Legend chip row for an owner-facing stage color key, stages-present only. */
@Composable
fun HypnogramLegend(bands: List<HypnogramBands.StageBand>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HypnogramBands.stagesPresent(bands).forEach { stage ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(stageColor(stage), RoundedCornerShape(3.dp)),
                )
                Text(
                    stageLabel(stage),
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun stageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.AWAKE -> MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
    SleepStage.REM -> MaterialTheme.colorScheme.tertiary
    SleepStage.LIGHT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    SleepStage.DEEP -> MaterialTheme.colorScheme.primary
}

private fun stageLabel(stage: SleepStage): String = when (stage) {
    SleepStage.AWAKE -> "Awake"
    SleepStage.REM -> "REM"
    SleepStage.LIGHT -> "Light"
    SleepStage.DEEP -> "Deep"
}

private const val LANE_HEIGHT_DP = 26
private const val BAND_FILL_FRACTION = 0.62f
private const val MIN_BAND_PX = 1.5f

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun formatTime(millis: Long): String = timeFormat.format(Date(millis))
