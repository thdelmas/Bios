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
    val weight: Double,
    val source: ThresholdSource = ThresholdSource.ENGINEERING,
    val citation: String = ""
)

enum class DeviationDirection { ABOVE, BELOW, IRREGULAR }

enum class ThresholdSource {
    /** Set by engineering judgment — not yet validated against literature. */
    ENGINEERING,
    /** Derived from peer-reviewed research with specific citation. */
    LITERATURE,
    /** Learned from the owner's own historical data (future). */
    PERSONAL
}

object ConditionPatterns {

    val all by lazy {
        listOf(
            infectionOnset, sleepDisruption, cardiovascularStress, overtraining,
            metabolicDrift, cardiorespiratoryDeconditioning, chronicInflammation, recoveryDeficit
        )
    }

    /** Infection / illness onset: the Phase 1 primary detection target. */
    val infectionOnset = ConditionPattern(
        id = "infection_onset",
        title = "Possible illness onset detected",
        category = ConditionCategory.INFECTIOUS,
        signalRules = listOf(
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 24, 1.0,
                ThresholdSource.LITERATURE, "Mishra et al. (2020) - resting HR +10% sustained >24h in pre-symptomatic infection"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.5, 24, 1.0,
                ThresholdSource.LITERATURE, "Quer et al. (2021) - HRV depression precedes symptom onset by 1-2 days"),
            SignalRule(MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.ABOVE, 1.5, 12, 1.2,
                ThresholdSource.LITERATURE, "Smarr et al. (2020) - +0.5°C skin temp deviation detects febrile illness early"),
            SignalRule(MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.5, 12, 0.8,
                ThresholdSource.LITERATURE, "Quer et al. (2021) - elevated respiratory rate in early infection"),
            SignalRule(MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.0, 24, 0.6,
                ThresholdSource.ENGINEERING),
            SignalRule(MetricType.STEPS, DeviationDirection.BELOW, 1.0, 24, 0.4,
                ThresholdSource.ENGINEERING)
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
            SignalRule(MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.5, 72, 1.0,
                ThresholdSource.LITERATURE, "Prather et al. (2015) - sleep efficiency <85% for >5 nights correlates with immune suppression"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.0, 48, 0.8,
                ThresholdSource.LITERATURE, "Kim et al. (2018) - sustained low HRV reflects autonomic stress during poor sleep"),
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 48, 0.6,
                ThresholdSource.ENGINEERING)
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
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 2.0, 48, 1.0,
                ThresholdSource.LITERATURE, "Booth et al. (2012) - +15% resting HR sustained >7d indicates cardiovascular deconditioning"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 2.0, 48, 1.0,
                ThresholdSource.LITERATURE, "Plews et al. (2013) - HRV depression >2σ for 48h+ indicates significant autonomic stress"),
            SignalRule(MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 1.5, 24, 1.2,
                ThresholdSource.LITERATURE, "Clinical consensus - SpO2 <95% at rest is clinically significant, <92% is urgent")
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
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 48, 1.0,
                ThresholdSource.LITERATURE, "Meeusen et al. (2013) - resting HR elevation is early marker of overreaching"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.5, 48, 1.2,
                ThresholdSource.LITERATURE, "Plews et al. (2013) - HRV decline >1.5σ over 48h distinguishes overreaching from normal fatigue"),
            SignalRule(MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.0, 48, 0.8,
                ThresholdSource.LITERATURE, "Saw et al. (2016) - sleep quality decline accompanies overtraining"),
            SignalRule(MetricType.ACTIVE_CALORIES, DeviationDirection.ABOVE, 1.5, 72, 0.6,
                ThresholdSource.ENGINEERING),
            SignalRule(MetricType.STEPS, DeviationDirection.BELOW, 1.0, 48, 0.4,
                ThresholdSource.ENGINEERING)
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

    // --- Phase 3B: Longevity-science-informed patterns ---

    /** Metabolic drift: glucose dysregulation + sleep/HR changes suggest pre-diabetic shift. */
    val metabolicDrift = ConditionPattern(
        id = "metabolic_drift",
        title = "Metabolic pattern shift detected",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(MetricType.BLOOD_GLUCOSE, DeviationDirection.IRREGULAR, 1.5, 168, 1.2,
                ThresholdSource.LITERATURE, "Hall et al. (2018) - glucose variability increase identifies pre-diabetic glucotypes"),
            SignalRule(MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE, "Reutrakul & Van Cauter (2018) - sleep disruption impairs glucose regulation"),
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 168, 0.6,
                ThresholdSource.LITERATURE, "Aune et al. (2017) - elevated resting HR associated with metabolic syndrome")
        ),
        minActiveSignals = 2,
        explanation = "Glucose variability has increased while sleep quality or resting heart rate have shifted from your baseline over the past week — a pattern associated with metabolic stress in clinical literature.",
        suggestedAction = "Review recent dietary patterns and sleep habits. If glucose variability persists, discuss HbA1c testing with your healthcare provider.",
        references = listOf(
            "Hall H et al. (2018) - Glucotypes reveal new patterns of glucose dysregulation",
            "Zeevi D et al. (2015) - Personalized nutrition by prediction of glycemic responses",
            "Reutrakul S & Van Cauter E (2018) - Sleep influences on obesity, insulin resistance, and type 2 diabetes"
        ),
        earlyDetection = "Metabolic drift develops gradually over weeks to months. The earliest wearable signal is increasing glucose variability — wider post-meal spikes and slower returns to baseline, visible on CGM. Concurrently, sleep quality often declines (less deep sleep, more awakenings) and resting heart rate drifts upward. These changes can precede a clinical HbA1c increase by months. Bios monitors this triad over a 7-day window, looking for the convergence of signals rather than any single metric in isolation.",
        prevention = "Maintain consistent meal timing and composition. Prioritize fiber, protein, and healthy fats to moderate glycemic spikes. Regular physical activity — even daily walking — significantly improves insulin sensitivity. Prioritize 7-9 hours of quality sleep, as even one night of poor sleep measurably worsens glucose tolerance. Limit refined carbohydrates and added sugars. Manage chronic stress, which elevates cortisol and impairs glucose regulation.",
        healing = "If glucose variability is already elevated, start with meal composition changes: add protein or fat to carbohydrate-heavy meals to flatten the spike. Time meals earlier in the day when insulin sensitivity is highest. Increase daily movement — a 15-minute walk after meals reduces post-prandial glucose significantly. Restore sleep hygiene: consistent bedtime, cool dark room, no screens before bed. If variability persists despite lifestyle changes, consult your doctor about fasting glucose or oral glucose tolerance testing.",
        risks = "Sustained glucose dysregulation is the pathway to type 2 diabetes, which develops over years before diagnosis. Pre-diabetic glucose patterns damage blood vessels, increase cardiovascular risk, promote visceral fat accumulation, and impair cognitive function. Insulin resistance also accelerates biological aging and increases risk of Alzheimer's disease. Early intervention — when glucose patterns are shifting but HbA1c is still normal — has the highest success rate for reversal."
    )

    /** Cardiorespiratory deconditioning: fitness markers declining over weeks. */
    val cardiorespiratoryDeconditioning = ConditionPattern(
        id = "cardiorespiratory_deconditioning",
        title = "Cardiorespiratory fitness declining",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 336, 1.0,
                ThresholdSource.LITERATURE, "Booth et al. (2012) - resting HR increase over 2+ weeks reflects deconditioning"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.0, 336, 1.0,
                ThresholdSource.LITERATURE, "Buchheit (2014) - HRV decline tracks fitness loss during detraining"),
            SignalRule(MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.5, 336, 0.8,
                ThresholdSource.LITERATURE, "Ross et al. (2016) - activity decline precedes measurable VO2 max loss")
        ),
        minActiveSignals = 2,
        explanation = "Your resting heart rate and HRV have shifted over the past two weeks in a direction consistent with cardiorespiratory fitness decline, alongside reduced activity levels.",
        suggestedAction = "Gradual return to regular aerobic activity can reverse deconditioning. Even moderate daily movement (brisk walking, cycling) improves cardiovascular fitness within weeks.",
        references = listOf(
            "Ross R et al. (2016) - Importance of assessing cardiorespiratory fitness in clinical practice",
            "Kodama S et al. (2009) - Cardiorespiratory fitness as a quantitative predictor of all-cause mortality",
            "Mandsager K et al. (2018) - Association of cardiorespiratory fitness with long-term mortality"
        ),
        earlyDetection = "Cardiorespiratory deconditioning starts within days of reduced activity but takes weeks to become measurable in resting vitals. Resting heart rate gradually increases as the heart becomes less efficient. HRV declines as autonomic fitness weakens. Activity minutes drop — the cause of the deconditioning. Bios uses a 14-day window to distinguish normal variation from a genuine downward trend. VO2 max, the clinical gold standard, drops measurably after 2-4 weeks of inactivity; the wearable proxy signals detect the trajectory earlier.",
        prevention = "Maintain at least 150 minutes of moderate or 75 minutes of vigorous aerobic activity per week, as recommended by the WHO. Include both sustained cardio (running, cycling, swimming) and everyday movement (walking, stairs). Avoid prolonged sedentary periods — stand or walk for 5 minutes every hour. Consistency matters more than intensity; three 30-minute sessions per week maintain baseline fitness. Even during illness or injury, gentle movement (if safe) slows deconditioning.",
        healing = "If fitness has already declined, rebuild gradually. Start with daily walks at a pace that feels moderate. Increase duration before intensity. The 10% rule applies to reconditioning: increase weekly volume by no more than 10%. Expect resting HR to begin improving within 1-2 weeks of resumed activity. Full reconditioning to prior fitness levels typically takes 2-3 times longer than the period of inactivity. If deconditioning occurred due to illness, get medical clearance before resuming vigorous exercise.",
        risks = "Cardiorespiratory fitness is one of the strongest predictors of all-cause mortality — stronger than smoking, diabetes, or hypertension. Each 1-MET decline in fitness increases mortality risk by approximately 13%. Prolonged deconditioning leads to reduced cardiac output, increased blood pressure, insulin resistance, and muscle atrophy. In older adults, deconditioning accelerates frailty and fall risk. The cardiovascular system deconditions faster than it reconditions — prevention is far easier than recovery."
    )

    /** Chronic inflammation: sustained low-grade inflammatory signal over 2+ weeks. */
    val chronicInflammation = ConditionPattern(
        id = "chronic_inflammation",
        title = "Sustained inflammatory signal detected",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 336, 1.0,
                ThresholdSource.LITERATURE, "Furman et al. (2019) - chronic resting HR elevation reflects systemic inflammation"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.0, 336, 1.0,
                ThresholdSource.LITERATURE, "Furman et al. (2019) - autonomic imbalance accompanies chronic inflammation"),
            SignalRule(MetricType.SLEEP_DURATION, DeviationDirection.BELOW, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE, "Irwin (2019) - sleep loss promotes inflammatory gene expression"),
            SignalRule(MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.ABOVE, 0.5, 168, 0.6,
                ThresholdSource.LITERATURE, "Smarr et al. (2020) - mild sustained skin temp elevation accompanies low-grade inflammation")
        ),
        minActiveSignals = 3,
        explanation = "Multiple vital signs have been mildly but persistently shifted from your baseline over the past 1-2 weeks. This multi-signal pattern is consistent with sustained low-grade inflammation in clinical literature.",
        suggestedAction = "Persistent mild deviations across multiple signals may warrant investigation. Consider discussing an hsCRP blood test with your healthcare provider.",
        references = listOf(
            "Furman D et al. (2019) - Chronic inflammation in the etiology of disease across the life span",
            "Ridker PM (2003) - C-reactive protein: a simple test to help predict risk of heart attack and stroke",
            "Irwin MR (2019) - Sleep and inflammation: partners in sickness and in health"
        ),
        earlyDetection = "Unlike acute infection (which produces dramatic, short-lived deviations), chronic inflammation manifests as a persistent, mild shift across multiple signals. Resting HR stays slightly elevated — not alarmingly so, but consistently above personal baseline. HRV is mildly suppressed. Sleep duration or quality drifts downward. Skin temperature may show a subtle sustained elevation. No single signal is concerning alone, but the convergence over 1-2 weeks distinguishes chronic inflammation from day-to-day noise. Bios requires 3 of 4 signals active to flag this pattern, minimizing false positives.",
        prevention = "Anti-inflammatory lifestyle factors include regular moderate exercise, adequate sleep (7-9 hours), a diet rich in omega-3 fatty acids, vegetables, and whole grains, stress management, and maintaining a healthy weight. Limit processed foods, excess sugar, alcohol, and tobacco — all of which promote systemic inflammation. Regular social connection and mental health care also reduce inflammatory markers. Periodic hsCRP blood tests provide a clinical baseline to complement wearable monitoring.",
        healing = "If inflammatory signals are already elevated, prioritize sleep restoration — it is the single most impactful intervention for reducing inflammatory gene expression. Increase anti-inflammatory foods: fatty fish, leafy greens, berries, nuts, olive oil. Reduce refined carbohydrates and processed foods. Add regular moderate exercise (30 minutes daily walking or equivalent). Consider stress reduction practices: meditation, time in nature, or social connection. If signals persist beyond 3-4 weeks despite lifestyle changes, an hsCRP blood test can quantify the inflammation and guide clinical decisions.",
        risks = "Chronic low-grade inflammation — sometimes called 'inflammaging' — is now recognized as a root driver of most age-related diseases. It accelerates atherosclerosis, promotes insulin resistance, damages neuronal tissue (increasing Alzheimer's risk), and creates a tumor-permissive microenvironment. Unlike acute inflammation (which heals), chronic inflammation compounds over months and years. Early detection via wearable proxy signals — when hsCRP is mildly elevated but the owner feels fine — is the window where lifestyle intervention is most effective."
    )

    /** Recovery deficit: HRV not recovering post-exercise over multiple weeks. */
    val recoveryDeficit = ConditionPattern(
        id = "recovery_deficit",
        title = "Recovery capacity diminished",
        category = ConditionCategory.RECOVERY,
        signalRules = listOf(
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.0, 336, 1.2,
                ThresholdSource.LITERATURE, "Stanley et al. (2015) - HRV recovery kinetics slow as recovery debt accumulates"),
            SignalRule(MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE, "Halson (2014) - sleep quality decline is both cause and consequence of poor recovery"),
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 0.8, 336, 0.8,
                ThresholdSource.LITERATURE, "Buchheit (2014) - resting HR drift reflects accumulated recovery debt")
        ),
        minActiveSignals = 2,
        explanation = "Your HRV has been below baseline for an extended period while sleep quality has declined and resting heart rate has drifted upward — a pattern consistent with accumulated recovery debt.",
        suggestedAction = "Focus on sleep quality and consider reducing overall physical and mental demands. If fatigue persists beyond 2 weeks of prioritized rest, consult your healthcare provider.",
        references = listOf(
            "Stanley J et al. (2015) - Cardiac parasympathetic reactivation following exercise",
            "Saw AE et al. (2016) - Monitoring the athlete training response",
            "Halson SL (2014) - Sleep in elite athletes and nutritional interventions"
        ),
        earlyDetection = "Recovery deficit differs from acute overtraining in timescale and subtlety. Where overtraining produces dramatic 48-72h deviations after heavy exercise, recovery deficit accumulates over weeks — HRV never quite returns to baseline between sessions, resting HR drifts upward gradually, and sleep quality erodes. The pattern is quiet enough to miss day-to-day but clear over a 14-day window. Bios detects this slower trajectory by using longer evaluation windows (14 days) and lower thresholds than the overtraining pattern.",
        prevention = "Build recovery into your routine proactively, not just when you feel tired. Ensure 7-9 hours of sleep (8+ if physically active). Alternate hard training days with easy days or rest. Manage total life stress — work, relationships, and training stress all draw from the same recovery bank. Adequate nutrition (especially protein and micronutrients), hydration, and social connection support systemic recovery. Monitor your HRV trend weekly; a flattening or declining trend is an early signal to reduce load.",
        healing = "Recovery deficit requires patience. Reduce all discretionary physical stress by 50% for at least 2 weeks. Prioritize sleep above all else — aim for 9 hours in bed. Gentle movement (walking, light stretching) is better than complete rest, which can worsen mood and deconditioning. Increase caloric intake slightly to support repair. Address any ongoing stressors: work deadlines, sleep environment, or relationship strain. If HRV and resting HR do not begin improving within 2 weeks, consult your doctor — persistent recovery failure can indicate underlying conditions (thyroid dysfunction, iron deficiency, depression).",
        risks = "Accumulated recovery debt weakens the immune system, impairs cognitive function, increases injury risk, and degrades mood. Over months, it can lead to burnout, chronic fatigue syndrome, or clinical depression. The cardiovascular system suffers from sustained sympathetic dominance (low HRV, elevated HR). Athletic performance plateaus or declines despite continued training. The longer recovery debt accumulates, the longer the recovery period — weeks of deficit may require months of careful restoration."
    )

    // --- Phase 4: Expanded condition patterns ---

    /** Respiratory infection: distinct from general infection onset, focuses on respiratory signals. */
    val respiratoryInfection = ConditionPattern(
        id = "respiratory_infection",
        title = "Respiratory pattern shift detected",
        category = ConditionCategory.RESPIRATORY,
        signalRules = listOf(
            SignalRule(MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.5, 24, 1.2,
                ThresholdSource.LITERATURE, "Cretikos et al. (2008) - respiratory rate elevation is earliest sign of respiratory compromise"),
            SignalRule(MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 1.0, 12, 1.2,
                ThresholdSource.LITERATURE, "Clinical consensus - SpO2 decline even within normal range is significant when sustained"),
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 24, 0.8,
                ThresholdSource.LITERATURE, "Mishra et al. (2020) - compensatory HR elevation accompanies respiratory infection"),
            SignalRule(MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.ABOVE, 1.0, 12, 0.6,
                ThresholdSource.LITERATURE, "Smarr et al. (2020) - temperature elevation correlates with respiratory illness")
        ),
        minActiveSignals = 2,
        explanation = "Your respiratory rate and blood oxygen are shifting from baseline in a pattern associated with respiratory infection. These changes can appear before cough or congestion symptoms.",
        suggestedAction = "Monitor for developing respiratory symptoms (cough, congestion, shortness of breath). If SpO2 drops below 95% or breathing difficulty develops, contact your healthcare provider.",
        references = listOf(
            "Cretikos MA et al. (2008) - Respiratory rate: the neglected vital sign",
            "Quer G et al. (2021) - Wearable sensor data for COVID-19 detection"
        ),
        earlyDetection = "Respiratory infections produce a specific signal pattern distinguishable from general illness. Respiratory rate elevates first — often 12-24 hours before symptoms. SpO2 may dip slightly, even remaining technically 'normal' (96-97% vs usual 98-99%). Heart rate compensates upward to maintain oxygen delivery. Temperature rises follow. Bios distinguishes respiratory infection from general infection onset by weighting respiratory signals higher and requiring fewer total signals to activate.",
        prevention = "Respiratory infections spread primarily through airborne droplets and surface contact. Hand washing, avoiding touching the face, and good ventilation reduce transmission. During high-risk seasons, N95/FFP2 masks in crowded indoor spaces provide significant protection. Adequate sleep, moderate exercise, and stress management support mucosal immune defenses — the first barrier against respiratory pathogens. Stay current on influenza and COVID-19 vaccinations.",
        healing = "Rest and hydration are foundational. Saline nasal irrigation reduces congestion. Honey (for adults) soothes cough. Monitor SpO2 if available — sustained readings below 95% warrant medical evaluation. Avoid cough suppressants that prevent productive clearing of mucus. Seek medical attention for high fever (>39°C), difficulty breathing, chest pain, or symptoms that worsen after 5-7 days (may indicate secondary bacterial infection requiring antibiotics). Most viral respiratory infections resolve within 7-14 days.",
        risks = "Untreated respiratory infections can progress to pneumonia, bronchitis, or sinusitis. In vulnerable populations, even common respiratory viruses can cause severe illness. Continuing intense exercise during active respiratory infection increases the risk of myocarditis and prolonged recovery. 'Silent hypoxia' — significant oxygen desaturation without subjective breathlessness — was identified as a dangerous feature of COVID-19 and can occur with other respiratory infections. Early detection enables earlier rest and medical intervention."
    )

    /** Atrial fibrillation screening: HRV irregularity patterns. */
    val atrialFibrillationScreen = ConditionPattern(
        id = "afib_screen",
        title = "Heart rhythm irregularity detected",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.IRREGULAR, 2.0, 12, 1.5,
                ThresholdSource.LITERATURE, "Perez et al. (2019) - irregular pulse notification from Apple Watch PPG correlated with AFib"),
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 12, 1.0,
                ThresholdSource.LITERATURE, "January et al. (2014) - elevated resting HR accompanies paroxysmal AFib episodes"),
            SignalRule(MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 1.0, 12, 0.6,
                ThresholdSource.ENGINEERING)
        ),
        minActiveSignals = 2,
        explanation = "Your heart rate variability pattern shows unusual irregularity combined with elevated resting heart rate. This pattern can be associated with atrial fibrillation episodes, though many other causes are possible.",
        suggestedAction = "This is not a diagnosis. If you experience palpitations, dizziness, or unexplained fatigue, discuss ECG screening with your healthcare provider. Wearable HRV data cannot definitively detect AFib — clinical ECG is required.",
        references = listOf(
            "Perez MV et al. (2019) - Large-scale assessment of a smartwatch to identify atrial fibrillation (Apple Heart Study)",
            "January CT et al. (2014) - AHA/ACC/HRS guideline for management of atrial fibrillation"
        ),
        earlyDetection = "Atrial fibrillation (AFib) is the most common cardiac arrhythmia, affecting 2-3% of adults. Many episodes are paroxysmal — they come and go — and are missed by periodic clinical ECGs. Wearable optical HR sensors can detect irregular pulse patterns consistent with AFib. The Apple Heart Study (2019) demonstrated that PPG-based notifications had a 34% positive predictive value for AFib on follow-up ECG — meaningful for screening but not diagnostic. Bios flags HRV irregularity patterns as screening signals, always with the explicit caveat that clinical confirmation is required.",
        prevention = "Modifiable AFib risk factors include hypertension (the strongest modifiable risk), obesity, excessive alcohol consumption, sleep apnea, and intense endurance exercise. Managing blood pressure, maintaining healthy weight, moderating alcohol, and treating sleep apnea significantly reduce AFib risk. Regular moderate exercise is protective. Stress management and adequate sleep support stable heart rhythm.",
        healing = "AFib management requires medical supervision. If AFib is confirmed by ECG, treatment options include rate control (beta-blockers, calcium channel blockers), rhythm control (antiarrhythmics, cardioversion, ablation), and anticoagulation to reduce stroke risk. Lifestyle modifications (weight loss, alcohol reduction, sleep apnea treatment) reduce AFib burden significantly. The owner's wearable data can help clinicians assess episode frequency and duration — export data for your cardiologist.",
        risks = "Undetected AFib increases stroke risk 5-fold. Many strokes are the first sign of previously undiagnosed AFib. AFib also contributes to heart failure, cognitive decline, and reduced quality of life. Paroxysmal AFib can progress to persistent or permanent AFib if underlying causes are not addressed. Early detection and treatment significantly reduce these risks — screening with wearable data, while imperfect, catches episodes that periodic clinical visits miss."
    )

    /** Mental health correlates: sleep, HRV, activity pattern changes. Reports data only — never diagnoses. */
    val mentalHealthCorrelate = ConditionPattern(
        id = "mental_health_correlate",
        title = "Behavioral pattern shift detected",
        category = ConditionCategory.MENTAL_HEALTH,
        signalRules = listOf(
            SignalRule(MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.5, 168, 1.0,
                ThresholdSource.LITERATURE, "Baglioni et al. (2016) - sleep architecture changes precede and accompany depressive episodes"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE, "Koch et al. (2019) - HRV reduction correlates with anxiety and depression severity"),
            SignalRule(MetricType.STEPS, DeviationDirection.BELOW, 1.5, 168, 0.8,
                ThresholdSource.LITERATURE, "Schuch et al. (2018) - activity reduction is both symptom and risk factor for depression"),
            SignalRule(MetricType.SLEEP_DURATION, DeviationDirection.IRREGULAR, 1.5, 168, 0.6,
                ThresholdSource.LITERATURE, "Wirz-Justice (2006) - circadian rhythm disruption correlates with mood disorders")
        ),
        minActiveSignals = 3,
        explanation = "Your sleep patterns, activity level, and autonomic markers have shifted over the past week. These physiological changes are correlated with stress, fatigue, or mood changes in clinical literature. This is a data observation, not a diagnosis.",
        suggestedAction = "Consider whether recent life changes, stress, or seasonal factors may be affecting your wellbeing. If you're experiencing persistent low mood, anxiety, or loss of interest, speaking with a mental health professional can help.",
        references = listOf(
            "Baglioni C et al. (2016) - Sleep and mental disorders: meta-analysis",
            "Koch C et al. (2019) - Heart rate variability and mental health",
            "Schuch FB et al. (2018) - Physical activity and incident depression"
        ),
        earlyDetection = "Mental health changes produce a distinctive physiological fingerprint: sleep architecture deteriorates (less deep sleep, more awakenings, irregular timing), HRV declines (reflecting autonomic stress), and daily activity decreases. These changes often precede conscious awareness of mood shifts by days to weeks. Bios monitors this triad over a 7-day window, requiring 3 of 4 signals to activate — a high bar that reduces false positives from short-term stress or illness. This pattern is strictly reported as data, never as a mental health assessment.",
        prevention = "Protective factors for mental health include regular physical activity (the strongest modifiable factor, per Schuch 2018), consistent sleep schedule, social connection, time in nature, stress management practices, and limiting alcohol. Building these into routine creates resilience against acute stressors. If you have a history of mood disorders, maintaining these foundations is especially important — and monitoring for physiological pattern shifts can provide early warning.",
        healing = "If a pattern shift is detected, the most evidence-supported immediate actions are: restore sleep consistency (fixed bedtime and wake time), increase daily movement (even a 20-minute walk has measurable effects), and connect with someone you trust. If the pattern persists beyond 2 weeks, or if you're experiencing hopelessness, persistent anxiety, or loss of interest in activities, professional support — therapy, counseling, or medical evaluation — is recommended. Physiological data from Bios can be shared with your provider to provide objective context.",
        risks = "Untreated mental health changes tend to deepen over time. Sustained sleep disruption and autonomic stress (low HRV) impair immune function, increase cardiovascular risk, and worsen cognitive performance. Social withdrawal and activity reduction create a feedback loop that accelerates decline. Early intervention — when physiological signals are shifting but before clinical symptoms fully manifest — has the highest success rate. If you are in crisis, contact a crisis helpline or emergency services immediately."
    )

    /** Menstrual cycle anomalies: BBT pattern deviation, cycle irregularity. Stored in isolated reproductive DB. */
    val menstrualCycleAnomaly = ConditionPattern(
        id = "menstrual_cycle_anomaly",
        title = "Cycle pattern deviation detected",
        category = ConditionCategory.WOMENS_HEALTH,
        signalRules = listOf(
            SignalRule(MetricType.BASAL_BODY_TEMPERATURE, DeviationDirection.IRREGULAR, 1.5, 168, 1.2,
                ThresholdSource.LITERATURE, "Shilaih et al. (2018) - BBT pattern deviation detects anovulatory cycles and luteal phase defects"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.IRREGULAR, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE, "Schmalenberger et al. (2019) - HRV fluctuations track menstrual cycle phases"),
            SignalRule(MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.0, 168, 0.6,
                ThresholdSource.LITERATURE, "Baker & Driver (2007) - sleep architecture varies across menstrual cycle phases")
        ),
        minActiveSignals = 2,
        explanation = "Your basal body temperature and associated physiological markers are showing a pattern that deviates from your established cycle. Cycle-to-cycle variation is normal, but persistent changes may warrant attention.",
        suggestedAction = "Track any associated symptoms (pain, flow changes, mood changes) in your health journal. If cycle irregularities persist across multiple cycles, discuss with your gynecologist.",
        references = listOf(
            "Shilaih M et al. (2018) - Modern fertility awareness methods: wrist wearables capture BBT changes",
            "Schmalenberger KM et al. (2019) - HRV across the menstrual cycle"
        ),
        earlyDetection = "The menstrual cycle produces predictable BBT patterns: lower temperatures in the follicular phase, a post-ovulation rise, and a drop before menstruation. Deviations from this established pattern — absent temperature rise (possible anovulation), shortened luteal phase, or irregular cycle length — are detectable via wearable BBT monitoring. HRV also fluctuates predictably across the cycle; deviations from the owner's personal pattern add confidence. Bios learns the owner's individual cycle pattern over 3+ cycles before flagging deviations.",
        prevention = "Menstrual cycle regularity is supported by consistent sleep, regular moderate exercise, adequate nutrition (especially iron and B vitamins), stress management, and maintaining a healthy weight. Extreme caloric restriction, excessive exercise, and chronic stress are common causes of cycle disruption. Tracking your cycle pattern over time helps establish what is normal for you — individual variation is wide, and population averages are less useful than personal baselines.",
        healing = "If a cycle deviation is detected, note any associated symptoms and continue tracking for 2-3 cycles to distinguish a one-time variation from a persistent change. Common causes of temporary irregularity include stress, travel, illness, medication changes, and weight fluctuation. Persistent changes (>3 cycles) warrant gynecological evaluation — possibilities include thyroid dysfunction, PCOS, premature ovarian insufficiency, or structural causes. Your tracked BBT and cycle data can be exported and shared with your provider.",
        risks = "Menstrual cycle irregularities can indicate underlying hormonal or metabolic conditions. Anovulatory cycles (no ovulation) affect fertility and may indicate PCOS or thyroid dysfunction. Shortened luteal phases can impair implantation. Irregular cycles are also associated with higher cardiovascular risk and lower bone density long-term. Early detection of pattern changes — when they're subtle shifts rather than dramatic disruptions — allows earlier investigation and treatment."
    )
}
