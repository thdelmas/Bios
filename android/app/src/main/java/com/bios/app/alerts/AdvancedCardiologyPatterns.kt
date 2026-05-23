package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Advanced cardiology patterns (#216 / CARDIOLOGY_POV §2.3+§2.5+§2.6,
 * Triage Inventory #26). This file is the home for the four sub-patterns
 * the audit grouped together — POTS, HRR, ectopy burden, and QT class —
 * but only HRR ships in v1 because it is the only one whose substrate is
 * already on the bus (continuous HR + EXERCISE_SESSION). POTS needs
 * accelerometer posture detection, ectopy needs beat-level PPG analysis,
 * QT needs ECG; each warrants its own design pass.
 *
 * Pattern uses [SignalRule.absoluteWindowHours] with a 3-reading minimum
 * over 30 days so a single bad session doesn't fire. Cole 1999 used
 * multi-test averaging; the median-of-three shape is the closest the
 * existing SignalRule machinery expresses to that protocol.
 *
 * ## References
 *
 * - Cole CR et al. (1999) — Heart-rate recovery immediately after exercise
 *   as a predictor of mortality. NEJM 341(18):1351-1357. (HRR1 ≤ 12 bpm
 *   identified 4-fold mortality risk over 6 years.)
 * - Imai K et al. (1994) — Vagally mediated heart rate recovery after
 *   exercise is accelerated in athletes but blunted in patients with
 *   chronic heart failure. JACC 24(6):1529-1535.
 * - Jouven X et al. (2005) — Heart-rate profile during exercise as a
 *   predictor of sudden death. NEJM 352(19):1951-1958.
 *
 * All text obeys [AlertContentPolicy].
 */
object AdvancedCardiologyPatterns {

    val all by lazy { listOf(hrRecoveryImpaired) }

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
}
