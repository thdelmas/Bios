package com.bios.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import com.bios.app.config.BiomarkerBand
import com.bios.app.config.BiomarkerBands
import com.bios.app.config.RegionConfigProvider
import com.bios.app.data.BiosDatabase
import com.bios.app.model.MetricReading
import com.bios.app.model.PersonalBaseline
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a single-page A4 PDF the owner can share with a doctor before or
 * after a consult. Designed to be readable in ~30 seconds. Generated on the
 * device, written to `context.cacheDir`, shared via the standard share sheet
 * (`FileProvider` + `ACTION_SEND`) — same pattern as the other exporters.
 *
 * Manifesto context (PR #103). Bios renders structured data the owner
 * already has access to. Cells are coloured against published clinical
 * reference ranges from [RegionConfigProvider] (ADA/NCEP/AHA/etc.), not
 * Bios's evaluation — the bands are the ones a doctor would apply. The
 * owner picks the focal metric and may attach a free-text note; the system
 * never picks for them. This surface is pull-side: invoked by the owner,
 * shown to a person of the owner's choosing.
 *
 * Excludes WOMENS_HEALTH metrics by construction — those readings live in
 * the isolated [com.bios.app.data.ReproductiveDatabase]. Including them in
 * a default summary would collapse the isolation that DB exists to provide.
 */
class PdfReportExporter(
    private val context: Context,
    private val db: BiosDatabase,
) {
    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()

    suspend fun exportToPdf(
        focalMetric: MetricType,
        windowDays: Int = 30,
        ownerNote: String? = null,
    ): File {
        require(focalMetric.domain != MetricDomain.WOMENS_HEALTH) {
            "Reproductive metrics live in the isolated DB; PDF export excludes them by design"
        }

        val endMillis = System.currentTimeMillis()
        val startMillis = endMillis - windowDays.toLong() * 24 * 3600 * 1000

        val focalReadings = readingDao.fetch(focalMetric.key, startMillis, endMillis)
        val focalBaseline = baselineDao.fetch(focalMetric.key)
        val biomarkerLatest = MetricType.entries
            .filter { it.domain == MetricDomain.BIOMARKER }
            .mapNotNull { metric ->
                readingDao.fetchLatest(metric.key, 1).firstOrNull()?.let { metric to it }
            }
            .sortedBy { it.first.readableName }

        val bands = RegionConfigProvider.forCurrentLocale().clinicalThresholds.biomarkerBands

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PX, PAGE_HEIGHT_PX, 1).create()
        val page = document.startPage(pageInfo)
        renderPage(
            canvas = page.canvas,
            focalMetric = focalMetric,
            focalReadings = focalReadings,
            focalBaseline = focalBaseline,
            biomarkerLatest = biomarkerLatest,
            bands = bands,
            startMillis = startMillis,
            endMillis = endMillis,
            ownerNote = ownerNote,
            versionName = appVersionName(context),
            generatedAtMillis = endMillis,
        )
        document.finishPage(page)

        val filename = "bios_doctor_summary_${timestampForFile()}.pdf"
        val file = File(context.cacheDir, filename)
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun timestampForFile(): String =
        SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())

    companion object {
        // A4 at 72 DPI.
        const val PAGE_WIDTH_PX = 595
        const val PAGE_HEIGHT_PX = 842
        const val MARGIN_PX = 36
        const val INNER_WIDTH_PX = PAGE_WIDTH_PX - 2 * MARGIN_PX
    }
}

// --- Pure-function layout/format helpers (internal so tests can exercise) ---

internal fun appVersionName(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0"
    } catch (_: Exception) {
        "0.0"
    }

internal fun formatDateRange(startMillis: Long, endMillis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return "${fmt.format(Date(startMillis))} to ${fmt.format(Date(endMillis))}"
}

internal fun formatGeneratedFooter(generatedAtMillis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return "Generated on-device by Bios on ${fmt.format(Date(generatedAtMillis))}. " +
        "No data left this device during generation."
}

