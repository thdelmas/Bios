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
    val references: List<String> = emptyList(),
    val earlyDetection: String = "",
    val prevention: String = "",
    val healing: String = "",
    val risks: String = ""
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

    val all by lazy { listOf(infectionOnset, sleepDisruption, cardiovascularStress, overtraining) }

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
        ),
        earlyDetection = "Wearable sensors can detect infection 1-2 days before symptoms appear. The earliest signs are a rise in resting heart rate, a drop in heart rate variability, and elevated skin temperature. These changes reflect your immune system activating. Respiratory rate often increases next, followed by reduced activity and disrupted sleep as fatigue sets in. Bios monitors all six signals simultaneously — a single elevated metric is usually normal variation, but three or more shifting together is a strong early warning.",
        prevention = "Strengthen your immune defenses with consistent sleep (7-9 hours), regular moderate exercise, and a balanced diet rich in vitamins C and D. Wash hands frequently and avoid touching your face. During high-risk seasons, consider reducing exposure to crowded indoor spaces. Manage chronic stress, as prolonged cortisol elevation suppresses immune function. Stay up to date on vaccinations relevant to your age and health profile.",
        healing = "Rest is the most important intervention in the early stages. Increase fluid intake, prioritize sleep, and reduce physical and mental exertion. Over-the-counter remedies can manage symptoms: acetaminophen or ibuprofen for fever and aches, saline spray for congestion. Monitor your temperature and oxygen saturation if available. Seek medical attention if you develop a high fever (>39.4C / 103F), difficulty breathing, persistent chest pain, or symptoms that worsen after initial improvement. Most viral infections resolve within 7-10 days with supportive care.",
        risks = "Ignoring early infection signs can lead to prolonged illness and secondary complications. Viral infections left unchecked may progress to bronchitis, sinusitis, or pneumonia. Continuing intense physical activity while infected increases the risk of myocarditis (heart inflammation), which can have lasting cardiac consequences. Untreated bacterial infections may spread systemically. The immune system works best when supported early — delaying rest and treatment often extends recovery time significantly."
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
        suggestedAction = "Try maintaining consistent sleep and wake times, reducing screen time before bed, and managing stress. If this persists for more than a week, consider consulting a healthcare provider.",
        earlyDetection = "Sleep disruption often manifests gradually. The first measurable sign is a reduction in deep and REM sleep stages, even when total sleep duration appears normal. Heart rate variability drops during sleep, and resting heart rate drifts upward — both indicators that your autonomic nervous system is not fully recovering overnight. Bios detects these shifts over a 48-72 hour window, catching the pattern before you consciously feel sleep-deprived.",
        prevention = "Maintain a consistent sleep schedule, even on weekends. Keep your bedroom cool (18-20C), dark, and quiet. Avoid caffeine after early afternoon and limit alcohol, which fragments sleep architecture. Reduce blue light exposure 1-2 hours before bed. Regular physical activity improves sleep quality, but avoid intense exercise within 3 hours of bedtime. Establish a wind-down routine — reading, stretching, or breathing exercises signal your body to prepare for rest.",
        healing = "If your sleep has already deteriorated, start by resetting your schedule: go to bed and wake up at fixed times for at least one week. Avoid napping longer than 20 minutes. Consider relaxation techniques such as progressive muscle relaxation, guided meditation, or 4-7-8 breathing. If stress is a contributor, journaling or cognitive behavioral strategies can help. Magnesium and melatonin supplements may assist short-term (consult your doctor). If poor sleep persists beyond two weeks, seek evaluation for underlying causes such as sleep apnea or anxiety disorders.",
        risks = "Chronic sleep disruption affects nearly every body system. Short-term, it impairs cognitive function, reaction time, mood regulation, and immune response. Over weeks and months, sustained poor sleep increases the risk of obesity, type 2 diabetes, cardiovascular disease, and depression. Sleep deprivation raises cortisol levels, promotes systemic inflammation, and impairs memory consolidation. Performance, relationships, and overall quality of life decline progressively the longer poor sleep goes unaddressed."
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
        suggestedAction = "Consider reducing intense exercise, managing stress, and ensuring adequate rest. If you experience chest pain, dizziness, or shortness of breath, seek medical attention promptly.",
        earlyDetection = "Cardiovascular stress builds over days before it becomes symptomatic. Resting heart rate rising 2+ standard deviations above your baseline is the primary signal, especially when sustained over 48 hours. A simultaneous drop in HRV indicates your autonomic nervous system is under sustained load. Declining blood oxygen saturation, even mildly, adds a third corroborating signal. Bios requires at least two of these three markers to flag cardiovascular strain, reducing false positives from exercise or transient stress.",
        prevention = "Regular aerobic exercise strengthens the cardiovascular system — aim for 150 minutes of moderate or 75 minutes of vigorous activity per week. Manage chronic stress through mindfulness, therapy, or lifestyle adjustments, as sustained cortisol elevates resting heart rate and blood pressure. Limit sodium intake, avoid smoking, and moderate alcohol consumption. Monitor blood pressure periodically. Maintain a healthy weight and get adequate sleep — both directly affect cardiovascular load.",
        healing = "Reduce physical and emotional stressors immediately. Prioritize rest and sleep. Practice breathing exercises or meditation to activate the parasympathetic nervous system and lower heart rate. If stress is work-related, consider taking breaks or adjusting workload. Stay hydrated and avoid stimulants (caffeine, nicotine). If resting heart rate remains elevated for more than a week, or if you experience chest pain, palpitations, dizziness, or shortness of breath, consult a cardiologist. An ECG or stress test may be warranted to rule out underlying conditions.",
        risks = "Sustained cardiovascular strain left unaddressed can lead to serious consequences. Chronically elevated resting heart rate is an independent risk factor for heart attack and stroke. Persistent autonomic imbalance (low HRV, high resting HR) is associated with hypertension, arrhythmias, and heart failure over time. Reduced blood oxygen may indicate respiratory or circulatory issues that worsen without intervention. Acute risks include exercise-induced cardiac events if intense training continues despite warning signs."
    )

    val overtraining = ConditionPattern(
        id = "overtraining",
        title = "Possible overtraining detected",
        category = ConditionCategory.RECOVERY,
        signalRules = listOf(
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 48, 1.0),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.5, 48, 1.2),
            SignalRule(MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.0, 48, 0.8),
            SignalRule(MetricType.ACTIVE_CALORIES, DeviationDirection.ABOVE, 1.5, 72, 0.6),
            SignalRule(MetricType.STEPS, DeviationDirection.BELOW, 1.0, 48, 0.4)
        ),
        minActiveSignals = 3,
        explanation = "Your body is showing signs of insufficient recovery from recent physical activity. Elevated resting heart rate combined with reduced HRV and poor sleep quality can indicate overtraining syndrome.",
        suggestedAction = "Consider taking 1-2 rest days, prioritizing sleep, and reducing training intensity. If fatigue persists beyond a few days of rest, consult a sports medicine professional.",
        references = listOf(
            "Meeusen et al. (2013) - Prevention, diagnosis, and treatment of the overtraining syndrome",
            "Plews et al. (2013) - Training adaptation and HRV in elite endurance athletes"
        ),
        earlyDetection = "Overtraining develops when training load consistently exceeds recovery capacity. The earliest wearable-detectable sign is a persistent drop in HRV despite adequate sleep — your body cannot fully restore parasympathetic tone overnight. Resting heart rate creeps up, sleep quality declines even though you feel exhausted, and daily activity (steps) drops as fatigue accumulates. Paradoxically, calorie burn may remain high from recent intense sessions. Bios looks for this combination over 48-72 hours, distinguishing normal training fatigue from the overreaching threshold.",
        prevention = "Follow the 10% rule: increase weekly training volume by no more than 10%. Schedule at least one full rest day per week and one recovery week per month. Periodize training with cycles of building and recovery. Prioritize sleep (8+ hours for athletes) and nutrition — adequate protein and carbohydrate intake supports recovery. Monitor your morning HRV trend; a declining trend over several days signals that you should back off. Listen to subjective cues: persistent muscle soreness, irritability, and loss of motivation are early warnings.",
        healing = "Reduce training volume and intensity by 50-75% for at least one week. Focus on low-intensity active recovery: walking, gentle yoga, or swimming. Prioritize 8-9 hours of sleep. Increase caloric intake, especially carbohydrates and protein, to support tissue repair. Address any nutritional deficiencies (iron, vitamin D, B12 are common in athletes). If symptoms persist beyond two weeks of reduced training, consult a sports medicine professional — blood work may reveal hormonal imbalances (cortisol, testosterone) or iron deficiency that require targeted treatment.",
        risks = "Overtraining syndrome (OTS) can take weeks to months to fully recover from if left unchecked. Continued high-intensity training during overreaching leads to chronic performance decline, hormonal disruption (elevated cortisol, suppressed testosterone), immune suppression with frequent illness, and increased injury risk from impaired coordination and weakened tissues. Psychological effects include chronic fatigue, depression, insomnia, and loss of motivation. In severe cases, athletes require 3-6 months of structured recovery before returning to prior training levels."
    )
}
