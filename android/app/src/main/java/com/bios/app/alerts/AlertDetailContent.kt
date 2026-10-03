package com.bios.app.alerts

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

    /** Detection window: the detector averages the 24 h before it runs. */
    const val WINDOW_MILLIS = 24L * 3600 * 1000

    fun signal(metricKey: String, value: Double, baseline: PersonalBaseline) = MeasuredSignal(
        metricKey = metricKey,
        value = value,
        usualLow = baseline.p5,
        usualHigh = baseline.p95,
        zScore = baseline.zScore(value),
    )

    fun label(metricKey: String): String = when (metricKey) {
        "heart_rate_variability" -> "heart rate variability (HRV)"
        "skin_temperature_deviation" -> "skin temperature"
        "blood_oxygen" -> "blood oxygen"
        else -> metricKey.replace("_", " ")
    }

    fun unit(metricKey: String): String = MetricType.fromKey(metricKey)?.unit?.symbol.orEmpty()

    fun formatValue(metricKey: String, value: Double): String {
        val decimals = when (metricKey) {
            "skin_temperature_deviation" -> 2
            "respiratory_rate" -> 1
            else -> 0
        }
        val number = String.format(Locale.getDefault(), "%.${decimals}f", value)
        val unit = unit(metricKey)
        return if (unit.isEmpty()) number else "$number $unit"
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

    fun emergencyLine(emergencyNumber: String?): String =
        if (emergencyNumber != null) {
            "With any of these, call $emergencyNumber or get urgent care. Do not wait for the data."
        } else {
            "With any of these, contact your local emergency services or get urgent care. " +
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
