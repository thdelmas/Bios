package com.bios.app.data

/**
 * Owner-facing comorbidities that change Beers-Criteria avoid-status for
 * a medication. Kept deliberately small — this is *not* a full
 * comorbidity registry, only the conditions Beers actually keys on.
 * Owners record their comorbidities elsewhere (risk profile, free-text
 * note); callers pass the set the owner has annotated.
 *
 * The set is open for extension as future audit issues introduce more
 * Beers-keyed conditions; current shape is sufficient for the §2.4
 * Beers integration in [GERIATRICS_PALLIATIVE_POV.md].
 */
enum class Comorbidity {
    /** Chronic kidney disease (any stage). Drives Beers NSAID warning. */
    CHRONIC_KIDNEY_DISEASE,

    /** Heart failure (HFrEF or HFpEF). Drives Beers NSAID + CCB warnings. */
    HEART_FAILURE,

    /** Cognitive impairment / dementia. Drives Beers anticholinergic warnings. */
    DEMENTIA,

    /** History of falls / fall risk. Drives Beers benzodiazepine warnings. */
    FALLS_HISTORY,

    /** Active or history of peptic ulcer disease. Drives Beers NSAID warnings. */
    PEPTIC_ULCER_DISEASE,
}

/**
 * Static subset of the American Geriatrics Society Beers Criteria 2023
 * "Potentially Inappropriate Medications" list. Used by the
 * GERIATRICS_PALLIATIVE_POV.md §2.4 audit: when an owner ≥65 annotates a
 * medication on this list, the pull-side explanation surface can flag
 * "this medication is on the AGS Beers list for adults 65+".
 *
 * **Manifesto compliance.**
 *  - *Pull-side only.* No push notification ever fires from this list.
 *  - *Recording is not contraindication.* Many older adults legitimately
 *    take a Beers-listed medication after a risk-benefit discussion. The
 *    surface is "this is on the list, ask your prescriber", not "stop".
 *  - *Owner is final.* Bios surfaces; the owner and their prescriber
 *    decide.
 *  - *No external lookup.* Frozen 2023-edition subset; the full Beers
 *    update cycle is handled by the AGS, not by Bios.
 *
 * Source: 2023 AGS Beers Criteria. The subset below covers the
 * highest-frequency outpatient classes called out in the audit:
 * first-generation antihistamines, benzodiazepines (in elderly),
 * tricyclic antidepressants, NSAIDs (in CKD or HF), proton pump
 * inhibitors (long-term), skeletal muscle relaxants, alpha-1
 * antagonists (in older men), and a few high-frequency anticholinergics
 * the audit specifically calls out.
 */
object BeersCriteriaList {

    /** Default Beers age threshold. AGS 2023 uses ≥65. */
    const val DEFAULT_AGE_THRESHOLD: Int = 65

    /**
     * A Beers entry: a substance token plus optional comorbidity
     * predicate. When [requiresComorbidity] is null the avoid-status is
     * unconditional (age threshold only); when set, the entry only
     * matches when the caller passes that comorbidity in the
     * `comorbidities` set.
     */
    data class Entry(
        val token: String,
        val requiresComorbidity: Comorbidity? = null,
    )