internal fun formatSummaryStats(
    metric: MetricType,
    readings: List<MetricReading>,
    baseline: PersonalBaseline?,
): String {
    if (readings.isEmpty()) {
        return "No ${metric.readableName.lowercase()} readings in this window."
    }
    val current = readings.last().value
    val mean = readings.map { it.value }.average()
    val parts = mutableListOf<String>()
    parts.add("Current: ${fmtValue(current)}${metric.unit.symbol}")
    parts.add("Window mean: ${fmtValue(mean)}${metric.unit.symbol}")
    if (baseline != null) {
        parts.add("Baseline p5–p95: ${fmtValue(baseline.p5)}–${fmtValue(baseline.p95)}${metric.unit.symbol}")
    }
    return parts.joinToString("  |  ")
}

internal fun fmtValue(v: Double): String = when {
    kotlin.math.abs(v) >= 100 -> String.format(Locale.US, "%.0f", v)
    else -> String.format(Locale.US, "%.1f", v)
}

internal fun bandFillColor(band: BiomarkerBand?): Int = when (band) {
    BiomarkerBand.NORMAL -> 0xFFE6F4EA.toInt()
    BiomarkerBand.BORDERLINE -> 0xFFFFF4CE.toInt()
    BiomarkerBand.CONCERNING -> 0xFFFADBD8.toInt()
    null -> 0xFFF5F5F5.toInt()
}

internal fun bandTextColor(band: BiomarkerBand?): Int = when (band) {
    BiomarkerBand.NORMAL -> 0xFF1E4620.toInt()
    BiomarkerBand.BORDERLINE -> 0xFF6F4F00.toInt()
    BiomarkerBand.CONCERNING -> 0xFF7A1C1C.toInt()
    null -> 0xFF333333.toInt()
}

internal fun bandLabel(band: BiomarkerBand?): String = when (band) {
    BiomarkerBand.NORMAL -> "normal"
    BiomarkerBand.BORDERLINE -> "borderline"
    BiomarkerBand.CONCERNING -> "concerning"
    null -> "—"
}

/**
 * Maps a value into a chart's pixel-Y coordinate (top-left origin). Returns
 * the y for [value] inside [chartTop, chartBottom] when [value] lies within
 * [valueMin, valueMax]; clamped at the edges otherwise.
 */
internal fun mapY(
    value: Double,
    valueMin: Double,
    valueMax: Double,
    chartTop: Float,
    chartBottom: Float,
): Float {
    if (valueMax <= valueMin) return chartBottom
    val clamped = value.coerceIn(valueMin, valueMax)
    val frac = (clamped - valueMin) / (valueMax - valueMin)
    return (chartBottom - frac * (chartBottom - chartTop)).toFloat()
}

/**
 * Returns the [min, max] value-range for the chart, padded by 5% on each
 * side so points don't sit on the axes. When a baseline is present, the
 * p5–p95 band is included in the range so the shaded baseline never spills
 * off the chart.
 */
internal fun chartValueRange(
    readings: List<MetricReading>,
    baseline: PersonalBaseline?,
): Pair<Double, Double> {
    val values = readings.map { it.value }
    val baselineMin = baseline?.p5
    val baselineMax = baseline?.p95
    val candidates = (values + listOfNotNull(baselineMin, baselineMax))
    if (candidates.isEmpty()) return 0.0 to 1.0
    val min = candidates.min()
    val max = candidates.max()
    if (max <= min) return min - 1.0 to max + 1.0
    val pad = (max - min) * 0.05
    return min - pad to max + pad
}

// --- Page rendering (file-private, big block — kept off the class) -----------

