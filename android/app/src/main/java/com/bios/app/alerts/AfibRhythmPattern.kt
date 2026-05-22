package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * PPG-derived AFib screening (#180, cardiology audit §2.1).
 *
 * Closes the headline gap that the audit named: the legacy
 * `atrialFibrillationScreen` pattern (now
 * [AutonomicPatternShiftPattern.autonomicPatternShift])
 * gated on personal-baseline HRV instability, not on classification of the
 * RR-interval series itself. The Apple Heart Study (Perez 2019 NEJM), Huawei
 * mAFA-II (Guo 2019 JACC), Fitbit Heart Study (Lubitz 2022 Circulation), and
 * the underlying Tateno-Glass 2001 algorithm all do the same fundamental
 * computation: classify the IBI tachogram as regular or irregularly irregular
 * using Poincaré dispersion (SD1/SD2), sample entropy, and a ΔRR
 * turning-point ratio. That is what `RhythmClassifier` does on-device, and
 * what this pattern reads via the [MetricType.IRREGULAR_RHYTHM_BURDEN]
 * derived metric.
 *
 * **Burden semantics.** Each PPG capture writes a per-session verdict as
 * 0 % (regular) or 100 % (irregularly irregular). The pattern's absolute-
 * threshold rule reads the **median** of the recent burden values over the
 * last [BURDEN_WINDOW_HOURS] (7 days), gated by [BURDEN_MIN_READINGS]
 * captures so a single odd reading can't fire the screen. With a 0/100
 * binary metric, median > [BURDEN_MEDIAN_CUTOFF] (50.0) means the **majority
 * of recent PPG sessions were scored irregularly irregular** — the analogue
 * of the Apple Heart Study notification gate ("≥5 of 6 consecutive
 * tachograms irregular within 48 h"), adapted to Bios's owner-initiated
 * capture cadence.
 *
 * **Severity.** ADVISORY-only (the engine default cap). The cardiology
 * audit §3.1 was explicit that a PPG-only AFib screen must not push as
 * URGENT — wearable PPG-AFib detection has ~34 % PPV (Perez 2019) and
 * pushing an "urgent" notification on a screening signal would violate the
 * manifesto's "silence is a feature" framing. The pattern surfaces on the
 * pull-side detail view; clinical ECG confirmation is the next step the
 * suggestedAction recommends.
 *
 * **Why this matters.** Untreated AFib increases stroke risk 5-fold and is
 * the dominant cardio-embolic stroke source (Wolf 1991; January 2014
 * AHA/ACC/HRS guideline). Many strokes are the first sign of previously
 * undiagnosed AFib. A passive, on-device, owner-initiated screening surface
 * — with honest framing and clinical-ECG handoff — is a protection-of-owner
 * feature that aligns with the manifesto exactly when it is delivered as a
 * pull-side detail rather than as an urgent push.
 *
 * All text obeys [AlertContentPolicy].
 */
object AfibRhythmPattern {

    val all by lazy { listOf(paroxysmalAfibScreen) }

    /** Rolling-burden window: 7 days. Apple Heart Study's underlying gate was
     *  shorter (48 h, 5 of 6 tachograms) because the watch can poll
     *  passively; Bios's PPG capture is owner-initiated, so a longer window
     *  is needed to accumulate enough sessions. */
    private const val BURDEN_WINDOW_HOURS = 168

    /** Minimum PPG captures in the window before the screen fires. Below
     *  this the burden estimate is noise. Apple Heart Study required 5
     *  consecutive irregular tachograms before notifying; this 5-capture
     *  floor mirrors that gate. */
    private const val BURDEN_MIN_READINGS = 5

    /** Median cutoff. With per-session verdicts encoded as 0.0 or 100.0,
     *  median > 50 means the **majority** of recent captures were scored
     *  irregularly irregular — the literature-anchored "irregularly
     *  irregular rhythm is sustained, not transient" condition. */
    private const val BURDEN_MEDIAN_CUTOFF = 50.0

    /**
     * Fires when ≥5 PPG captures over the past 7 days have been written and
     * the median per-session irregular-rhythm verdict exceeds 50 % — i.e.
     * the majority of recent captures were scored irregularly irregular by
     * [com.bios.app.engine.RhythmClassifier].
     *
     * Single signal rule (the burden metric anchors the pattern; no
     * corroborators required). `minActiveSignals = 1`. Severity is
     * ADVISORY (engine default cap) — pull-side detail surface, not URGENT
     * push, per cardiology audit §3.1.
     */
    val paroxysmalAfibScreen = ConditionPattern(
        id = "paroxysmal_afib_screen",
        title = "Irregular rhythm pattern across recent PPG captures",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                metricType = MetricType.IRREGULAR_RHYTHM_BURDEN,
                direction = DeviationDirection.ABOVE,
                thresholdSigma = 0.0,
                minDurationHours = 0,
                weight = 2.0,
                source = ThresholdSource.LITERATURE,
                citation = "Perez 2019 (Apple Heart Study); Guo 2019 (mAFA-II); Lubitz 2022 (Fitbit Heart Study); Tateno-Glass 2001 IEEE TBE — RR-interval rhythm classification on PPG-derived IBI series with majority-irregular sustained burden as the screening gate",
                required = true,
                absoluteAbove = BURDEN_MEDIAN_CUTOFF,
                absoluteWindowHours = BURDEN_WINDOW_HOURS,
                absoluteMinReadings = BURDEN_MIN_READINGS,
            )
        ),
        minActiveSignals = 1,
        explanation = "The majority of recent fingertip-PPG captures (median burden above 50 %) were scored irregularly irregular by the on-device rhythm classifier — Poincaré SD1/SD2 dispersion, sample entropy, and ΔRR turning-point ratio all elevated. This is the same family of computations used by the Apple Heart Study, mAFA-II, and Fitbit Heart Study. PPG-based screening has roughly 34 % positive predictive value for AFib on follow-up ECG (Perez 2019) — informative for screening, not diagnostic. Many non-AFib factors can produce an irregular RR series: frequent ectopy, motion artifact across multiple sessions, atrial flutter, or sinus arrhythmia in younger owners.",
        suggestedAction = "Discuss these readings with a healthcare provider. The standard follow-up is a clinical ECG — single-lead (Apple Watch, KardiaMobile, Withings ScanWatch) or 12-lead in office — to confirm or rule out atrial fibrillation. The burden trend and individual session data can be exported and shared with a cardiologist.",
        references = listOf(
            "Perez MV et al. (2019) — Large-scale assessment of a smartwatch to identify atrial fibrillation. N Engl J Med 381:1909–1917 (Apple Heart Study)",
            "Guo Y et al. (2019) — Mobile photoplethysmographic technology to detect atrial fibrillation. JACC 74:2365–2375 (mAFA-II)",
            "Lubitz SA et al. (2022) — Detection of atrial fibrillation in a large population using wearable devices: the Fitbit Heart Study. Circulation 146:1415–1424",
            "Tateno K & Glass L (2001) — Automatic detection of atrial fibrillation using the coefficient of variation and density histograms of RR and ΔRR intervals. IEEE Trans Biomed Eng 48:169–179",
            "January CT et al. (2014) — AHA/ACC/HRS guideline for management of atrial fibrillation"
        ),
        earlyDetection = "Atrial fibrillation is the most common sustained cardiac arrhythmia, affecting 2–3 % of adults, and is the dominant cardio-embolic stroke source. Many episodes are paroxysmal — they come and go — and are missed by periodic clinical ECGs. The PPG-derived rhythm classifier on each Bios capture computes Poincaré dispersion (SD1/SD2 ratio), sample entropy, and the ΔRR turning-point ratio over the inter-beat-interval series; an irregularly irregular RR pattern is the electrocardiographic signature of AFib. The 7-day rolling burden is the analogue of the Apple Heart Study's '5-of-6 tachograms' notification gate, adapted to owner-initiated capture cadence.",
        prevention = "Modifiable AFib risk factors include hypertension (the strongest single modifiable risk), obesity, excessive alcohol consumption, untreated sleep apnea, and very intense endurance exercise. Managing blood pressure, maintaining a healthy weight, moderating alcohol, and treating sleep apnea all significantly reduce AFib risk. Regular moderate exercise is protective. Adequate sleep and stress management support stable atrial electrophysiology.",
        healing = "Confirmed AFib management requires a healthcare provider. Standard treatment options span rate control (beta-blockers, calcium channel blockers), rhythm control (antiarrhythmics, cardioversion, catheter ablation), and anticoagulation for stroke prevention — choice depends on episode frequency, symptoms, comorbidities, and stroke-risk scoring (CHA₂DS₂-VASc). Lifestyle modifications — weight loss, alcohol reduction, sleep-apnea treatment, blood-pressure control — significantly reduce AFib burden. The burden trend and individual session data from Bios can help a cardiologist assess episode frequency over weeks to months.",
        risks = "Untreated AFib raises stroke risk approximately 5-fold; many strokes are the first sign of previously undiagnosed AFib. AFib also contributes to heart failure, cognitive decline, and reduced quality of life. Paroxysmal AFib can progress to persistent or permanent AFib over time if underlying drivers are not addressed. Screening — wearable or otherwise — is imperfect (Perez 2019: ~34 % positive predictive value on PPG) but catches episodes that periodic clinical visits miss. The clinical-ECG handoff this pattern recommends is the established next step."
    )
}
