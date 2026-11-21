package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.PersonalBaseline
import com.bios.contracts.MetricType
import java.util.Locale
import kotlin.math.abs

/**
 * One metric as it stood when an alert fired: the 24 h sensor average next
 * to the owner's own usual range (baseline p5–p95). Real units, never only σ.
 */
data class MeasuredSignal(
    val metricKey: String,
    val value: Double,
    val usualLow: Double,
    val usualHigh: Double,
    val zScore: Double,
    /** Sensor readings averaged into [value]; 0 when unknown. */
    val readingCount: Int = 0,
    /** Owner-facing names of the sources those readings came from. */
    val sources: List<String> = emptyList(),
) {
    val deviating: Boolean get() = abs(zScore) > AlertDetailContent.DEVIATION_Z
    val above: Boolean get() = zScore > 0
}

/**
 * Wording for an alert and its detail screen (docs/specs/instrument-only.md):
 * measured values next to the owner's usual range, and how large the
 * deviation is. No cause, no advice, no meaning. Pure so the copy is
 * unit-testable.
 */
object AlertDetailContent {

    /** Metrics the detector scores; also the "other metrics" list. */
    val WATCHED_METRICS = listOf(
        "heart_rate", "heart_rate_variability", "resting_heart_rate",
        "blood_oxygen", "respiratory_rate", "skin_temperature_deviation",
        "sleep_duration", "steps", "active_calories",
    )

    /** Same cut the detector uses to list a metric as deviating. */
    const val DEVIATION_Z = 1.5

    /** Where a value sits against the owner's range, for the strip's dot color. */
    enum class DeviationBand { INSIDE, EDGE, OUTSIDE, FAR }

    private const val EDGE_Z = 1.0
    private const val FAR_Z = 3.0

    fun deviationBand(zScore: Double): DeviationBand = when {
        abs(zScore) >= FAR_Z -> DeviationBand.FAR
        abs(zScore) > DEVIATION_Z -> DeviationBand.OUTSIDE
        abs(zScore) >= EDGE_Z -> DeviationBand.EDGE
        else -> DeviationBand.INSIDE
    }

    /** What each tier says: the size of the deviation, nothing about what it means. */
    fun tierMeaning(tier: AlertTier): String = when (tier) {
        AlertTier.OBSERVATION -> "A small deviation from your usual range, kept for the record."
        AlertTier.NOTICE -> "A few metrics are a little outside your usual range."
        AlertTier.ADVISORY -> "Several metrics are clearly outside your usual range."
        AlertTier.WARNING -> "Several metrics are outside your usual range, at least one by a wide margin."
        AlertTier.URGENT -> "Recorded by an earlier version, which had a higher tier. This version does not use it."
    }

    /** Detection window: the detector averages the 24 h before it runs. */
    const val WINDOW_MILLIS = 24L * 3600 * 1000

    fun signal(
        metricKey: String,
        value: Double,
        baseline: PersonalBaseline,
        readingCount: Int = 0,
        sources: List<String> = emptyList(),
    ) = MeasuredSignal(
        metricKey = metricKey,
        value = value,
        usualLow = baseline.p5,
        usualHigh = baseline.p95,
        zScore = baseline.zScore(value),
        readingCount = readingCount,
        sources = sources,
    )

    /** "3 readings · COROS, Bios (inferred)"; empty when the count is unknown. */
    fun evidenceLine(s: MeasuredSignal): String {
        if (s.readingCount <= 0) return ""
        val n = if (s.readingCount == 1) "1 reading" else "${s.readingCount} readings"
        return if (s.sources.isEmpty()) n else "$n · ${s.sources.joinToString(", ")}"
    }

    /** One line under the strips; the band and dot are defined once, not per row. */
    const val STRIP_LEGEND =
        "Band: your usual range (5th–95th percentile of the previous two weeks). " +
            "Dot: the 24 h average the alert was raised on. Each row has its own scale. " +
            "\"Now\" is the same 24 hours re-read today, when later syncs changed it."

    fun label(metricKey: String): String = when (metricKey) {
        "heart_rate_variability" -> "heart rate variability (HRV)"
        "skin_temperature_deviation" -> "skin temperature"
        "blood_oxygen" -> "blood oxygen"
        else -> metricKey.replace("_", " ")
    }

    fun unit(metricKey: String): String = MetricType.fromKey(metricKey)?.unit?.symbol.orEmpty()

    fun formatValue(metricKey: String, value: Double): String {
        if (metricKey == "sleep_duration") return formatDuration(value)
        val decimals = when (metricKey) {
            "skin_temperature_deviation" -> 2
            "respiratory_rate" -> 1
            else -> 0
        }
        val number = String.format(Locale.getDefault(), "%.${decimals}f", value)
        val unit = unit(metricKey)
        return if (unit.isEmpty()) number else "$number $unit"
    }

    /** Seconds as "8h24": hours and zero-padded minutes, no unit word. */
    fun formatDuration(seconds: Double): String {
        val minutes = (seconds / SECONDS_PER_MINUTE).toLong()
        return "${minutes / MINUTES_PER_HOUR}h${String.format(Locale.ROOT, "%02d", minutes % MINUTES_PER_HOUR)}"
    }

    private const val SECONDS_PER_MINUTE = 60.0
    private const val MINUTES_PER_HOUR = 60L

    /**
     * "now 63 bpm from 1 reading · COROS" when the same window re-read today
     * differs from what the alert was raised on; empty when it reads the same
     * or when there is nothing to compare.
     */
    fun nowLine(atDetection: MeasuredSignal, now: MeasuredSignal?): String {
        if (now == null) return ""
        val same = formatValue(now.metricKey, now.value) == formatValue(atDetection.metricKey, atDetection.value) &&
            now.readingCount == atDetection.readingCount
        if (same) return ""
        val evidence = evidenceLine(now)
        val from = if (evidence.isEmpty()) "" else " from $evidence"
        return "now ${formatValue(now.metricKey, now.value)}$from"
    }

    /** "66 bpm, usually 53–60" */
    fun valueVersusUsual(s: MeasuredSignal): String =
        "${formatValue(s.metricKey, s.value)}, usually " +
            "${formatValue(s.metricKey, s.usualLow).substringBefore(' ')}–" +
            formatValue(s.metricKey, s.usualHigh)

    /** One-line data statement for the notification body. */
    fun notificationLine(s: MeasuredSignal): String {
        val name = label(s.metricKey).replaceFirstChar { it.uppercase() }
        val dir = if (s.above) "above" else "below"
        return "$name ${formatValue(s.metricKey, s.value)} over the last 24 h, $dir your usual " +
            "${formatValue(s.metricKey, s.usualLow).substringBefore(' ')}–" +
            "${formatValue(s.metricKey, s.usualHigh)}."
    }

    /** How an alert comes about, shown under the measurements. */
    const val HOW_COMPARED =
        "Bios compares the last 24 hours with your own previous two weeks. " +
            "An alert says the numbers differ from your usual range, not why."
}
