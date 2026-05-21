package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Sleep-apnea screening pattern (#157, audit gap §2.8). Surfaces vendor-
 * detected apnea events or owner-entered AHI alongside the wearable
 * signatures that corroborate sleep-disordered breathing — nocturnal SpO2
 * desaturation and sustained resting-HR elevation.
 *
 * Lives in its own file (same convention as [EmergencyVitalPatterns],
 * [Wave5BiomarkerPatterns]) so [ConditionPatterns] stays under the
 * 500-line cap.
 *
 * **Threshold sourcing.** AASM (American Academy of Sleep Medicine)
 * scoring is universal: AHI 5–15 mild, 15–30 moderate, ≥30 severe OSA.
 * Bios gates at the 5/h screening threshold — the clinical floor where
 * formal sleep-medicine evaluation is recommended.
 */
object SleepApneaPattern {

    val all by lazy { listOf(sleepApneaScreen) }

    /**
     * AHI ≥5 events/h (AASM mild-OSA screening threshold) paired with at
     * least one of: nocturnal SpO2 desaturation, sustained resting-HR
     * elevation, sleep-fragmentation rise. AHI is the lab anchor — without
     * a measured AHI (vendor-derived nightly or owner-entered from a
     * PSG/HSAT), the wearable signals alone are too nonspecific to
     * single-handedly screen for OSA.
     *
     * `required = true` on the AHI rule mirrors the biomarker-pattern
     * shape: the lab gates the pattern; wearable trends corroborate.
     */
    val sleepApneaScreen = ConditionPattern(
        id = "sleep_apnea_screen",
        title = "Sleep-disordered breathing signals detected",
        category = ConditionCategory.RESPIRATORY,
        signalRules = listOf(
            SignalRule(
                MetricType.AHI, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "AASM scoring manual (Berry et al. 2020) — AHI ≥5 events/h is the mild-OSA threshold; severity tiers 5–15 mild, 15–30 moderate, ≥30 severe",
                required = true,
                absoluteAbove = 5.0,
            ),
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 1.0, 168, 1.2,
                ThresholdSource.LITERATURE,
                "Berry et al. 2020 — sustained nocturnal SpO2 desaturation is the canonical wearable signature of sleep-disordered breathing",
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Somers et al. 2008 — OSA produces sustained nocturnal sympathetic activation and resting-HR elevation via repeated arousals and chemoreflex stimulation",
            ),
            SignalRule(
                MetricType.SLEEP_FRAGMENTATION_INDEX, DeviationDirection.ABOVE, 1.0, 168, 0.6,
                ThresholdSource.LITERATURE,
                "Bonzini et al. 2020 — apnea-driven micro-arousals raise the post-onset awakening count even when total sleep time appears normal",
            ),
        ),
        minActiveSignals = 2,
        explanation = "The most recent apnea-hypopnea index sits at or above 5 events/hour — the AASM mild-OSA screening threshold — while at least one corroborating wearable signal has appeared: sustained nocturnal SpO2 desaturation, resting-HR drift, or rising sleep fragmentation. The AHI anchors the pattern; the wearable signals on their own have many possible causes.",
        suggestedAction = "Discuss the AHI value and the nightly SpO2 / resting-HR trends with a healthcare provider. The standard follow-up is a sleep-medicine referral for confirmatory evaluation — home sleep apnea test (HSAT) or in-laboratory polysomnography. The AHI reading, SpO2 trend, and RHR trend from Bios are useful to share.",
        references = listOf(
            "Berry RB et al. (2020) — AASM Scoring Manual v2.6 (Apnea-hypopnea index definitions and severity)",
            "Kapur VK et al. (2017) — Clinical Practice Guideline for Diagnostic Testing for Adult OSA (AASM)",
            "Somers VK et al. (2008) — Sleep apnea and cardiovascular disease (AHA/ACC scientific statement)",
        ),
        earlyDetection = "Apple Watch and Samsung Galaxy Watch ship FDA-cleared sleep-apnea detection algorithms; Bios's role is to surface those passthrough signals alongside the corroborating physiology, not to re-detect. An AHI ≥5 events/hour on a wearable-derived nightly estimate is the screening threshold at which sleep-medicine evaluation is recommended — confirmation requires HSAT or PSG. The owner can also enter an AHI directly from an existing sleep-study report.",
    )
}
