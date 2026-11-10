package com.bios.app.ui.alerts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.bios.app.alerts.AlertDetailContent
import com.bios.app.alerts.MeasuredSignal
import com.bios.app.alerts.SignalStrip
import com.bios.app.alerts.SignalStripGeometry
import com.bios.app.ui.theme.BiosTokens

/**
 * One watched signal as a range strip (docs/specs/alert-detail.md, criterion
 * 6): the owner's usual range as a band, the alert's 24 h average as a dot,
 * the band's ends labelled in real units. Text stays in text tokens; only the
 * dot carries color, by distance from the range: inside (success), at the
 * edge (warning), outside (attention), far outside (error). The caption names
 * the evidence behind the average (readings and sources).
 */
@Composable
internal fun SignalStripRow(signal: MeasuredSignal, status: String, nowLine: String = "") {
    val geometry = SignalStrip.geometry(signal)
    val dotColor = when (AlertDetailContent.deviationBand(signal.zScore)) {
        AlertDetailContent.DeviationBand.INSIDE -> BiosTokens.success
        AlertDetailContent.DeviationBand.EDGE -> BiosTokens.warning
        AlertDetailContent.DeviationBand.OUTSIDE -> BiosTokens.attention
        AlertDetailContent.DeviationBand.FAR -> BiosTokens.error
    }
    val zones = StripZoneColors(
        inside = BiosTokens.success.copy(alpha = BAND_ALPHA),
        outside = BiosTokens.attention.copy(alpha = TRACK_ALPHA),
        far = BiosTokens.error.copy(alpha = TRACK_ALPHA),
    )
    val ringColor = MaterialTheme.colorScheme.surface
    val tickStyle = BiosTokens.instrument.copy(
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val measurer = rememberTextMeasurer()
    val lowLabel = AlertDetailContent.formatValue(signal.metricKey, signal.usualLow).substringBefore(' ')
    val highLabel = AlertDetailContent.formatValue(signal.metricKey, signal.usualHigh)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                AlertDetailContent.label(signal.metricKey).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                AlertDetailContent.formatValue(signal.metricKey, signal.value),
                style = BiosTokens.instrument.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(STRIP_HEIGHT)) {
            drawStrip(geometry, zones, dotColor, ringColor)
            drawTicks(geometry, measurer, tickStyle, lowLabel, highLabel)
        }
        val caption = listOf(status, AlertDetailContent.evidenceLine(signal), nowLine)
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val STRIP_HEIGHT = 40.dp
private val BAND_HEIGHT = 10.dp
private val DOT_RADIUS = 6.dp
private val RING_WIDTH = 2.dp
private val TRACK_WIDTH = 2.dp
private const val BAND_ALPHA = 0.30f
private const val TRACK_ALPHA = 0.40f

/** Tints for the three zones of the track; same bands as the dot's color. */
internal data class StripZoneColors(val inside: Color, val outside: Color, val far: Color)

/** Zones, band and dot share one centre line in the upper part of the canvas. */
private fun DrawScope.drawStrip(
    g: SignalStripGeometry,
    zones: StripZoneColors,
    dotColor: Color,
    ringColor: Color,
) {
    val centreY = BAND_HEIGHT.toPx()
    val inset = DOT_RADIUS.toPx() + RING_WIDTH.toPx()
    val usable = size.width - 2 * inset
    fun x(fraction: Float) = inset + fraction * usable
    val trackHeight = TRACK_WIDTH.toPx() * 2
    fun zone(from: Float, to: Float, color: Color) {
        if (to <= from) return
        drawRect(color, Offset(x(from), centreY - trackHeight / 2), Size(x(to) - x(from), trackHeight))
    }
    zone(0f, g.farLow, zones.far)
    zone(g.farLow, g.bandStart, zones.outside)
    zone(g.bandEnd, g.farHigh, zones.outside)
    zone(g.farHigh, 1f, zones.far)
    val bandHeight = BAND_HEIGHT.toPx()
    drawRoundRect(
        color = zones.inside,
        topLeft = Offset(x(g.bandStart), centreY - bandHeight / 2),
        size = Size((x(g.bandEnd) - x(g.bandStart)).coerceAtLeast(bandHeight / 2), bandHeight),
        cornerRadius = CornerRadius(bandHeight / 2),
    )
    val centre = Offset(x(g.dot), centreY)
    drawCircle(color = dotColor, radius = DOT_RADIUS.toPx(), center = centre)
    drawCircle(color = ringColor, radius = DOT_RADIUS.toPx(), center = centre, style = Stroke(RING_WIDTH.toPx()))
}

/** p5 and p95 in units under the band ends; kept inside the canvas width. */
private fun DrawScope.drawTicks(
    g: SignalStripGeometry,
    measurer: TextMeasurer,
    style: TextStyle,
    lowLabel: String,
    highLabel: String,
) {
    val inset = DOT_RADIUS.toPx() + RING_WIDTH.toPx()
    val usable = size.width - 2 * inset
    val top = BAND_HEIGHT.toPx() * 2
    val low = measurer.measure(lowLabel, style)
    val high = measurer.measure(highLabel, style)
    val lowX = (inset + g.bandStart * usable - low.size.width / 2f).coerceIn(0f, size.width - low.size.width)
    val highX = (inset + g.bandEnd * usable - high.size.width / 2f).coerceIn(0f, size.width - high.size.width)
    drawText(low, topLeft = Offset(lowX, top))
    // Do not let the two ticks overprint when the band is narrow.
    if (highX >= lowX + low.size.width + 4.dp.toPx()) drawText(high, topLeft = Offset(highX, top))
}
