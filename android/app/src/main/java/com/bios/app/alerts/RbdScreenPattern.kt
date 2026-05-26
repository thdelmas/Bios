package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * REM Sleep Behaviour Disorder (RBD) screen (#206, NEUROLOGY_POV §2.7).
 *
 * RBD is "REM without atonia" — clinically the highest-leverage neurology
 * prodrome currently visible to wearables, because ~80 % of
 * polysomnogram-confirmed idiopathic RBD cases convert to a synucleinopathy
 * (Parkinson's disease, dementia with Lewy bodies, or multiple system
 * atrophy) within 10–15 years (Postuma et al. 2019; Schenck et al. 2013).
 *
 * Diagnosis requires polysomnography with surface EMG; wearable
 * accelerometer bursts during REM sleep are the published research-grade
 * proxy. This pattern composes those candidate events over a 4-week
 * window — sustained signal across many nights distinguishes the
 * RBD-shaped pattern from one-off positional shifts, bed-partner contact,
 * or motion artefacts.
 *
 * ## Manifesto guard (extremely strict for this pattern)
 *
 * NEUROLOGY_POV §3.3 explicitly forbids push-side communication of
 * synucleinopathy / neurodegenerative risk. RBD has a strong long-horizon
 * conversion rate but the wearable proxy is far less specific than PSG,
 * and a push notification claiming "you may be developing Parkinson's"
 * would violate the manifesto in three ways: (1) push-side prognostic
 * communication, (2) person-evaluative push, and (3) speaking to a horizon
 * the owner cannot act on this week.
 *
 * Consequently this pattern:
 *
 *  - is **pull-side only** (`pullSideOnly = true`) — the alert is
 *    persisted to the DB for the alert log surface, but
 *    [com.bios.app.alerts.AlertManager.sendNotification] skips the
 *    system notification channel.
 *  - is **ADVISORY only** — never URGENT. The clinical step is a
 *    referral to sleep medicine; nothing about this is same-week urgent.
 *  - uses **physiological-description framing only** in the alert text.
 *    All references to PD / DLB / MSA / "synucleinopathy" /
 *    "neurodegenerative" / "prodromal" sit on the documentation side,
 *    not in the owner-facing strings. [AlertContentPolicy] bans those
 *    phrases for any push-side surface.
 *  - is **not state-gated** — every owner with detected events sees the
 *    pull-side advisory if they navigate to it, because withholding a
 *    pattern the data clearly shows would violate "Bios never withholds
 *    information the owner looks for."
 *
 * ## Pattern shape
 *
 *  - signal: [MetricType.RBD_EVENT_FLAG], absolute threshold
 *    ≥ [MIN_EVENT_NIGHTS] event-flagged nights in the last
 *    [WINDOW_HOURS] (4 weeks). Each burst-detected night writes a
 *    `rbd_event_flag = 1.0` row; the count is the night count.
 *  - corroborator: [MetricType.REM_MOVEMENT_INDEX] — the per-night
 *    burst count itself, surfaced for the trend view. Not a separate
 *    rule (no second active-signal requirement); included in the
 *    `signalRules` list so the pull-side detail screen can pick it up.
 *
 * References:
 *  - Postuma RB et al. (2019) — Risk and predictors of dementia and
 *    parkinsonism in idiopathic REM sleep behaviour disorder: a
 *    multicentre study. Brain 142(3):744–759.
 *  - Schenck CH et al. (2013) — Delayed emergence of a parkinsonian
 *    disorder or dementia in 81 % of older men initially diagnosed with
 *    idiopathic RBD: a 16-year update. Sleep Medicine 14(8):744–748.
 *  - Iranzo A et al. (2017) — Neurodegenerative disorder risk in
 *    idiopathic REM sleep behaviour disorder. PLoS One 9(2):e89741.
 *  - Louter M et al. (2014) — Accelerometer-based quantitative analysis
 *    of sleep movements as a screening tool for RBD. Mov Disord 29(1):
 *    150–155.
 *  - American Academy of Sleep Medicine (2014) — ICSD-3 diagnostic
 *    criteria for REM sleep behaviour disorder.
 */
object RbdScreenPattern {

    val all by lazy { listOf(rbdScreen) }

    /** Composite window: 4 weeks (28 days). RBD's clinical phenotype is a
     *  pattern of repeated nights, not a single noisy reading. */
    const val WINDOW_HOURS = 28 * 24

    /** Minimum count of event-flagged nights inside [WINDOW_HOURS] before
     *  the advisory fires. Three nights with detected bursts inside a
     *  four-week window is the screening threshold the wearable RBD
     *  literature (Louter 2014; St Louis 2017 expert review) treats as a
     *  meaningful candidate signal; below this the false-positive rate
     *  from non-RBD movement sources is too high. */
    const val MIN_EVENT_NIGHTS = 3

    /**
     * The RBD pattern. Composes [MetricType.RBD_EVENT_FLAG] over a 4-week
     * window. ADVISORY pull-side only; never push.
     */
    val rbdScreen = ConditionPattern(
        id = "rbd_screen",
        title = "Pattern of movement during dream-stage sleep",
        category = ConditionCategory.NEUROLOGICAL,
        signalRules = listOf(
            SignalRule(
                metricType = MetricType.RBD_EVENT_FLAG,
                direction = DeviationDirection.ABOVE,
                thresholdSigma = 0.0,
                minDurationHours = 0,
                weight = 1.0,
                source = ThresholdSource.LITERATURE,
                citation = "Louter et al. (2014) Mov Disord — accelerometer-bursts-in-REM is the wearable proxy for the polysomnographic 'REM without atonia' finding; repeated nights inside a four-week window separates the RBD-shaped pattern from one-off positional or bed-partner artefacts. Postuma et al. (2019) Brain anchors the 4-week multi-night convention. St Louis et al. (2017) Lancet Neurol expert review treats ≥3 nights/4 weeks as the screening operating point.",
                required = true,
                absoluteAbove = MIN_EVENT_NIGHTS.toDouble() - 0.5,
                absoluteWindowHours = WINDOW_HOURS,
                absoluteMinReadings = MIN_EVENT_NIGHTS,
            ),
        ),
        minActiveSignals = 1,
        explanation = "Across the past four weeks, multiple nights of recorded REM sleep have contained accelerometer bursts large enough to exceed the atonia threshold the research literature uses. This pattern can arise from several non-clinical sources — bed-partner contact, mattress coupling, positional shifts, restless legs — but it is also the pattern that polysomnographic studies use as a screening signal worth confirming. The wearable signal is informative for trend-tracking and clinician handoff, not diagnostic.",
        suggestedAction = "Discuss this trend with a healthcare provider, ideally one with sleep-medicine experience. The definitive evaluation is overnight polysomnography with surface EMG. The detected events and REM movement index from Bios can be exported and shared at the appointment.",
        references = listOf(
            "Postuma RB et al. (2019) — Risk and predictors of dementia and parkinsonism in idiopathic REM sleep behaviour disorder: a multicentre study. Brain 142(3):744–759",
            "Schenck CH et al. (2013) — Delayed emergence of a parkinsonian disorder or dementia in 81 % of older men initially diagnosed with idiopathic RBD: a 16-year update. Sleep Medicine 14(8):744–748",
            "Iranzo A et al. (2017) — Neurodegenerative disorder risk in idiopathic REM sleep behaviour disorder. PLoS One 9(2):e89741",
            "Louter M et al. (2014) — Accelerometer-based quantitative analysis of sleep movements as a screening tool for RBD. Movement Disorders 29(1):150–155",
            "St Louis EK et al. (2017) — REM sleep behaviour disorder in Parkinson's disease and other synucleinopathies. Lancet Neurol 16(8):655–667",
            "American Academy of Sleep Medicine (2014) — ICSD-3 diagnostic criteria for REM sleep behaviour disorder",
        ),
        earlyDetection = "REM sleep is normally accompanied by muscle atonia — the body is paralysed so the sleeper does not act out dreams. When that atonia fails, the wearable can detect bursts of acceleration during REM windows the sleep stager has marked. Single-night detections are noisy: a bed-partner brushing the wrist, a leg jerk during arousal, or a positional shift during REM all produce similar accelerometer events. The clinically informative shape is repetition across multiple nights inside a four-week window, which is what this surface composes.",
        prevention = "REM sleep behaviour management is owned by the owner and their sleep-medicine clinician. Standard precautions for sleepers with the condition include securing the bed environment (padding sharp edges, removing nightstand objects, sometimes a separate sleeping arrangement) so any acted-out movement causes no injury. The clinical plan handles those decisions; Bios's role is to surface the trend.",
        healing = "Treatment of confirmed RBD belongs with sleep medicine and neurology. The evidence base supports clonazepam and melatonin as first-line pharmacotherapy; lifestyle factors (alcohol reduction, certain SSRI/SNRI medication review) and environmental safety measures complement the medical plan. The detected-event history and REM movement index from Bios can give the clinician longitudinal context across many nights, which a single in-clinic visit cannot provide.",
        risks = "RBD is associated with a higher long-horizon risk of certain progressive neurological conditions; the published cohort data is unambiguous on this point and any sleep-medicine clinician will reference it during evaluation. Bios does not surface those statistics in this alert because they belong to a clinical conversation with full context, not to a pattern card. The acute risk is injury during sleep (the sleeper or a bed-partner being struck by an acted-out movement), which the precautionary measures above address.",
        // Manifesto-critical guard: this pattern is pull-side only. The
        // alert is persisted to the DB so the owner finds it in the alert
        // log when they navigate there, but no system notification fires
        // — NEUROLOGY_POV §3.3 forbids push-side prognostic communication.
        pullSideOnly = true,
    )
}
