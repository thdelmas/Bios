package com.bios.app.alerts

import com.bios.app.model.MetricType

/**
 * Maps a clinical biomarker tracked in longevity research to the wearable-derived
 * proxy metrics that Bios can monitor. Informational only — never prescriptive.
 */
data class BiomarkerReference(
    val id: String,
    val clinicalName: String,
    val description: String,
    val proxyMetrics: List<MetricType>,
    val proxyExplanation: String,
    val normalRange: String,
    val whyItMatters: String,
    val limitations: String,
    val citations: List<String>
)

object BiomarkerReferences {

    val all by lazy {
        listOf(
            inflammation, metabolicHealth, cardiorespFitness,
            arterialHealth, stressLoad, bodyComposition, sleepArchitecture
        )
    }

    fun forMetric(metricType: MetricType): List<BiomarkerReference> =
        all.filter { metricType in it.proxyMetrics }

    val inflammation = BiomarkerReference(
        id = "hscrp_inflammation",
        clinicalName = "hsCRP (High-Sensitivity C-Reactive Protein)",
        description = "A blood marker of systemic inflammation. Elevated hsCRP is associated with increased cardiovascular risk, infection, and chronic disease progression.",
        proxyMetrics = listOf(
            MetricType.RESTING_HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY
        ),
        proxyExplanation = "Sustained resting heart rate elevation combined with HRV depression correlates with systemic inflammatory states. Wearable data cannot measure hsCRP directly, but multi-day trends in these two metrics have been shown to track inflammatory episodes.",
        normalRange = "Clinical hsCRP: <1.0 mg/L (low risk), 1.0–3.0 mg/L (moderate), >3.0 mg/L (high risk). Wearable proxy: resting HR and HRV within 1σ of personal baseline.",
        whyItMatters = "Chronic low-grade inflammation accelerates aging and is implicated in cardiovascular disease, type 2 diabetes, neurodegeneration, and cancer. Catching inflammatory episodes early — even via proxy signals — allows earlier investigation.",
        limitations = "Resting HR and HRV respond to many stimuli beyond inflammation: exercise, stress, alcohol, sleep debt. A sustained multi-day deviation is more meaningful than a single reading. A blood test is required for definitive hsCRP measurement.",
        citations = listOf(
            "Ridker PM (2003) - C-reactive protein: a simple test to help predict risk of heart attack and stroke",
            "Furman D et al. (2019) - Chronic inflammation in the etiology of disease across the life span",
            "Mishra T et al. (2020) - Pre-symptomatic detection of COVID-19 from smartwatch data"
        )
    )

    val metabolicHealth = BiomarkerReference(
        id = "hba1c_metabolic",
        clinicalName = "HbA1c (Glycated Hemoglobin)",
        description = "A 3-month average of blood glucose levels. Elevated HbA1c indicates pre-diabetes or diabetes risk.",
        proxyMetrics = listOf(
            MetricType.BLOOD_GLUCOSE,
            MetricType.SLEEP_STAGE,
            MetricType.RESTING_HEART_RATE
        ),
        proxyExplanation = "Continuous glucose monitor (CGM) data captures glucose variability, which correlates with HbA1c trends. Sleep disruption and elevated resting HR are secondary indicators of metabolic dysregulation. Together these signals approximate metabolic health status.",
        normalRange = "Clinical HbA1c: <5.7% (normal), 5.7–6.4% (pre-diabetic), ≥6.5% (diabetic). Wearable proxy: glucose variability within personal baseline, sleep quality stable, resting HR stable.",
        whyItMatters = "Metabolic health underpins nearly every organ system. Early detection of glucose dysregulation — before symptoms appear — enables lifestyle adjustments that can prevent or reverse pre-diabetes.",
        limitations = "CGM data reflects real-time glucose, not the 3-month average that HbA1c represents. Sleep and HR are indirect proxies. Without a CGM connected, Bios can only detect metabolic stress through secondary signals. A blood HbA1c test is required for diagnosis.",
        citations = listOf(
            "Hall H et al. (2018) - Glucotypes reveal new patterns of glucose dysregulation",
            "Zeevi D et al. (2015) - Personalized nutrition by prediction of glycemic responses"
        )
    )

