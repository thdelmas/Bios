package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Advanced cardiology patterns (#216 / CARDIOLOGY_POV §2.3+§2.5+§2.6,
 * Triage Inventory #26). Home for the four sub-patterns the audit
 * grouped together — POTS, HRR, ectopy burden, and QT class. HRR shipped
 * in the first PR and ectopy burden ships here. POTS needs accelerometer
 * posture detection and QT needs ECG; each remains deferred.
 *
 * HRR pattern uses [SignalRule.absoluteWindowHours] with a 3-reading
 * minimum over 30 days so a single bad session doesn't fire — Cole 1999
 * used multi-test averaging.
 *
 * Ectopy-burden pattern fires at the Niwano 2009 10 % threshold across a
 * 7-day window with ≥ 3 PPG captures. The shorter window matches the
 * higher cadence of PPG sessions (HRR is gated by exercise sessions; PPG
 * captures can happen daily) and reflects the clinical convention of
 * Holter-burden averaging over days, not weeks.
 *
 * ## References
 *
 * - Cole CR et al. (1999) — Heart-rate recovery immediately after exercise
 *   as a predictor of mortality. NEJM 341(18):1351-1357.
 * - Imai K et al. (1994) — Vagally mediated heart rate recovery after
 *   exercise. JACC 24(6):1529-1535.
 * - Jouven X et al. (2005) — Heart-rate profile during exercise as a
 *   predictor of sudden death. NEJM 352(19):1951-1958.
 * - Baman TS et al. (2010) — Relationship between burden of premature
 *   ventricular complexes and left ventricular function. Heart Rhythm
 *   7(7):865-869.
 * - Niwano S et al. (2009) — Prognostic significance of frequent PVCs in
 *   patients with normal LV function. Heart 95(15):1230-1237.
 *
 * All text obeys [AlertContentPolicy].
 */
object AdvancedCardiologyPatterns {

    val all by lazy { listOf(hrRecoveryImpaired, ectopyBurdenElevated) }

    /**
     * Sustained HRR1 below the Cole 1999 cutoff (12 bpm) across multiple
     * exercise sessions. ADVISORY tier — chronic prognostic signal, not
     * an acute medical event.
     */
    val hrRecoveryImpaired = ConditionPattern(
        id = "advanced_cardiology_hr_recovery_impaired",
        title = "Heart-rate recovery below clinical cutoff",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.HR_RECOVERY_1MIN, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Cole 1999 NEJM 341:1351 — HRR1 ≤ 12 bpm at 1 min post-exercise identified 4-fold all-cause mortality risk over 6 years; median across recent sessions captures the chronic prognostic signal Cole's multi-test averaging targeted",
                required = true,
                absoluteBelow = 12.0,
                absoluteWindowHours = 720,
                absoluteMinReadings = 3,
            ),
        ),
        minActiveSignals = 1,
        explanation = "The median heart-rate-recovery value at 1 minute post-exercise across recent sessions is at or below 12 bpm. Cole 1999 (NEJM) showed this threshold identifies a four-fold increase in 6-year all-cause mortality independent of fitness, beta-blocker use, and standard cardiovascular risk factors. A blunted HRR reflects delayed vagal reactivation — the autonomic-recovery signal that distinguishes a healthy post-exercise rebound from cardiac, autonomic, or deconditioning states.",
        suggestedAction = "Discuss the trend with a clinician at the next visit. The HRR cutoff is a prognostic marker, not an acute medical event — a formal cardiopulmonary exercise test is the gold-standard follow-up when the trend persists. Confounders worth ruling out before clinical concern: very-low-intensity sessions where peak HR didn't actually reach an effort threshold (HRR loses meaning), recent illness, dehydration, sleep debt.",
        references = listOf(
            "Cole CR et al. (1999) — Heart-rate recovery immediately after exercise as a predictor of mortality. NEJM 341(18):1351-1357",
            "Imai K et al. (1994) — Vagally mediated heart rate recovery after exercise is accelerated in athletes but blunted in patients with chronic heart failure. JACC 24(6):1529-1535",
            "Jouven X et al. (2005) — Heart-rate profile during exercise as a predictor of sudden death. NEJM 352(19):1951-1958",
        ),
        earlyDetection = "HRR is a continuous wearable-derivable surrogate for autonomic-recovery health. Cole's multi-test averaging is mimicked by the median-of-three-sessions gate (`absoluteMinReadings = 3` across a 30-day window) so a single sub-threshold session — possibly explained by illness, dehydration, or a low-effort workout — does not fire the pattern.",
    )

    /**
     * Sustained ECTOPY_BURDEN_ESTIMATE above the Niwano 2009 10 % cutoff
     * across ≥ 3 PPG captures in the last 7 days. ADVISORY tier — high
     * PVC burden is the trigger for outpatient Holter / 12-lead workup,
     * not an acute event.
     */
    val ectopyBurdenElevated = ConditionPattern(
        id = "advanced_cardiology_ectopy_burden_elevated",
        title = "Ventricular ectopy burden above clinical screening threshold",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.ECTOPY_BURDEN_ESTIMATE, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Niwano 2009 Heart 95:1230 / Baman 2010 Heart Rhythm 7:865 — PVC burden ≥ 10 % is the screening threshold for PVC-induced cardiomyopathy concern and is the conventional trigger for outpatient Holter / 12-lead workup; median across recent PPG sessions filters single-session motion artefacts",
                required = true,
                absoluteAbove = 10.0,
                absoluteWindowHours = 168,
                absoluteMinReadings = 3,
            ),
        ),
        minActiveSignals = 1,
        explanation = "The median ventricular-ectopy-burden estimate across recent PPG captures is at or above 10 %. Niwano 2009 (Heart) and Baman 2010 (Heart Rhythm) identify this threshold as the screening anchor for PVC-induced cardiomyopathy concern. Bios's PPG-derived estimate looks for short-RR-then-compensatory-pause pairs in the inter-beat-interval series — the same signature automated Holter algorithms key off — but the PPG cannot distinguish PVCs from atrial premature complexes with concealed conduction or from non-respiratory sinus arrhythmia, and motion artefacts can produce false short-long pairs.",
        suggestedAction = "Discuss the trend with a clinician — outpatient 24-hour Holter or 12-lead ECG is the standard confirmation step. The 10 % cutoff is a screening anchor, not a diagnosis; many owners with sustained PVC burden at this level have structurally normal hearts on echocardiography. Confounders worth ruling out before clinical concern: caffeine-rich days, stimulant medications, sleep deprivation, recent alcohol intake, electrolyte derangement.",
        references = listOf(
            "Baman TS et al. (2010) — Relationship between burden of premature ventricular complexes and left ventricular function. Heart Rhythm 7(7):865-869",
            "Niwano S et al. (2009) — Prognostic significance of frequent PVCs originating from the ventricular outflow tract in patients with normal LV function. Heart 95(15):1230-1237",
            "Park KM et al. (2018) — Frequent premature ventricular complex-induced cardiomyopathy. Korean Circulation Journal 48(8):1-19",
        ),
        earlyDetection = "PPG-derived PVC burden lets a wearable surface a Holter-cardinal signal between clinic visits. The screening threshold (10 %) is intentionally conservative — most owners spend their PVC burden well below 1 %; sustained readings at or above 10 % across several captures are the conventional referral anchor for outpatient cardiology. Suppressed automatically on PPG windows the existing RhythmClassifier scores IRREGULARLY_IRREGULAR — AFib's randomness dominates and would otherwise inflate the burden estimate.",
    )
}
