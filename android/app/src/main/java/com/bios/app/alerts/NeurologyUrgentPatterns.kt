package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Neurology URGENT patterns (#216 / NEUROLOGY_POV.md §2.1+§2.3, Triage
 * Inventory #24). Hard-cutoff URGENT alerts that fire on a single owner-
 * logged event or single GCS reading crossing a literature-anchored
 * clinical threshold. Same shape as [EmergencyVitalPatterns] — `required`
 * absolute-threshold rules with `severityFloor = URGENT`.
 *
 * ## Scope discipline
 *
 * v1 ships event-presence URGENT patterns only. A duration-aware
 * `status_epilepticus_convulsive` pattern (single seizure ≥ 5 min per the
 * ILAE 2015 t1 operational definition) needs a per-rule duration filter
 * in [SignalRule] that today's evaluator does not express; the
 * conservative substitute below fires URGENT on **any** owner-logged
 * SEIZURE_EVENT, which is the safety-leaning default (a known epileptic
 * who logs a seizure still benefits from the notification surface
 * documenting the time). Duration-specific status-epilepticus
 * discrimination is a follow-up.
 *
 * Similarly the wearable convulsive-pattern detector (band-pass 2–8 Hz
 * accelerometer + ictal-tachycardia corroborator, Empatica Embrace2
 * shape) is deferred — the substrate exists but a calibrated detector
 * needs its own design pass.
 *
 * ## References
 *
 * - Trinka E et al. (2015) — A definition and classification of status
 *   epilepticus. *Epilepsia* 56(10):1515–1523. (ILAE t1 = 5 min for
 *   convulsive SE.)
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
            seizureEventLogged,
            acuteAlteredConsciousnessLowGcs,
            thunderclapHeadache,
        )
    }

    /**
     * Any owner-logged SEIZURE_EVENT in the last hour escalates URGENT.
     * v1 substitute for the ILAE 2015 status-epilepticus pattern — the
     * conservative shape until duration-aware rules ship.
     */
    val seizureEventLogged = ConditionPattern(
        id = "neurology_seizure_event_logged",
        title = "Seizure event logged",
        category = ConditionCategory.MENTAL_HEALTH,
        signalRules = listOf(
            SignalRule(
                MetricType.SEIZURE_EVENT, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ILAE 2015 / Trinka 2015 — any acute seizure event warrants URGENT clinical evaluation pending duration classification (t1 = 5 min defines convulsive status epilepticus)",
                required = true,
                absoluteAbove = 0.0,
                absoluteWindowHours = 1,
                absoluteMinReadings = 1,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "A seizure event was logged within the last hour. Acute seizures warrant prompt clinical evaluation: a single seizure of any duration is the threshold for an emergency-department visit when no prior diagnosis exists, and a single seizure lasting ≥5 minutes meets the ILAE 2015 operational definition of convulsive status epilepticus regardless of seizure history.",
        suggestedAction = "Call emergency services or proceed to an emergency department immediately if the seizure lasted ≥5 minutes, repeated within an hour, occurred in an owner with no prior seizure diagnosis, or was followed by injury / sustained altered consciousness / breathing difficulty. For an owner with established epilepsy whose pattern matches their baseline, follow the seizure action plan from their neurologist; document the event for the next clinical visit.",
        references = listOf(
            "Trinka E et al. (2015) — A definition and classification of status epilepticus. Epilepsia 56(10):1515-1523",
            "Glauser T et al. (2016) — Evidence-based guideline: treatment of convulsive status epilepticus in children and adults. Epilepsy Currents 16(1):48-61",
        ),
        earlyDetection = "Owner-logged seizure events serve two purposes: (1) timestamp documentation for clinical review and (2) URGENT-tier surfacing for the cases where the seizure represents either a new diagnosis or a status-epilepticus emergency. Duration-aware discrimination (ILAE t1 = 5 min for convulsive SE) is a planned engine extension; until it ships, the conservative pattern surfaces all logged events at URGENT severity.",
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
