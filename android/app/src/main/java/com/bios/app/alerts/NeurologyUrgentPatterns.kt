package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.model.SeizureEventFields
import com.bios.contracts.MetricType

/**
 * Neurology event-triggered alert patterns (#216 / NEUROLOGY_POV.md
 * §2.1+§2.3, Triage Inventory #24). Owner-logged seizure / headache
 * events plus GCS-reading thresholds, each gated by a literature-anchored
 * `required` absolute rule. Same shape as [EmergencyVitalPatterns];
 * severity floor is URGENT for the single-event acute patterns and
 * ADVISORY for [seizureCluster] (chronic-pattern cluster signal).
 *
 * ## Scope discipline
 *
 * v2 (#268) splits the seizure surface into two clinically-distinct
 * patterns: [statusEpilepticusConvulsive] is the duration-aware URGENT
 * gate (single SEIZURE_EVENT ≥ 5 min per the ILAE 2015 t1 operational
 * definition), and [seizureCluster] is the count-aware ADVISORY gate
 * (≥ 3 SEIZURE_EVENT rows in 24 h per Haut 2007). The duration filter
 * lives in [SignalRule.durationAtLeastSec]; the count gate uses the
 * existing [SignalRule.absoluteMinReadings].
 *
 * The wearable convulsive-pattern detector (band-pass 2–8 Hz
 * accelerometer + ictal-tachycardia corroborator, Empatica Embrace2
 * shape, #269 Cut 1) ships at [com.bios.app.model.ConfidenceTier.LOW]
 * and writes `detection_source = "wearable_inferred"` on the payload.
 * Both seizure patterns here apply [SignalRule.excludePayloadFieldValue]
 * to keep those rows out of the URGENT / ADVISORY paths — automation
 * alone must not page the owner for emergency services. Wearable-
 * inferred events stay visible in the timeline; escalation waits for
 * owner confirmation (#269 Cut 2).
 *
 * ## References
 *
 * - Trinka E et al. (2015) — A definition and classification of status
 *   epilepticus. *Epilepsia* 56(10):1515–1523. (ILAE t1 = 5 min for
 *   convulsive SE.)
 * - Haut SR (2007) — Seizure clustering: risks and outcomes.
 *   *Epilepsia* 47(8):1267–1273.
 * - Jennett B, Teasdale G (1974) — Assessment of coma and impaired
 *   consciousness. *Lancet* 304:81–84. (GCS canonical.)
 * - International Headache Society (2018) — ICHD-3 §6.2.2: Headache
 *   attributed to non-traumatic subarachnoid haemorrhage.
 *
 * All text obeys [AlertContentPolicy].
 */
object NeurologyUrgentPatterns {

    val all by lazy {
        listOf(
            statusEpilepticusConvulsive,
            seizureCluster,
            acuteAlteredConsciousnessLowGcs,
            thunderclapHeadache,
        )
    }