    /**
     * Entries flagged Beers "avoid" (or "avoid unless …" reduced to
     * "avoid if comorbidity X"). Substring-matched case-insensitively
     * against the medication name, same matching contract as
     * [CredibleMedsList].
     */
    val ENTRIES: List<Entry> = listOf(
        // First-generation antihistamines — anticholinergic burden
        Entry("diphenhydramine"),
        Entry("benadryl"),                  // brand for diphenhydramine
        Entry("chlorpheniramine"),
        Entry("hydroxyzine"),
        Entry("vistaril"),                  // brand for hydroxyzine
        Entry("promethazine"),
        Entry("doxylamine"),
        Entry("meclizine"),

        // Benzodiazepines — falls / cognition risk in older adults
        Entry("alprazolam"),
        Entry("xanax"),                     // brand for alprazolam
        Entry("clonazepam"),
        Entry("klonopin"),                  // brand for clonazepam
        Entry("diazepam"),
        Entry("valium"),                    // brand for diazepam
        Entry("lorazepam"),
        Entry("ativan"),                    // brand for lorazepam
        Entry("temazepam"),
        Entry("triazolam"),

        // "Z-drugs" — non-benzo hypnotics, similar Beers concern
        Entry("zolpidem"),
        Entry("ambien"),                    // brand for zolpidem
        Entry("zaleplon"),
        Entry("eszopiclone"),

        // Tricyclic antidepressants — anticholinergic + orthostasis
        Entry("amitriptyline"),
        Entry("elavil"),                    // brand for amitriptyline
        Entry("nortriptyline"),
        Entry("doxepin"),                   // >6mg dose-dependent; flag broadly
        Entry("imipramine"),
        Entry("clomipramine"),

        // Skeletal muscle relaxants — sedation + falls
        Entry("cyclobenzaprine"),
        Entry("flexeril"),                  // brand for cyclobenzaprine
        Entry("methocarbamol"),
        Entry("robaxin"),                   // brand for methocarbamol
        Entry("carisoprodol"),
        Entry("soma"),                      // brand for carisoprodol

        // Conditional — NSAIDs avoided when CKD or heart failure
        Entry("ibuprofen", Comorbidity.CHRONIC_KIDNEY_DISEASE),
        Entry("ibuprofen", Comorbidity.HEART_FAILURE),
        Entry("ibuprofen", Comorbidity.PEPTIC_ULCER_DISEASE),
        Entry("naproxen", Comorbidity.CHRONIC_KIDNEY_DISEASE),
        Entry("naproxen", Comorbidity.HEART_FAILURE),
        Entry("naproxen", Comorbidity.PEPTIC_ULCER_DISEASE),
        Entry("diclofenac", Comorbidity.CHRONIC_KIDNEY_DISEASE),
        Entry("diclofenac", Comorbidity.HEART_FAILURE),
        Entry("ketorolac", Comorbidity.CHRONIC_KIDNEY_DISEASE),
        Entry("indomethacin"),              // generally avoid in elderly

        // Conditional — benzodiazepines in fall-history patients
        Entry("alprazolam", Comorbidity.FALLS_HISTORY),
        Entry("clonazepam", Comorbidity.FALLS_HISTORY),
        Entry("zolpidem", Comorbidity.FALLS_HISTORY),

        // Conditional — anticholinergic burden in dementia
        Entry("diphenhydramine", Comorbidity.DEMENTIA),
        Entry("oxybutynin", Comorbidity.DEMENTIA),
        Entry("oxybutynin"),                // also unconditional in older adults

        // Long-term PPI use — Beers cautions beyond 8 weeks
        Entry("omeprazole"),
        Entry("prilosec"),                  // brand for omeprazole
        Entry("esomeprazole"),
        Entry("pantoprazole"),
        Entry("lansoprazole"),

        // First-generation alpha-1 blockers — orthostasis
        Entry("doxazosin"),
        Entry("terazosin"),
        Entry("prazosin"),

        // Sulfonylureas — long-acting hypoglycaemia risk
        Entry("glyburide"),
        Entry("chlorpropamide"),

        // Digoxin — narrow therapeutic index, especially in HF
        Entry("digoxin", Comorbidity.HEART_FAILURE),
    )

    /**
     * Returns true when [substanceName] matches a Beers entry for an
     * owner of [age] years with the given [comorbidities] set. Unconditional
     * entries (no required comorbidity) match whenever
     * [age] ≥ [ageThreshold]. Conditional entries additionally require
     * their `requiresComorbidity` to be present.
     */
    fun matches(
        substanceName: String?,
        age: Int,
        comorbidities: Set<Comorbidity> = emptySet(),
        ageThreshold: Int = DEFAULT_AGE_THRESHOLD,
    ): Boolean {
        if (substanceName.isNullOrBlank()) return false
        if (age < ageThreshold) return false
        val lower = substanceName.lowercase()
        return ENTRIES.any { entry ->
            if (!lower.contains(entry.token)) return@any false
            entry.requiresComorbidity?.let { it in comorbidities } ?: true
        }
    }
}
