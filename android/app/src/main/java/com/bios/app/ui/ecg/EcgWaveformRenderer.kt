package com.bios.app.ui.ecg

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bios.app.model.EcgStrip
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal Compose Canvas waveform renderer for [EcgStrip] (#188).
 *
 * Pull-side surface — the owner has already imported the strip and is
 * reviewing it (or showing it to a clinician). The manifesto's
 * "instrument, not coach" stance scopes evaluation to the owner; this
 * renderer does no rhythm interpretation, no annotation, no warnings.
 * It draws the waveform and stops.
 *
 * Scope: single pass through the samples, scale-to-fit, draw as a
 * polyline. No grid, no calipers, no R-peak markers — those are
 * clinical-app features the owner would use a dedicated cardiology
 * viewer for.
 *
 * The renderer decodes [EcgStrip.samples] according to
 * [EcgStrip.sampleEncoding] (today: `int16_le`) and applies the
 * voltage scale + offset to recover millivolts before the y-axis
 * normalisation. Other encodings fall through to a flat line —
 * unsupported is not the same as broken, and the owner can still see
 * that the strip is on file.
 */
@Composable
fun EcgWaveformCanvas(
    strip: EcgStrip,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFFD32F2F),
) {
    val mv = remember(strip.id) { decodeMillivolts(strip) }
    Box(modifier = modifier.fillMaxWidth().height(160.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            if (mv.isEmpty()) return@Canvas
            val w = size.width
            val h = size.height
            // Symmetric vertical scale: peak amplitude maps to ±h/2-ish.
            val peak = mv.maxOf { kotlin.math.abs(it) }.coerceAtLeast(0.001)
            val midY = h / 2f
            val yScale = (h * 0.45f) / peak.toFloat()
            val xStep = if (mv.size > 1) w / (mv.size - 1) else w
            val path = Path()
            path.moveTo(0f, midY - (mv[0].toFloat() * yScale))
            for (i in 1 until mv.size) {
                val x = i * xStep
                val y = midY - (mv[i].toFloat() * yScale)
                path.lineTo(x, y)
            }
            // Baseline reference line (zero-mv axis), drawn under the wave.
            drawLine(
                color = lineColor.copy(alpha = 0.15f),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 1f,
            )
            drawPath(path = path, color = lineColor, style = Stroke(width = 2f))
        }
    }
}

/**
 * Decodes the strip's BLOB into a list of millivolt samples for
 * rendering. Public for unit-testing the byte-to-mv math; the
 * renderer itself is the only caller in production.
 */
internal fun decodeMillivolts(strip: EcgStrip): DoubleArray {
    return when (strip.sampleEncoding) {
        "int16_le" -> decodeInt16Le(strip.samples, strip.voltageScale, strip.voltageOffset)
        else -> DoubleArray(0)
    }
}

private fun decodeInt16Le(bytes: ByteArray, scale: Double, offset: Double): DoubleArray {
    if (bytes.size < 2) return DoubleArray(0)
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val count = bytes.size / 2
    val out = DoubleArray(count)
    for (i in 0 until count) {
        out[i] = buf.short.toInt() * scale + offset
    }
    return out
}
