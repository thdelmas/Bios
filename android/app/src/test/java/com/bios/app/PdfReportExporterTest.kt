package com.bios.app

import com.bios.app.config.BiomarkerBand
import com.bios.app.export.bandFillColor
import com.bios.app.export.bandLabel
import com.bios.app.export.bandTextColor
import com.bios.app.export.chartValueRange
import com.bios.app.export.fmtValue
import com.bios.app.export.formatDateRange
import com.bios.app.export.formatGeneratedFooter
import com.bios.app.export.formatSummaryStats
import com.bios.app.export.mapY
import com.bios.app.model.MetricReading
import com.bios.app.model.PersonalBaseline
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Pure-logic tests for [com.bios.app.export.PdfReportExporter]'s
 * deterministic helpers — formatting, band colouring, chart-coordinate
 * math. Canvas rendering itself requires android.graphics.Paint at
 * runtime, so it's covered by build-and-eyeball, not unit tests.
 */
class PdfReportExporterTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    init {
        // Pin dates so the test passes in every CI timezone.
        TimeZone.setDefault(utc)
    }

    // --- formatDateRange ---

    @Test
    fun `formatDateRange renders ISO yyyy-MM-dd start to end`() {
        // 2026-01-15 00:00:00 UTC → epoch 1768435200000
        val start = 1768435200000L
        // 2026-02-14 00:00:00 UTC → 30 days later
        val end = start + 30L * 24 * 3600 * 1000
        assertEquals("2026-01-15 to 2026-02-14", formatDateRange(start, end))
    }

    // --- formatGeneratedFooter ---

    @Test
    fun `footer states on-device generation and zero data egress`() {
        val footer = formatGeneratedFooter(1768435200000L) // 2026-01-15 UTC
        assertTrue(
            "Footer must declare the generation date",
            footer.contains("2026-01-15"),
        )
        assertTrue(
            "Footer must reassure the doctor & owner that no data left the device",
            footer.contains("No data left this device"),
        )
    }

    // --- formatSummaryStats ---

    @Test
    fun `summary stats with no readings explains absence rather than printing 0`() {
        val stats = formatSummaryStats(MetricType.HEART_RATE, emptyList(), baseline(60.0))
        // Anti-regression: a previous draft rendered "Current: 0bpm" with empty
        // readings, which would look like a real reading to a doctor.
        assertTrue(stats.contains("No"))
        assertTrue(stats.contains("readings"))
    }

    @Test
    fun `summary stats include current, mean, and baseline range when all are available`() {
        val readings = listOf(
            reading(MetricType.HEART_RATE, 58.0, t = 1L),
            reading(MetricType.HEART_RATE, 62.0, t = 2L),
            reading(MetricType.HEART_RATE, 70.0, t = 3L), // current
        )
        val stats = formatSummaryStats(MetricType.HEART_RATE, readings, baseline(60.0, p5 = 50.0, p95 = 72.0))
        // Current uses the last reading (chronologically, given the fetch is ASC).
        assertTrue("Current must be the last reading", stats.contains("Current: 70"))
        // Window mean = (58+62+70)/3 = 63.33 → formats as "63.3"
        assertTrue("Window mean must be present", stats.contains("Window mean: 63.3"))
        assertTrue("Baseline p5-p95 must be present", stats.contains("Baseline p5–p95: 50.0–72.0"))
    }

    @Test
    fun `summary stats omit baseline line when no baseline exists yet`() {
        val readings = listOf(reading(MetricType.HEART_RATE, 70.0))
        val stats = formatSummaryStats(MetricType.HEART_RATE, readings, baseline = null)
        assertTrue(stats.contains("Current"))
        assertTrue("No baseline → no p5/p95 line", !stats.contains("Baseline"))
    }

    // --- fmtValue (used to render every numeric stat) ---

    @Test
    fun `fmtValue chooses 1-decimal under 100 and 0-decimal at-or-above 100`() {
        // 1 decimal for small numbers (HRV, SpO2, BBT, etc.).
        assertEquals("70.0", fmtValue(70.0))
        assertEquals("4.2", fmtValue(4.2))
        // 0 decimal for big numbers (Steps, etc.) to avoid clutter.
        assertEquals("100", fmtValue(100.0))
        assertEquals("8523", fmtValue(8523.4))
        // Negatives use absolute-value cutoff (e.g., glucose delta).
        assertEquals("-150", fmtValue(-150.3))
    }

    // --- band colour / label ---

    @Test
    fun `every band has a distinct fill and text colour`() {
        val fills = setOf(
            bandFillColor(BiomarkerBand.NORMAL),
            bandFillColor(BiomarkerBand.BORDERLINE),
            bandFillColor(BiomarkerBand.CONCERNING),
            bandFillColor(null),
        )
        assertEquals("Each band (incl. null) must paint a different cell colour", 4, fills.size)

        val texts = setOf(
            bandTextColor(BiomarkerBand.NORMAL),
            bandTextColor(BiomarkerBand.BORDERLINE),
            bandTextColor(BiomarkerBand.CONCERNING),
            bandTextColor(null),
        )
        assertEquals("Each band must use a distinct text colour", 4, texts.size)
        // Text colour must contrast with fill — at minimum, not equal.
        for (band in listOf(BiomarkerBand.NORMAL, BiomarkerBand.BORDERLINE, BiomarkerBand.CONCERNING, null)) {
            assertNotEquals("Text on a $band fill must differ from the fill", bandFillColor(band), bandTextColor(band))
        }
    }

    @Test
    fun `band label uses an em-dash for null so the column never reads as a band`() {
        assertEquals("normal", bandLabel(BiomarkerBand.NORMAL))
        assertEquals("borderline", bandLabel(BiomarkerBand.BORDERLINE))
        assertEquals("concerning", bandLabel(BiomarkerBand.CONCERNING))
        assertEquals("—", bandLabel(null))
    }

    // --- mapY ---

    @Test
    fun `mapY maps min to chartBottom and max to chartTop`() {
        // Top-left origin: chartTop has the SMALLEST y, chartBottom the LARGEST.
        assertEquals(200f, mapY(value = 50.0, valueMin = 50.0, valueMax = 100.0, chartTop = 0f, chartBottom = 200f), 0.01f)
        assertEquals(0f, mapY(value = 100.0, valueMin = 50.0, valueMax = 100.0, chartTop = 0f, chartBottom = 200f), 0.01f)
        // Midpoint maps to vertical centre.
        assertEquals(100f, mapY(value = 75.0, valueMin = 50.0, valueMax = 100.0, chartTop = 0f, chartBottom = 200f), 0.01f)
    }

    @Test
    fun `mapY clamps values outside the range so chart points never leave the box`() {
        val y = mapY(value = 1000.0, valueMin = 50.0, valueMax = 100.0, chartTop = 0f, chartBottom = 200f)
        assertEquals("Above-range values clamp to chartTop", 0f, y, 0.01f)
        val yLow = mapY(value = -1.0, valueMin = 50.0, valueMax = 100.0, chartTop = 0f, chartBottom = 200f)
        assertEquals("Below-range values clamp to chartBottom", 200f, yLow, 0.01f)
    }

    @Test
    fun `mapY returns chartBottom when valueMax is not greater than valueMin`() {
        // Degenerate input — constant series — would otherwise NaN/inf. Drop
        // to the floor instead so the chart still renders.
        assertEquals(200f, mapY(value = 70.0, valueMin = 70.0, valueMax = 70.0, chartTop = 0f, chartBottom = 200f), 0.01f)
    }

    // --- chartValueRange ---

    @Test
    fun `chartValueRange spans both readings and baseline so the band never spills`() {
        val readings = listOf(reading(MetricType.HEART_RATE, 60.0), reading(MetricType.HEART_RATE, 80.0))
        val baseline = baseline(70.0, p5 = 50.0, p95 = 90.0)
        val (min, max) = chartValueRange(readings, baseline)
        assertTrue("min must be ≤ smallest baseline edge", min <= 50.0)
        assertTrue("max must be ≥ largest baseline edge", max >= 90.0)
    }

    @Test
    fun `chartValueRange handles empty readings without crashing`() {
        val (min, max) = chartValueRange(emptyList(), baseline = null)
        // Caller still draws the chart frame; we just need finite values.
        assertTrue(min.isFinite())
        assertTrue(max.isFinite())
        assertTrue(max > min)
    }

    @Test
    fun `chartValueRange pads constant-valued readings so the line is not on the axis`() {
        val readings = listOf(reading(MetricType.HEART_RATE, 65.0), reading(MetricType.HEART_RATE, 65.0))
        val (min, max) = chartValueRange(readings, baseline = null)
        assertTrue("min must sit below the constant value", min < 65.0)
        assertTrue("max must sit above the constant value", max > 65.0)
    }

    // --- helpers ---

    private fun reading(metric: MetricType, value: Double, t: Long = 0L): MetricReading =
        MetricReading(
            metricType = metric.key,
            value = value,
            timestamp = t,
            sourceId = "src",
            confidence = 90,
        )

    private fun baseline(mean: Double, p5: Double = mean - 10.0, p95: Double = mean + 10.0): PersonalBaseline =
        PersonalBaseline(
            metricType = MetricType.HEART_RATE.key,
            mean = mean,
            stdDev = (p95 - p5) / 4.0,
            p5 = p5,
            p95 = p95,
        )
}