    val cardiorespFitness = BiomarkerReference(
        id = "vo2max_fitness",
        clinicalName = "VO2 Max (Maximal Oxygen Uptake)",
        description = "The gold-standard measure of cardiorespiratory fitness. One of the strongest predictors of all-cause mortality.",
        proxyMetrics = listOf(
            MetricType.RESTING_HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY,
            MetricType.ACTIVE_MINUTES
        ),
        proxyExplanation = "Many wearables estimate VO2 max from heart rate during exercise. Even without a direct estimate, the combination of resting HR trend (lower is fitter), HRV (higher is fitter), and sustained activity minutes reflects cardiorespiratory fitness trajectory.",
        normalRange = "Clinical VO2 max varies by age and sex. General: >40 mL/kg/min (good for adults). Wearable proxy: declining resting HR trend, rising HRV trend, consistent activity minutes.",
        whyItMatters = "A 1 MET (3.5 mL/kg/min) increase in fitness is associated with a 13% reduction in all-cause mortality. VO2 max decline with age is modifiable through exercise — tracking the trend helps the owner see whether fitness is improving, stable, or declining.",
        limitations = "Wearable VO2 max estimates are approximations based on HR-pace algorithms and vary significantly between devices. Resting HR and HRV are influenced by many factors beyond fitness. A lab-based cardiopulmonary exercise test is the definitive measurement.",
        citations = listOf(
            "Ross R et al. (2016) - Importance of assessing cardiorespiratory fitness in clinical practice",
            "Kodama S et al. (2009) - Cardiorespiratory fitness as a quantitative predictor of all-cause mortality",
            "Mandsager K et al. (2018) - Association of cardiorespiratory fitness with long-term mortality"
        )
    )

    val arterialHealth = BiomarkerReference(
        id = "arterial_stiffness",
        clinicalName = "Arterial Stiffness / Pulse Wave Velocity",
        description = "A measure of how rigid the arterial walls have become. Arterial stiffness increases with age and is an independent predictor of cardiovascular events.",
        proxyMetrics = listOf(
            MetricType.BLOOD_PRESSURE_SYSTOLIC,
            MetricType.BLOOD_PRESSURE_DIASTOLIC,
            MetricType.RESTING_HEART_RATE
        ),
        proxyExplanation = "Blood pressure readings from connected devices (Withings BPM, etc.) track arterial load. Widening pulse pressure (systolic minus diastolic) over time can indicate increasing stiffness. Resting HR provides context for cardiac workload.",
        normalRange = "Clinical: pulse wave velocity <10 m/s (age-dependent). Wearable proxy: systolic <120 mmHg, diastolic <80 mmHg, pulse pressure <40 mmHg, all within personal baseline.",
        whyItMatters = "Arterial stiffness is a leading indicator of hypertension, stroke, and heart failure. It progresses silently for years. Tracking blood pressure trends at home catches the drift before a clinical visit would.",
        limitations = "Consumer blood pressure monitors have measurement variability. Bios does not measure pulse wave velocity directly — only tracks BP trends as a proxy. Requires a connected BP device (not available from wrist wearables alone). Clinical assessment via pulse wave analysis is the gold standard.",
        citations = listOf(
            "Vlachopoulos C et al. (2010) - Prediction of cardiovascular events with arterial stiffness",
            "Williams B et al. (2018) - ESC/ESH guidelines for management of arterial hypertension"
        )
    )