    /**
     * Single SEIZURE_EVENT of duration ≥ 5 min within the last hour —
     * meets the ILAE 2015 / Trinka 2015 operational definition of
     * convulsive status epilepticus (t1 = 300 s). URGENT.
     */
    val statusEpilepticusConvulsive = ConditionPattern(
        id = "neurology_status_epilepticus_convulsive",
        title = "Convulsive status epilepticus suspected",
        category = ConditionCategory.MENTAL_HEALTH,
        signalRules = listOf(
            SignalRule(
                MetricType.SEIZURE_EVENT, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ILAE 2015 / Trinka 2015 — single convulsive seizure lasting ≥ 5 minutes (t1 = 300 s) defines convulsive status epilepticus and is a neurological emergency",
                required = true,
                absoluteAbove = 0.0,
                absoluteWindowHours = 1,
                absoluteMinReadings = 1,
                durationAtLeastSec = 300,
                // #269 Cut 2 safety gate: URGENT escalation must require
                // owner confirmation. The wearable convulsive-pattern
                // detector (SeizureDetector) writes LOW-confidence rows
                // with detection_source = "wearable_inferred"; those
                // rows are kept in the timeline but excluded here so
                // automation alone cannot trigger an emergency-services
                // prompt. Bare owner-logged rows (no payload) pass
                // through unchanged.
                excludePayloadFieldValue = SeizureEventFields.DETECTION_SOURCE to
                    SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "A convulsive seizure lasting at least 5 minutes was logged within the last hour. This meets the ILAE 2015 (Trinka et al.) operational definition of convulsive status epilepticus (t1 = 300 s): the duration beyond which spontaneous termination is unlikely and at which morbidity and mortality begin to climb steeply. Status epilepticus is a time-critical emergency regardless of seizure history — established epilepsy does not exempt an owner from the duration-based escalation.",
        suggestedAction = "Call emergency services immediately. While waiting: protect the head, do not place anything in the mouth, time the seizure, note onset and any focal features, place the person in the recovery position once convulsions stop. First-line in-hospital treatment is benzodiazepines (lorazepam IV / midazolam IM / diazepam rectal) per Glauser 2016.",
        references = listOf(
            "Trinka E et al. (2015) — A definition and classification of status epilepticus — report of the ILAE task force. Epilepsia 56(10):1515-1523",
            "Glauser T et al. (2016) — Evidence-based guideline: treatment of convulsive status epilepticus in children and adults. Epilepsy Currents 16(1):48-61",
        ),
        earlyDetection = "The 5-minute cutoff is the clinically-actionable discriminator between a self-terminating seizure and convulsive status epilepticus. Bios's SEIZURE_EVENT log captures `durationSec`; the pattern keys off that column directly so a 2-minute event does not trigger URGENT escalation while a 6-minute event does. The pattern fires per-event without a known-epilepsy exemption — even for owners with established epilepsy, a 5-minute-plus seizure is the threshold for emergency intervention.",
    )

    /**
     * Three or more SEIZURE_EVENT rows within the last 24 hours — the
     * canonical seizure-cluster shape per Haut 2007. ADVISORY: clusters
     * elevate the short-term risk of status epilepticus and warrant
     * clinical follow-up, but a single non-prolonged event in a cluster
     * is not itself an acute emergency.
     */
    val seizureCluster = ConditionPattern(
        id = "neurology_seizure_cluster",
        title = "Seizure cluster in last 24 hours",
        category = ConditionCategory.MENTAL_HEALTH,
        signalRules = listOf(
            SignalRule(
                MetricType.SEIZURE_EVENT, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Haut 2007 Epilepsia 47:1267 — three or more seizure events within 24 hours defines a cluster and elevates the short-term risk of status epilepticus",
                required = true,
                absoluteAbove = 0.0,
                absoluteWindowHours = 24,
                absoluteMinReadings = 3,
                // #269 Cut 2 safety gate: a "cluster" of wearable-inferred
                // LOW-confidence detections is detector noise, not a
                // clinical signal. The cluster pattern's ADVISORY action
                // ("contact treating neurologist within 24 h") is owner-
                // facing and demands confirmation that the events
                // happened — same reasoning as the URGENT gate above.
                excludePayloadFieldValue = SeizureEventFields.DETECTION_SOURCE to
                    SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.ADVISORY,
        explanation = "Three or more seizure events were logged within the last 24 hours. The Haut 2007 cluster definition identifies this pattern as a marker of elevated short-term risk for status epilepticus and for prolonged post-ictal morbidity. Clusters most often reflect a transient destabiliser — missed medication, intercurrent illness, sleep deprivation, alcohol withdrawal, hormonal change — but they can also be the first sign of a progressive process.",
        suggestedAction = "Contact the treating neurologist within 24 hours; if no neurologist is established, seek same-day urgent care or telehealth review. If any single event in the cluster lasted ≥ 5 minutes, treat as status epilepticus instead and call emergency services. Review the seizure action plan (rescue benzodiazepine if prescribed), medication-adherence log, sleep, and recent illness in the days leading up to the cluster.",
        references = listOf(
            "Haut SR (2007) — Seizure clustering: risks and outcomes. Epilepsia 47(8):1267-1273",
            "Jafarpour S et al. (2019) — Seizure cluster: definition, prevalence, consequences, and management. Seizure 68:9-15",
        ),
        earlyDetection = "Seizure clustering is the most actionable short-horizon warning sign for status epilepticus in the established-epilepsy population. The pattern fires from the SEIZURE_EVENT log without a duration filter — clusters are defined by count and recency, not the length of any individual event.",
    )

    /**
     * GCS ≤ 8 = "unable to protect own airway" threshold (Jennett 1974;
     * NICE 2023 head-injury guideline). Single-reading URGENT — there is
     * no clinically meaningful baseline for unconsciousness.
     */
    val acuteAlteredConsciousnessLowGcs = ConditionPattern(
        id = "neurology_acute_altered_consciousness_gcs_le_8",
        title = "Glasgow Coma Scale at or below 8",
        category = ConditionCategory.MENTAL_HEALTH,
        signalRules = listOf(
            SignalRule(
                MetricType.CONSCIOUSNESS_LEVEL, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Jennett & Teasdale 1974 (GCS canonical) + NICE 2023 head-injury guidance — GCS ≤ 8 is the airway-protection threshold and the operational definition of severe head injury / coma",
                required = true,
                absoluteBelow = 9.0,
                absoluteWindowHours = 1,
                absoluteMinReadings = 1,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "A Glasgow Coma Scale total of 8 or below was recorded within the last hour. GCS ≤ 8 is the canonical airway-protection threshold: at this level a person cannot reliably protect their own airway, and the NICE 2023 head-injury guideline classifies it as severe traumatic brain injury when the cause is trauma. Non-traumatic causes (post-ictal state, hypoxia, hypoglycaemia, intoxication, stroke, sepsis, hepatic encephalopathy, metabolic derangement) require the same immediate response.",
        suggestedAction = "Call emergency services immediately. Place the person in the recovery position if no spinal injury is suspected. Do not give food or drink. If the GCS reading was taken by a bystander on a person who appears unconscious, treat as an emergency regardless of the apparent cause.",
        references = listOf(
            "Jennett B, Teasdale G (1974) — Assessment of coma and impaired consciousness. Lancet 304:81-84",
            "Teasdale G et al. (2014) — The Glasgow Coma Scale at 40 years: standing the test of time. Lancet Neurology 13(8):844-854",
            "NICE (2023) — Head injury: assessment and early management (NG232) §1.2.5 — severe head injury defined by GCS ≤ 8",
        ),
        earlyDetection = "GCS is the universal triage scale for acute neurological emergencies. A single observed GCS of 8 or below from a bystander or first responder is sufficient to escalate URGENT — the threshold is operationally equivalent to coma. Pattern uses the standard absoluteBelow cutoff at 9.0 (matching GCS integer semantics: 9 and above are above-threshold, 8 and below trigger the alert).",
    )

    /**
     * Thunderclap headache event — IHS ICHD-3 §6.2.2 SAH screen.
     * Owner-logged report of a sudden-onset headache reaching maximum
     * intensity within 60 seconds. Subarachnoid haemorrhage is the
     * primary differential and a time-sensitive emergency.
     */
    val thunderclapHeadache = ConditionPattern(
        id = "neurology_thunderclap_headache",
        title = "Thunderclap headache event logged",
        category = ConditionCategory.MENTAL_HEALTH,
        signalRules = listOf(
            SignalRule(
                MetricType.THUNDERCLAP_HEADACHE_SUSPECTED, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "IHS ICHD-3 §6.2.2 + Edlow & Caplan 2000 — sudden-onset headache reaching peak intensity within 60 seconds is the SAH (subarachnoid haemorrhage) screening criterion, time-sensitive emergency",
                required = true,
                absoluteAbove = 0.0,
                absoluteWindowHours = 1,
                absoluteMinReadings = 1,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "A thunderclap headache (sudden-onset headache reaching peak intensity within 60 seconds) was logged within the last hour. The IHS ICHD-3 §6.2.2 screening criterion targets subarachnoid haemorrhage, the primary time-sensitive differential. Other emergent causes include reversible cerebral vasoconstriction syndrome (RCVS), cervical artery dissection, cerebral venous sinus thrombosis, pituitary apoplexy, and acute hypertensive crisis. CT within hours of onset is the standard diagnostic step; CSF analysis follows if CT is negative.",
        suggestedAction = "Proceed to an emergency department or call emergency services immediately. Time-to-CT is the key prognostic variable for subarachnoid haemorrhage. Do not delay for symptom progression — thunderclap headache warrants imaging on the index event even when the headache subsequently resolves.",
        references = listOf(
            "International Headache Society (2018) — ICHD-3 §6.2.2: Headache attributed to non-traumatic subarachnoid haemorrhage",
            "Edlow JA, Caplan LR (2000) — Avoiding pitfalls in the diagnosis of subarachnoid hemorrhage. NEJM 342(1):29-36",
            "Connolly ES et al. (2012) — Guidelines for the management of aneurysmal subarachnoid hemorrhage. Stroke 43(6):1711-1737",
        ),
        earlyDetection = "Thunderclap headache is the canonical wearable-detectable owner-reported neurological emergency. The operational definition (peak intensity within 60 s from onset) is distinct from severity score on the headache NRS — a 10/10 migraine that built over an hour is not a thunderclap. The event-logging surface in the journal screen captures both fields; this pattern keys off the velocity flag.",
    )
}
