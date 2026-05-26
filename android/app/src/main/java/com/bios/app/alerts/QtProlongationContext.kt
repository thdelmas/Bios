package com.bios.app.alerts

import com.bios.app.model.MedicationAnnotation

/**
 * QT-prolongation footnote (#204, CARDIOLOGY_POV §2.7).
 *
 * Pull-side helper that, when the owner has an active medication annotation
 * for a CredibleMeds **Known Risk** (Class 1) QT-prolonging drug, returns a
 * single-sentence footnote suitable for appending to the AFib screen / cardiac
 * rhythm pattern detail view. The footnote is non-judgmental, factual, and
 * directs the owner back to their prescriber — i.e. content-policy compliant
 * (see [AlertContentPolicy]).
 *
 * ## Why this exists
 *
 * QT-interval prolongation is the precondition for torsades-de-pointes
 * (TdP), a polymorphic ventricular tachycardia with high sudden-cardiac-
 * death potential. CredibleMeds (Woosley et al., 2013, Drug Saf) curates
 * the canonical list of drugs by their TdP risk class:
 *
 *  - **Class 1 / Known Risk** — drugs that prolong QT *and* have published
 *    cases of TdP at therapeutic doses. ~70 drugs (subset shipped here).
 *  - Class 2 (Possible Risk), Class 3 (Conditional Risk), Class 4 (Avoid
 *    in cLQTS) — lower-confidence categories; out of scope for v1.
 *
 * When an owner viewing the AFib screen / cardiac rhythm pattern is on a
 * Class 1 drug, the QT-prolongation risk context is clinically relevant —
 * concurrent rhythm abnormalities raise the joint risk. The footnote
 * surfaces that context as data; the clinical decision belongs to the
 * owner and their prescriber.
 *
 * ## Manifesto stance
 *
 * Pull-side only — no push, no URGENT escalation, no judgment of the
 * medication choice. The owner's care plan is owned by them and their
 * prescriber; Bios reports the literature-anchored interaction context
 * when the owner navigates to the relevant detail view.
 *
 * ## References
 *
 *  - Woosley RL, Heise CW, Romero KA — CredibleMeds.org QT-drugs list
 *    (Known Risk / Possible Risk / Conditional Risk / Avoid in cLQTS).
 *    https://crediblemeds.org/ (Drug Saf 2013;36:1043–1055).
 *  - Tisdale JE et al. (2013) — Development and validation of a risk
 *    score to predict QT interval prolongation in hospitalized patients.
 *    Circ Cardiovasc Qual Outcomes 6:479–487.
 *  - Roden DM (2004) — Drug-induced prolongation of the QT interval.
 *    N Engl J Med 350:1013–1022.
 */
object QtProlongationContext {

    /**
     * CredibleMeds **Known Risk** (Class 1) QT-prolonging drugs — generic
     * names normalised to lowercase. Curated subset of the most
     * commonly-prescribed entries on the canonical list. The match is
     * a case-insensitive substring search against
     * [MedicationAnnotation.name] (which is owner-free-text), so brand
     * names like "Cipro" still match against generic "ciprofloxacin"
     * when the owner records the generic name. Brand-name detection is
     * out of scope for v1 — Bios doesn't ship an RxNorm dictionary yet.
     *
     * Snapshot date: CredibleMeds list as of 2024. Periodic refresh is
     * a follow-up; the constant is documented so the maintenance cadence
     * is visible.
     */
    val CREDIBLE_MEDS_CLASS_1_KNOWN_RISK: Set<String> = setOf(
        // Antiarrhythmics
        "amiodarone", "dronedarone", "dofetilide", "ibutilide", "procainamide",
        "quinidine", "sotalol", "flecainide",
        // Antibiotics (macrolides + fluoroquinolones + others)
        "azithromycin", "clarithromycin", "erythromycin", "ciprofloxacin",
        "levofloxacin", "moxifloxacin", "gemifloxacin",
        // Antipsychotics
        "haloperidol", "chlorpromazine", "thioridazine", "pimozide",
        "droperidol", "ziprasidone", "sertindole",
        // Antidepressants (SSRIs / tricyclics with documented TdP cases)
        "citalopram", "escitalopram",
        // Antimalarials
        "chloroquine", "hydroxychloroquine", "halofantrine",
        // Antifungals
        "fluconazole", "pentamidine",
        // Antiemetics
        "ondansetron", "domperidone",
        // Opioid (methadone is the classic example)
        "methadone",
        // Anticancer (kinase inhibitors with documented TdP)
        "arsenic trioxide", "vandetanib", "nilotinib", "sunitinib",
        // Misc
        "cisapride", "terfenadine", "astemizole",
    )

    /**
     * Returns the QT-prolongation footnote text for the AFib / cardiac
     * rhythm detail view if the owner has at least one active medication
     * annotation matching a Class 1 Known Risk drug. Returns null when no
     * match — callers can append the footnote unconditionally without
     * dragging in a stale "you are on …" line.
     *
     * Phrasing is the audit's exact recommendation: factual, prescriber-
     * referral, no second-person lifestyle judgment. Passes
     * [AlertContentPolicy] — CI-pinned by [QtProlongationContextTest].
     */
    fun footnoteFor(activeMedications: List<MedicationAnnotation>): String? {
        val matches = activeMedications.mapNotNull { med ->
            classifyKnownRisk(med.name)?.let { hit -> med.name to hit }
        }
        if (matches.isEmpty()) return null
        // Surface the owner's recorded name (their phrasing); when more
        // than one Class 1 drug is active, join with a comma — Bios is
        // not in the business of picking which one to surface.
        val names = matches.joinToString(", ") { it.first }
        return "You are on $names — increased QT-prolongation risk. Discuss with prescriber."
    }

    /**
     * Returns the canonical CredibleMeds generic name a free-text
     * medication-annotation entry matches, or null if no Class 1 Known
     * Risk drug is detected. Match is case-insensitive whole-word
     * substring — "Amiodarone 200 mg" matches "amiodarone".
     */
    fun classifyKnownRisk(annotationName: String): String? {
        val lower = annotationName.lowercase()
        for (drug in CREDIBLE_MEDS_CLASS_1_KNOWN_RISK) {
            // Word-boundary check so "amiodarone" doesn't match
            // "amiodaronefoo" or the middle of another word.
            val idx = lower.indexOf(drug)
            if (idx < 0) continue
            val before = if (idx == 0) ' ' else lower[idx - 1]
            val afterIdx = idx + drug.length
            val after = if (afterIdx >= lower.length) ' ' else lower[afterIdx]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) {
                return drug
            }
        }
        return null
    }

    /**
     * Convenience: returns the list of canonical Class 1 generic names
     * matched by [activeMedications]. Used by the detail-view to render
     * the drug list separately from the footnote sentence.
     */
    fun activeKnownRiskDrugs(
        activeMedications: List<MedicationAnnotation>,
    ): List<String> = activeMedications.mapNotNull { classifyKnownRisk(it.name) }
}
