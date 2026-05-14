package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Cross-correlation patterns over the keys companion apps inject — Phase 7.3 and
 * the substance-use portion of Phase 7.7. Kept in a separate file so the original
 * `ConditionPatterns.kt` stays under the 500-line limit and so the companion-derived
 * detection surface is grouped where it can be audited.
 *
 * Every pattern combines a companion-emitted event stream (fall, near-miss, check-in
 * miss, tobacco use, craving) with the physiological signals Bios already baselines
 * — so the pattern only fires when both the event rate AND the underlying vitals
 * are shifting in a literature-aligned direction. Stand-alone event spikes are not
 * sufficient (per the "silence is a feature" rule).
 *
 * All text obeys the Phase 3.6 content policy: data statements, never lifestyle
 * judgments, never gamification.
 *
 * Event-driven rules use `required = true` so a vital-sign drift alone — without
 * a fall, miss, or substance event in the window — never triggers a companion
 * pattern. The cessation-recovery pattern uses [DeviationDirection.ABSENT] to
 * gate on a sustained absence of TOBACCO_USE events before reading the positive
 * cardiovascular trends as recovery (per §7.7).
 */
object CompanionConditionPatterns {

    /** Falls clustering with low blood pressure — proxy for orthostatic hypotension. */
    val fallOrthostatic = ConditionPattern(
        id = "fall_orthostatic_pattern",
        title = "Fall pattern with low blood pressure detected",
        category = ConditionCategory.SAFETY,
        signalRules = listOf(
            SignalRule(MetricType.FALL_EVENT, DeviationDirection.ABOVE, 1.5, 168, 1.5,
                ThresholdSource.COMPANION,
                "Virgil FALL_EVENT — verified-fall rate above baseline over a 7-day window",
                required = true),
            SignalRule(MetricType.BLOOD_PRESSURE_SYSTOLIC, DeviationDirection.BELOW, 1.0, 72, 1.0,
                ThresholdSource.LITERATURE,
                "Rutan et al. (1992) — sustained low standing systolic BP is the dominant antecedent of orthostatic falls"),
            SignalRule(MetricType.BLOOD_PRESSURE_DIASTOLIC, DeviationDirection.BELOW, 1.0, 72, 0.7,
                ThresholdSource.LITERATURE,
                "Ricci et al. (2015) — orthostatic hypotension is bivariate: systolic + diastolic drop"),
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 72, 0.6,
                ThresholdSource.LITERATURE,
                "Tachycardic compensation often accompanies orthostatic intolerance"),
        ),
        minActiveSignals = 2,
        explanation = "Verified falls have clustered above baseline while systolic and diastolic blood pressure have trended below baseline. This combination is the clinical fingerprint of orthostatic intolerance — research links it to medication, dehydration, autonomic conditions, and age-related changes.",
        suggestedAction = "Discuss the recent fall pattern and blood pressure trend with a healthcare provider. Bring the date and time of each fall along with the BP record from Bios.",
        references = listOf(
            "Rutan GH et al. (1992) — Orthostatic hypotension in older adults: the Cardiovascular Health Study",
            "Ricci F et al. (2015) — Orthostatic hypotension: epidemiology, prognosis, and treatment",
        ),
    )

    /** Falls plus depressed HRV — autonomic / neurological involvement signal. */
    val fallNeurological = ConditionPattern(
        id = "fall_neurological_pattern",
        title = "Fall pattern with autonomic depression detected",
        category = ConditionCategory.SAFETY,
        signalRules = listOf(
            SignalRule(MetricType.FALL_EVENT, DeviationDirection.ABOVE, 1.5, 168, 1.5,
                ThresholdSource.COMPANION,
                "Virgil FALL_EVENT rate above baseline",
                required = true),
            SignalRule(MetricType.NEAR_MISS_FALL, DeviationDirection.ABOVE, 1.0, 168, 1.0,
                ThresholdSource.COMPANION,
                "Near-miss rate is an earlier predictor than verified falls"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.5, 168, 1.2,
                ThresholdSource.LITERATURE,
                "Sleimen-Malkoun et al. (2014) — depressed HRV correlates with gait variability and neurodegenerative trajectory"),
        ),
        minActiveSignals = 2,
        explanation = "Verified-fall and near-miss rates have risen above baseline while heart rate variability has stayed depressed. Literature links this combination to autonomic and neurological involvement — the gait-instability signature of neurodegenerative or recovery conditions.",
        suggestedAction = "A healthcare provider can investigate whether the autonomic and gait signals share a common cause. Recent fall events, near-miss counts, and HRV trend from Bios are useful to share.",
        references = listOf(
            "Sleimen-Malkoun R et al. (2014) — Aging-related changes in motor variability and the autonomic nervous system",
        ),
    )

    /** Falls correlating with low blood glucose — hypoglycemia-driven syncope. */
    val fallHypoglycemia = ConditionPattern(
        id = "fall_hypoglycemia_pattern",
        title = "Fall pattern with low blood glucose detected",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(MetricType.FALL_EVENT, DeviationDirection.ABOVE, 1.5, 168, 1.5,
                ThresholdSource.COMPANION,
                "Virgil FALL_EVENT rate above baseline",
                required = true),
            SignalRule(MetricType.BLOOD_GLUCOSE, DeviationDirection.BELOW, 1.5, 72, 1.5,
                ThresholdSource.LITERATURE,
                "Yale & Begg (2002) — sustained low blood glucose is a leading reversible cause of falls and syncope in insulin-treated adults"),
        ),
        minActiveSignals = 2,
        explanation = "Verified falls have clustered above baseline alongside blood glucose readings sustained below personal baseline. Hypoglycemia is one of the most consistently reversible causes of falls when present.",
        suggestedAction = "Bring the recent CGM trace and fall log to a healthcare provider — medication or meal-timing adjustments may resolve the pattern.",
        references = listOf(
            "Yale JF & Begg I (2002) — Hypoglycemia as a contributor to syncope and falls",
        ),
    )

    /** Recurring check-in misses plus disrupted sleep and elevated mood drift. */
    val checkInDecline = ConditionPattern(
        id = "check_in_decline_pattern",
        title = "Recurring missed check-ins with sleep and mood drift detected",
        category = ConditionCategory.SAFETY,
        signalRules = listOf(
            SignalRule(MetricType.CHECK_IN_MISS, DeviationDirection.ABOVE, 1.5, 168, 1.5,
                ThresholdSource.COMPANION,
                "Virgil CHECK_IN_MISS rate above baseline over the past 7 days",
                required = true),
            SignalRule(MetricType.SLEEP_DURATION, DeviationDirection.IRREGULAR, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Riemann et al. (2020) — sleep duration irregularity correlates with cognitive decline and depressive load"),
            SignalRule(MetricType.MOOD_DRIFT_SCORE, DeviationDirection.ABOVE, 1.0, 168, 1.2,
                ThresholdSource.COMPANION,
                "W2F mood drift composite — sustained elevation is a depressive-load correlate"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Koch et al. (2019) — depressed HRV correlates with depression severity and cognitive effort"),
        ),
        minActiveSignals = 2,
        explanation = "Check-in responses have been missed above baseline while sleep is irregular and mood-drift composite is elevated. Literature links this combination to cognitive load, depressive episodes, and the early indicators of cognitive decline. This is a data observation, not a diagnosis.",
        suggestedAction = "Discuss the pattern with a healthcare provider or trusted contact. Sharing the check-in record alongside sleep and mood data can help identify a reversible cause.",
        references = listOf(
            "Riemann D et al. (2020) — Sleep, insomnia and the risk of dementia",
            "Koch C et al. (2019) — Heart rate variability and mental health",
        ),
    )

    /** Tobacco use clustered with cardiovascular load — RHR / HRV / SpO2 signals. */
    val substanceUseCardiovascularLoad = ConditionPattern(
        id = "substance_use_cv_load_pattern",
        title = "Substance-use clustering with cardiovascular load detected",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(MetricType.TOBACCO_USE, DeviationDirection.ABOVE, 1.0, 72, 1.2,
                ThresholdSource.COMPANION,
                "Smokeless TOBACCO_USE — event rate above 3-day baseline",
                required = true),
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 72, 1.0,
                ThresholdSource.LITERATURE,
                "Benowitz NL (2009) — sustained tobacco use elevates resting heart rate via nicotinic stimulation"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.0, 72, 1.0,
                ThresholdSource.LITERATURE,
                "Mahmud A & Feely J (2003) — HRV depression is the autonomic correlate of active tobacco use"),
            SignalRule(MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 1.0, 72, 0.7,
                ThresholdSource.LITERATURE,
                "Macnee W (2005) — SpO2 dips with sustained tobacco load"),
        ),
        minActiveSignals = 2,
        explanation = "Tobacco-use events have run above your 3-day baseline while resting heart rate, heart rate variability, and blood oxygen have all drifted in the directions literature associates with cardiovascular load from active use. This is a data observation about your physiology, not a judgment about your choices.",
        suggestedAction = "If you're trying to reduce or quit, sharing this physiological signature with a healthcare provider can help target medication or behavioral support to where it has the most measurable effect.",
        references = listOf(
            "Benowitz NL (2009) — Pharmacology of nicotine: addiction, smoking-induced disease, and therapeutics",
            "Mahmud A & Feely J (2003) — Effect of smoking on arterial stiffness and pulse pressure amplification",
            "Macnee W (2005) — Pathogenesis of chronic obstructive pulmonary disease",
        ),
    )

    /** Cravings clustering with sleep debt — the best-documented craving amplifier. */
    val cravingSleepDebt = ConditionPattern(
        id = "craving_sleep_debt_pattern",
        title = "Craving frequency with sleep debt detected",
        category = ConditionCategory.SLEEP,
        signalRules = listOf(
            SignalRule(MetricType.TOBACCO_CRAVING, DeviationDirection.ABOVE, 1.5, 72, 1.3,
                ThresholdSource.COMPANION,
                "Smokeless TOBACCO_CRAVING — event rate above 3-day baseline",
                required = true),
            SignalRule(MetricType.SLEEP_DURATION, DeviationDirection.BELOW, 1.0, 72, 1.0,
                ThresholdSource.LITERATURE,
                "Jaehne et al. (2012) — short sleep amplifies next-day craving frequency and intensity"),
            SignalRule(MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.0, 72, 0.8,
                ThresholdSource.LITERATURE,
                "Hamidovic & de Wit (2009) — sleep efficiency is the strongest single sleep correlate of craving"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.0, 72, 0.7,
                ThresholdSource.LITERATURE,
                "Eddie et al. (2015) — depressed HRV correlates with autonomic stress that drives craving"),
        ),
        minActiveSignals = 2,
        explanation = "Craving events have run above baseline during the same window in which sleep duration and sleep efficiency have dropped below baseline. Sleep debt is the best-documented craving amplifier in the cessation literature — restoring sleep often reduces craving load before any other intervention.",
        suggestedAction = "Restoring a regular sleep window can reduce craving frequency. Sharing this craving + sleep record with a cessation program or healthcare provider can target support precisely.",
        references = listOf(
            "Jaehne A et al. (2012) — Effects of nicotine on sleep during consumption, withdrawal and replacement",
            "Hamidovic A & de Wit H (2009) — Sleep deprivation increases cigarette smoking",
            "Eddie D et al. (2015) — Heart rate variability biofeedback for substance use disorder",
        ),
    )

    /**
     * Sustained tobacco-use absence (>72h) paired with the literature-backed
     * positive cardiovascular trajectory (RHR ↓, HRV ↑, SpO2 ↑). Information-only,
     * no praise, no streaks, no encouragement language — respects the
     * "silence is a feature" and "never evaluate the person" principles.
     */
    val cessationRecovery = ConditionPattern(
        id = "cessation_recovery_pattern",
        title = "Cardiovascular markers shifting in cessation-recovery direction",
        category = ConditionCategory.RECOVERY,
        signalRules = listOf(
            SignalRule(MetricType.TOBACCO_USE, DeviationDirection.ABSENT, 1.0, 72, 1.5,
                ThresholdSource.COMPANION,
                "No TOBACCO_USE events recorded in the past 72 hours — gating condition",
                required = true),
            SignalRule(MetricType.RESTING_HEART_RATE, DeviationDirection.BELOW, 1.0, 72, 1.0,
                ThresholdSource.LITERATURE,
                "Benowitz NL (2009) — RHR decline within days of cessation as nicotinic stimulation clears"),
            SignalRule(MetricType.HEART_RATE_VARIABILITY, DeviationDirection.ABOVE, 1.0, 72, 1.0,
                ThresholdSource.LITERATURE,
                "Mahmud A & Feely J (2003) — HRV recovery within 2–12 weeks of cessation"),
            SignalRule(MetricType.BLOOD_OXYGEN, DeviationDirection.ABOVE, 0.5, 72, 0.7,
                ThresholdSource.LITERATURE,
                "Macnee W (2005) — SpO2 normalization following sustained cessation"),
        ),
        minActiveSignals = 2,
        explanation = "There have been no tobacco-use events logged for at least 72 hours, and during the same window resting heart rate has trended below baseline while heart rate variability and blood oxygen have trended above baseline. Literature describes this combination as the early cardiovascular recovery signature following cessation. This is a data observation about your physiology — surfaced once, with no follow-up reminders or progress tracking.",
        suggestedAction = "If you're working with a cessation program, sharing this physiological trace can help them calibrate support to where it has measurable effect.",
        references = listOf(
            "Benowitz NL (2009) — Pharmacology of nicotine: addiction, smoking-induced disease, and therapeutics",
            "Mahmud A & Feely J (2003) — Effect of smoking on arterial stiffness and pulse pressure amplification",
            "Macnee W (2005) — Pathogenesis of chronic obstructive pulmonary disease",
        ),
    )

    val all: List<ConditionPattern> = listOf(
        fallOrthostatic,
        fallNeurological,
        fallHypoglycemia,
        checkInDecline,
        substanceUseCardiovascularLoad,
        cravingSleepDebt,
        cessationRecovery,
    )
}
