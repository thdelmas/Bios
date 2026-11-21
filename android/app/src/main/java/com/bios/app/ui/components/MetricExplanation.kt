package com.bios.app.ui.components

import com.bios.contracts.MetricType

/**
 * Short descriptions of the metrics surfaced on Read: what the metric is
 * and how it is measured. A description never says what a change in the
 * metric may indicate.
 */
data class MetricExplanation(
    val title: String,
    val whatItIs: String,
    val howItIsMeasured: String,
)

object MetricExplanations {

    fun forMetric(metric: MetricType): MetricExplanation? = explanations[metric]

    private val explanations: Map<MetricType, MetricExplanation> = mapOf(
        MetricType.HEART_RATE to MetricExplanation(
            title = "Heart rate",
            whatItIs = "Beats per minute: how often your heart contracts.",
            howItIsMeasured = "Your wearable counts beats with an optical sensor on the wrist, or with electrodes " +
                "on a chest strap. Bios stores each reading with its source and shows it next to your own usual range.",
        ),
        MetricType.HEART_RATE_VARIABILITY to MetricExplanation(
            title = "Heart rate variability",
            whatItIs = "The variation, in milliseconds, between consecutive heartbeats.",
            howItIsMeasured = "Computed by your wearable from beat-to-beat intervals, usually during sleep or at " +
                "rest, or by Bios from a one-minute camera capture. Devices use different formulas, so values from " +
                "different sources are not directly comparable.",
        ),
        MetricType.BLOOD_OXYGEN to MetricExplanation(
            title = "Blood oxygen (SpO2)",
            whatItIs = "The percentage of hemoglobin in your blood that is carrying oxygen.",
            howItIsMeasured = "Estimated by an optical sensor from the light absorbed at two wavelengths, on the " +
                "wrist or a fingertip. Wrist readings are noisy from one sample to the next.",
        ),
        MetricType.RESPIRATORY_RATE to MetricExplanation(
            title = "Respiratory rate",
            whatItIs = "Breaths per minute.",
            howItIsMeasured = "Derived by your wearable, usually during sleep, from the breathing rhythm visible " +
                "in heartbeat intervals or in movement.",
        ),
        MetricType.STEPS to MetricExplanation(
            title = "Steps",
            whatItIs = "Total steps counted since local midnight.",
            howItIsMeasured = "Counted by the motion sensor of your phone or wearable. The count resets at " +
                "midnight; Trends shows the day totals.",
        ),
        MetricType.SKIN_TEMPERATURE_DEVIATION to MetricExplanation(
            title = "Skin temperature deviation",
            whatItIs = "The difference, in degrees Celsius, between your skin temperature and your own usual " +
                "skin temperature.",
            howItIsMeasured = "Measured by a temperature sensor on the wearable, usually at the wrist during " +
                "sleep. The value is the difference, not the absolute temperature.",
        ),
        MetricType.SLEEP_DURATION to MetricExplanation(
            title = "Sleep duration",
            whatItIs = "Total time asleep in a night.",
            howItIsMeasured = "Taken from your wearable's sleep stages, or estimated from the phone when no " +
                "wearable reported. The Sleep dashboard shows regularity, latency and efficiency separately.",
        ),
        MetricType.HEART_RATE_BEFORE_SLEEP to MetricExplanation(
            title = "Heart rate before sleep",
            whatItIs = "Median beats per minute in the quarter-hour you spent lying quietly before falling asleep.",
            howItIsMeasured = "Bios computes it from your continuous heart-rate stream. You can also enter it " +
                "yourself by counting your pulse for 15 seconds and multiplying by four.",
        ),
    )
}
