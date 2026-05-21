package com.bios.app.ui.components

import com.bios.contracts.MetricType

/**
 * Short, non-prescriptive explanations for the vital metrics surfaced on
 * Read. The voice is the manifesto's: the owner is reading an
 * instrument. We describe what the metric measures and what shifts it.
 * We never say "you should" — evaluation belongs to the owner.
 *
 * Content is intentionally concise (a few sentences each). The
 * Longevity Reference screen is the deeper-read fallback for biomarkers.
 */
data class MetricExplanation(
    val title: String,
    val whatItMeasures: String,
    val whyItVaries: String,
    val howBiosUsesIt: String,
)

object MetricExplanations {

    fun forMetric(metric: MetricType): MetricExplanation? = explanations[metric]

    private val explanations: Map<MetricType, MetricExplanation> = mapOf(
        MetricType.HEART_RATE to MetricExplanation(
            title = "Heart rate",
            whatItMeasures = "Beats per minute — how fast your heart contracts.",
            whyItVaries = "Activity, stress, hydration, illness, temperature, hormones, breathing depth, and even moving from sitting to standing all shift it minute to minute.",
            howBiosUsesIt = "Bios learns your personal range from your own data. A reading is interesting when it deviates from that range — not when it crosses a textbook threshold.",
        ),
        MetricType.HEART_RATE_VARIABILITY to MetricExplanation(
            title = "Heart rate variability",
            whatItMeasures = "The millisecond-level variation between consecutive heartbeats — a window onto how your autonomic nervous system is balancing stress and recovery.",
            whyItVaries = "Lower HRV typically follows physical or emotional stress, illness, alcohol, or poor sleep. Higher HRV tends to follow rest. Night-to-night swings are normal even in healthy people.",
            howBiosUsesIt = "Bios watches multi-day trends against your personal baseline rather than reacting to single nights.",
        ),
        MetricType.BLOOD_OXYGEN to MetricExplanation(
            title = "Blood oxygen (SpO2)",
            whatItMeasures = "The percentage of hemoglobin in your blood carrying oxygen.",
            whyItVaries = "Altitude, illness, sleep-disordered breathing, and rarely cardiopulmonary conditions lower it. Most readings cluster between 95–100% at sea level.",
            howBiosUsesIt = "Wrist optical sensors are noisy by design — Bios pays attention to sustained dips and patterns, not single low samples.",
        ),
        MetricType.RESPIRATORY_RATE to MetricExplanation(
            title = "Respiratory rate",
            whatItMeasures = "Breaths per minute, usually measured at rest or during sleep.",
            whyItVaries = "Rises with fever, infection, exercise, and emotional state; falls with sleep depth. Healthy adult rest range is roughly 12–20 breaths/min.",
            howBiosUsesIt = "A multi-day elevation against your baseline can precede respiratory illness by 24–48 hours in some studies — Bios surfaces the trend, you interpret it.",
        ),
        MetricType.STEPS to MetricExplanation(
            title = "Steps",
            whatItMeasures = "Total steps counted since local midnight.",
            whyItVaries = "Daily movement patterns, exercise, schedule, weather, terrain. The cumulative count resets at midnight.",
            howBiosUsesIt = "Bios shows today's cumulative total. The Trends drill-down shows day-over-day patterns.",
        ),
        MetricType.SKIN_TEMPERATURE_DEVIATION to MetricExplanation(
            title = "Skin temperature deviation",
            whatItMeasures = "Departure in degrees Celsius from your personal skin-temperature baseline, usually measured at the wrist during sleep.",
            whyItVaries = "Hormonal cycles (notably the luteal phase), ambient room temperature, infection, and circadian phase all shift it. Bios reports the deviation, not the absolute temperature.",
            howBiosUsesIt = "Persistent positive deviation can be an early signal of infection or hormonal shift; persistent negative can reflect cooler environments or recovery.",
        ),
        MetricType.SLEEP_DURATION to MetricExplanation(
            title = "Sleep duration",
            whatItMeasures = "Total time asleep, computed from your wearable's sleep stages or phone-based inference when no wearable reported.",
            whyItVaries = "Schedule, stress, caffeine, alcohol, light exposure, and screen use shift it. Adult guidance is 7–9 hours but personal need varies.",
            howBiosUsesIt = "Bios reports what you slept — not what you should sleep. The Sleep dashboard (tap the card) shows regularity, latency, and efficiency separately.",
        ),
    )
}
