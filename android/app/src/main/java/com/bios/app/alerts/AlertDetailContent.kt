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
 * Pull-side copy for the alert detail screen (docs/specs/alert-detail.md).
 * The owner opens this screen, so explanatory content is frame-legal, but it
 * keeps the push-side register anyway: possibilities, never an inferred cause
 * about this owner, never a diagnosis. Pure so the copy is unit-testable.
 */
object AlertDetailContent {

    /** Metrics the holistic detector scores; also the "other signals" list. */
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

    /**
     * What each tier means in Bios's own behaviour (criterion 7). States what
     * the app did and how large the shift is; no instruction to the owner.
     */
    fun tierMeaning(tier: AlertTier): String = when (tier) {
        AlertTier.OBSERVATION -> "Logged quietly, no notification. A small shift kept for the record."
        AlertTier.NOTICE -> "A silent notification. One signal outside your usual range."
        AlertTier.ADVISORY -> "A notification with sound. Several signals, or a larger shift, over one day."
        AlertTier.WARNING -> "A loud notification. Several signals, at least one far outside your usual range."
        AlertTier.URGENT -> "The highest tier. The seek-care list below is the part that matters now."
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

    /**
     * Everyday and clinical causes that commonly move a metric in this
     * direction. Listed as possibilities; the owner is the one who knows
     * which applies.
     */
    @Suppress("CyclomaticComplexMethod")
    fun commonCauses(metricKey: String, above: Boolean): List<String> = when (metricKey) {
        "resting_heart_rate", "heart_rate" -> if (above) HR_UP else HR_DOWN
        "heart_rate_variability" -> if (above) HRV_UP else HRV_DOWN
        "skin_temperature_deviation" -> if (above) TEMP_UP else TEMP_DOWN
        "respiratory_rate" -> if (above) RR_UP else listOf(DEVICE_CHANGE)
        "blood_oxygen" -> if (above) listOf(DEVICE_CHANGE) else SPO2_DOWN
        "sleep_duration" -> if (above) SLEEP_UP else SLEEP_DOWN
        "steps", "active_calories" -> if (above) ACTIVITY_UP else ACTIVITY_DOWN
        else -> listOf(DEVICE_CHANGE)
    }

    const val ONE_ALERT_NOTE =
        "Bios compares the last 24 hours with your own previous weeks. Everyday " +
            "events move these numbers too, so one alert is a prompt to look, not a finding."

    val WORTH_WATCHING = listOf(
        "Whether the next night or two bring the value back into your usual range. " +
            "A one-off deviation with a likely everyday cause usually does.",
        "Whether it stays outside your range for two or more days with no likely cause.",
        "Whether symptoms appear: fever, sore throat, cough, aches, unusual tiredness.",
        "If it persists or symptoms appear, rest and a conversation with a healthcare " +
            "provider are the usual next steps.",
    )

    val SEEK_CARE_NOW = listOf(
        "Chest pain, pressure or tightness",
        "Shortness of breath at rest",
        "Fainting or nearly fainting",
        "A racing or irregular heartbeat at rest that does not settle",
        "Sudden confusion, weakness on one side, or trouble speaking",
    )

    /** Heading of the seek-care card: one item is enough, not all of them. */
    const val SEEK_CARE_HEADING = "Seek care now if any one of these applies"

    fun emergencyLine(emergencyNumber: String?, regionName: String? = null): String =
        if (emergencyNumber != null) {
            val where = if (regionName.isNullOrBlank()) "" else " ($regionName)"
            "Any one of them is enough: call $emergencyNumber$where or get urgent care. Do not wait for the data."
        } else {
            "Any one of them is enough: contact your local emergency services or get urgent care. " +
                "Do not wait for the data."
        }

    private const val DEVICE_CHANGE = "A change of device, strap fit, or data source"

    private val HR_UP = listOf(
        "Alcohol the evening before",
        "A short or broken night",
        "A large or late meal",
        "The start of an infection, often a day or two before symptoms",
        "A hard training session in the last day or two",
        "Heat or dehydration",
        "Nicotine, caffeine or other stimulants late in the day",
        "Acute stress",
    )
    private val HR_DOWN = listOf(
        "Training adaptation or a run of restful days",
        "Medications that slow the heart (for example beta blockers)",
        DEVICE_CHANGE,
    )
    private val HRV_DOWN = listOf(
        "Alcohol the evening before",
        "A short or broken night",
        "The start of an infection",
        "A hard training session in the last day or two",
        "Acute stress",
        "A large or late meal",
    )
    private val HRV_UP = listOf("Recovery after rest days", DEVICE_CHANGE)
    private val TEMP_UP = listOf(
        "Fever or the start of an infection",
        "Alcohol the evening before",
        "A warm room or heavy bedding",
        "The luteal phase of the menstrual cycle",
    )
    private val TEMP_DOWN = listOf("A cool room or light bedding", DEVICE_CHANGE)
    private val RR_UP = listOf(
        "A respiratory infection",
        "Alcohol the evening before",
        "Altitude",
        "Heat, stress or anxiety",
    )
    private val SPO2_DOWN = listOf(
        "A loose sensor or an unusual sleeping position",
        "Altitude",
        "A respiratory infection",
        "Breathing pauses during sleep",
    )
    private val SLEEP_DOWN = listOf("A late night or early start", "Alcohol", "Stress", "Illness")
    private val SLEEP_UP = listOf("Catching up after short nights", "Illness")
    private val ACTIVITY_DOWN = listOf("A rest or travel day", "Illness", DEVICE_CHANGE)
    private val ACTIVITY_UP = listOf("A long walk, outing or training day")
}