    val stressLoad = BiomarkerReference(
        id = "cortisol_stress",
        clinicalName = "Cortisol / Chronic Stress Load",
        description = "Cortisol is the primary stress hormone. Chronically elevated cortisol suppresses immune function, impairs sleep, and accelerates biological aging.",
        proxyMetrics = listOf(
            MetricType.HEART_RATE_VARIABILITY,
            MetricType.SLEEP_STAGE,
            MetricType.RESTING_HEART_RATE
        ),
        proxyExplanation = "HRV is the strongest wearable proxy for autonomic stress load — sustained low HRV indicates sympathetic dominance consistent with elevated cortisol. Sleep quality degradation (reduced deep sleep, increased awakenings) and resting HR elevation provide corroborating signals.",
        normalRange = "Clinical cortisol: 6–18 μg/dL (morning). Wearable proxy: HRV within 1σ of baseline, sleep architecture normal, resting HR stable.",
        whyItMatters = "Chronic stress is a root driver of inflammation, metabolic dysfunction, and immune suppression. Unlike acute stress (which is healthy), sustained stress load accumulates silently. Wearable proxies can reveal the pattern weeks before burnout or illness.",
        limitations = "HRV responds to fitness, hydration, alcohol, and illness — not just psychological stress. Sleep disruption has many causes. No wearable measures cortisol directly. A saliva or blood cortisol test provides definitive measurement.",
        citations = listOf(
            "McEwen BS (2008) - Central effects of stress hormones in health and disease",
            "Kim HG et al. (2018) - Stress and heart rate variability: a meta-analysis"
        )
    )

    val bodyComposition = BiomarkerReference(
        id = "body_composition",
        clinicalName = "Body Composition (Visceral Fat / Lean Mass)",
        description = "The ratio of fat to lean tissue, and particularly visceral fat distribution, predicts metabolic disease risk more accurately than BMI alone.",
        proxyMetrics = listOf(
            MetricType.STEPS,
            MetricType.ACTIVE_CALORIES,
            MetricType.ACTIVE_MINUTES
        ),
        proxyExplanation = "Bios cannot measure body composition directly from wrist wearables. However, sustained declines in activity metrics (steps, active calories, active minutes) correlate with body composition changes over months. Connected scales (Withings) can provide direct body fat and lean mass readings.",
        normalRange = "Clinical: varies by age and sex. General healthy ranges — men: 10–20% body fat, women: 18–28% body fat. Visceral fat: <100 cm² on CT. Wearable proxy: activity metrics stable or improving.",
        whyItMatters = "Visceral fat is metabolically active and produces inflammatory cytokines. Excess visceral fat independently predicts type 2 diabetes, cardiovascular disease, and certain cancers, even at a normal BMI.",
        limitations = "Activity metrics are a very indirect proxy for body composition. Step counts and calories burned do not account for diet, which is the primary driver of fat gain or loss. DEXA scanning is the clinical gold standard for body composition analysis.",
        citations = listOf(
            "Neeland IJ et al. (2019) - Visceral and ectopic fat, atherosclerosis, and cardiometabolic disease",
            "Ross R et al. (2020) - Waist circumference as a vital sign in clinical practice"
        )
    )

    val sleepArchitecture = BiomarkerReference(
        id = "sleep_architecture",
        clinicalName = "Sleep Architecture Quality",
        description = "The structure of sleep — time in deep, REM, and light stages — reflects brain health, immune function, and recovery capacity.",
        proxyMetrics = listOf(
            MetricType.SLEEP_STAGE,
            MetricType.SLEEP_DURATION,
            MetricType.HEART_RATE_VARIABILITY
        ),
        proxyExplanation = "Wearables with sleep staging (Oura, Whoop, Galaxy Watch) directly measure sleep architecture. HRV during sleep correlates with autonomic recovery. Duration alone is insufficient — architecture determines whether sleep is restorative.",
        normalRange = "Clinical: 7–9 hours total, 13–23% deep sleep, 20–25% REM, sleep latency <20 min. Wearable data aligns well with these ranges when device is worn consistently.",
        whyItMatters = "Deep sleep drives physical recovery, immune function, and growth hormone release. REM sleep supports memory consolidation and emotional regulation. Disrupted architecture — even at normal duration — impairs immune defense and accelerates cognitive decline.",
        limitations = "Consumer wearable sleep staging is less accurate than clinical polysomnography, particularly for distinguishing light from deep sleep. Single-night variation is high; trends over weeks are more meaningful. Environmental factors (noise, temperature, partner) affect architecture independently of health status.",
        citations = listOf(
            "Walker M (2017) - Why We Sleep: Unlocking the Power of Sleep and Dreams",
            "Prather AA et al. (2015) - Behaviorally assessed sleep and susceptibility to the common cold",
            "Smarr BL et al. (2020) - Feasibility of continuous fever monitoring using wearable devices"
        )
    )
}
