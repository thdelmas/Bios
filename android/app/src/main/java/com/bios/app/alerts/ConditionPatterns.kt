package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.app.model.MetricType

data class ConditionPattern(
    val id: String,
    val title: String,
    val category: ConditionCategory,
    val signalRules: List<SignalRule>,
    val minActiveSignals: Int,
    val explanation: String,
    val suggestedAction: String?,
    val references: List<String> = emptyList()
)

data class SignalRule(
    val metricType: MetricType,
    val direction: DeviationDirection,
    val thresholdSigma: Double,
    val minDurationHours: Int,
    val weight: Double
)

enum class DeviationDirection { ABOVE, BELOW, IRREGULAR }

object ConditionPatterns {

    val all by lazy { listOf(infectionOnset, sleepDisruption, cardiovascularStress) }

    /** Infection / illness onset: the Phase 1 primary detection target. */
    val infectionOnset = ConditionPattern(
        id = "infection_onset",
        title = "Possible illness onset detected",
        category = ConditionCategory.INFECTIOUS,
        signalRules = listOf(
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 24, 1.0),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.5, 24, 1.0),
            SignalRule(MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.ABOVE, 1.5, 12, 1.2),
            SignalRule(MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.5, 12, 0.8),
            SignalRule(MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.0, 24, 0.6),
            SignalRule(MetricType.STEPS, DeviationDirection.BELOW, 1.0, 24, 0.4)
        ),
        minActiveSignals = 3,
        explanation = "Multiple vital signs are deviating from your personal baseline in a pattern consistent with the early stages of illness. Research shows these changes can appear 1-2 days before symptoms.",
        suggestedAction = "Consider resting, staying hydrated, and monitoring how you feel. If symptoms develop, consult your healthcare provider.",
        references = listOf(
            "Mishra et al. (2020) - Pre-symptomatic detection of COVID-19 using wearable data",
            "Quer et al. (2021) - Wearable sensor data and self-reported symptoms for COVID-19 detection"
        )
    )

    val sleepDisruption = ConditionPattern(
        id = "sleep_disruption",
        title = "Sleep quality declining",
        category = ConditionCategory.SLEEP,
        signalRules = listOf(
            SignalRule(MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.5, 72, 1.0),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.0, 48, 0.8),
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 48, 0.6)
        ),
        minActiveSignals = 2,
        explanation = "Your sleep quality has been declining for several days. Reduced deep sleep combined with physiological stress markers may indicate accumulated fatigue or stress.",
        suggestedAction = "Try maintaining consistent sleep and wake times, reducing screen time before bed, and managing stress. If this persists for more than a week, consider consulting a healthcare provider."
    )

    val cardiovascularStress = ConditionPattern(
        id = "cardiovascular_stress",
        title = "Elevated cardiovascular strain",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 2.0, 48, 1.0),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 2.0, 48, 1.0),
            SignalRule(MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 1.5, 24, 1.2)
        ),
        minActiveSignals = 2,
        explanation = "Your cardiovascular markers are showing sustained elevation above your personal baseline. This may indicate physical or emotional stress, overtraining, or other factors affecting your heart.",
        suggestedAction = "Consider reducing intense exercise, managing stress, and ensuring adequate rest. If you experience chest pain, dizziness, or shortness of breath, seek medical attention promptly."
    )
}