private fun renderPage(
    canvas: Canvas,
    focalMetric: MetricType,
    focalReadings: List<MetricReading>,
    focalBaseline: PersonalBaseline?,
    biomarkerLatest: List<Pair<MetricType, MetricReading>>,
    bands: Map<MetricType, BiomarkerBands>,
    startMillis: Long,
    endMillis: Long,
    ownerNote: String?,
    versionName: String,
    generatedAtMillis: Long,
) {
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 16f
        isFakeBoldText = true
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF444444.toInt(); textSize = 10f
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF222222.toInt(); textSize = 11f
    }
    val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
    val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt(); textSize = 9f
    }
    val bandPaint = Paint().apply { style = Paint.Style.FILL }
    val baselineBandPaint = Paint().apply {
        color = 0x33339966
        style = Paint.Style.FILL
    }
    val meanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF339966.toInt()
        strokeWidth = 1f
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1F4E79.toInt()
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1F4E79.toInt()
        style = Paint.Style.FILL
    }
    val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCCCCCC.toInt()
        strokeWidth = 0.5f
        style = Paint.Style.STROKE
    }

    val left = PdfReportExporter.MARGIN_PX.toFloat()
    val right = (PdfReportExporter.PAGE_WIDTH_PX - PdfReportExporter.MARGIN_PX).toFloat()
    var y = PdfReportExporter.MARGIN_PX.toFloat() + 14f

    // --- Header ---
    canvas.drawText("Bios health summary", left, y, titlePaint)
    y += 18f
    canvas.drawText(
        "Window: ${formatDateRange(startMillis, endMillis)}   |   Bios v$versionName",
        left, y, labelPaint
    )
    y += 14f
    if (!ownerNote.isNullOrBlank()) {
        for (line in wrapText(ownerNote.trim(), bodyPaint, (right - left).toInt()).take(2)) {
            canvas.drawText(line, left, y, bodyPaint)
            y += 13f
        }
    }
    y += 6f

    // --- Focal metric chart ---
    val unitSuffix = if (focalMetric.unit.symbol.isNotBlank()) " (${focalMetric.unit.symbol})" else ""
    canvas.drawText("${focalMetric.readableName}$unitSuffix", left, y, bodyPaint.also { it.isFakeBoldText = true })
    bodyPaint.isFakeBoldText = false
    y += 4f
    val chartTop = y
    val chartBottom = chartTop + 220f
    canvas.drawRect(left, chartTop, right, chartBottom, axisPaint)

    val (valueMin, valueMax) = chartValueRange(focalReadings, focalBaseline)
    if (focalBaseline != null) {
        val yP95 = mapY(focalBaseline.p95, valueMin, valueMax, chartTop, chartBottom)
        val yP5 = mapY(focalBaseline.p5, valueMin, valueMax, chartTop, chartBottom)
        canvas.drawRect(left, yP95, right, yP5, baselineBandPaint)
        val yMean = mapY(focalBaseline.mean, valueMin, valueMax, chartTop, chartBottom)
        canvas.drawLine(left, yMean, right, yMean, meanLinePaint)
    }

    if (focalReadings.isNotEmpty()) {
        val span = (endMillis - startMillis).coerceAtLeast(1L).toDouble()
        val path = Path()
        focalReadings.forEachIndexed { i, r ->
            val xFrac = ((r.timestamp - startMillis).coerceAtLeast(0L)) / span
            val x = (left + xFrac * (right - left)).toFloat()
            val yPt = mapY(r.value, valueMin, valueMax, chartTop, chartBottom)
            if (i == 0) path.moveTo(x, yPt) else path.lineTo(x, yPt)
            canvas.drawCircle(x, yPt, 1.5f, pointPaint)
        }
        canvas.drawPath(path, linePaint)
    } else {
        canvas.drawText(
            "No readings in this window.",
            left + 8f, chartTop + 110f, labelPaint
        )
    }

    canvas.drawText(fmtValue(valueMax), right + 2f, chartTop + 8f, labelPaint)
    canvas.drawText(fmtValue(valueMin), right + 2f, chartBottom, labelPaint)
    y = chartBottom + 16f

    // Summary stats line.
    canvas.drawText(
        formatSummaryStats(focalMetric, focalReadings, focalBaseline),
        left, y, bodyPaint
    )
    y += 18f

    // --- Biomarker table ---
    if (biomarkerLatest.isNotEmpty()) {
        canvas.drawText("Recent lab biomarkers", left, y, bodyPaint.also { it.isFakeBoldText = true })
        bodyPaint.isFakeBoldText = false
        y += 12f
        canvas.drawText(
            "Cells coloured against published clinical reference ranges " +
                "(ADA / NCEP / AHA, etc.) from the in-app reference. Bios does not interpret values.",
            left, y, captionPaint
        )
        y += 12f

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val colMetricRight = left + 240f
        val colValueRight = left + 320f
        val colDateRight = left + 410f
        val rowHeight = 16f

        // Header row
        canvas.drawText("Biomarker", left + 4f, y + 10f, labelPaint)
        canvas.drawText("Value", colMetricRight + 4f, y + 10f, labelPaint)
        canvas.drawText("Date", colValueRight + 4f, y + 10f, labelPaint)
        canvas.drawText("Band", colDateRight + 4f, y + 10f, labelPaint)
        y += rowHeight

        // Body rows — clipped if they'd overrun the footer.
        val footerTopY = (PdfReportExporter.PAGE_HEIGHT_PX - PdfReportExporter.MARGIN_PX - 28).toFloat()
        val rowsThatFit = ((footerTopY - y) / rowHeight).toInt().coerceAtLeast(0)
        val overflow = biomarkerLatest.size - rowsThatFit

        for ((i, pair) in biomarkerLatest.withIndex()) {
            if (i >= rowsThatFit) break
            val (metric, reading) = pair
            val band = bands[metric]?.classify(reading.value)
            bandPaint.color = bandFillColor(band)
            canvas.drawRect(left, y, right, y + rowHeight - 2f, bandPaint)
            rowPaint.color = bandTextColor(band)
            canvas.drawText(metric.readableName, left + 4f, y + 11f, rowPaint)
            val valueText = "${fmtValue(reading.value)} ${metric.unit.symbol}".trim()
            canvas.drawText(valueText, colMetricRight + 4f, y + 11f, rowPaint)
            canvas.drawText(dateFmt.format(Date(reading.timestamp)), colValueRight + 4f, y + 11f, rowPaint)
            canvas.drawText(bandLabel(band), colDateRight + 4f, y + 11f, rowPaint)
            y += rowHeight
        }
        if (overflow > 0) {
            canvas.drawText(
                "+ $overflow more biomarker${if (overflow == 1) "" else "s"} not shown (one-page MVP).",
                left, y + 11f, captionPaint
            )
        }
    } else {
        canvas.drawText(
            "No lab biomarkers entered yet. Add values via Settings → Add Lab Values.",
            left, y, labelPaint
        )
    }

    // --- Footer ---
    val footerY = (PdfReportExporter.PAGE_HEIGHT_PX - PdfReportExporter.MARGIN_PX).toFloat()
    canvas.drawText(formatGeneratedFooter(generatedAtMillis), left, footerY, captionPaint)
}

/**
 * Simple word-wrap for the owner note. Returns at most two lines worth of
 * input width-fitting; the caller can take(2) to clip. Trailing partial
 * words are appended as their own line so nothing is silently dropped.
 */
internal fun wrapText(text: String, paint: Paint, maxWidthPx: Int): List<String> {
    val words = text.split(' ').filter { it.isNotEmpty() }
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (paint.measureText(candidate) <= maxWidthPx) {
            if (current.isEmpty()) current.append(word) else { current.append(' '); current.append(word) }
        } else {
            if (current.isNotEmpty()) lines.add(current.toString())
            current = StringBuilder(word)
        }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines
}

