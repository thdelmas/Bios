package com.bios.app.screening

/**
 * Anatomy-direct answers driving screening applicability (#209).
 *
 * Replaces the prior binary "female-bodied" / "male-bodied"
 * [Applicability] flag in the demographics UI. The Indigenous Americas
 * audit (§2.4 two-spirit / nádleehi / winkte / lhamana) named the binary
 * gender / sex model as a design defect; the Ob/Gyn audit (§2.16) named
 * the same UI in PreventiveCareScreen as exclusionary for trans, non-
 * binary, intersex, and post-surgical owners.
 *
 * The fix is to ask what the recommendation needs to know — does the
 * screening's target organ exist for this owner? — without asking the
 * owner to declare a gender or sex category. Mammogram cadence wants to
 * know about breast tissue; cervical screening wants to know about a
 * cervix. Each question is independent because anatomy is.
 *
 * `null` means "owner hasn't said." [ScreeningCadenceEngine] treats null
 * the way the prior store treated null `presentsAs`: render the
 * recommendation as elective rather than filtering it out.
 *
 * Manifesto guard: each field is owner-set, owner-readable, owner-
 * erasable. Bios infers nothing.
 */
data class AnatomyProfile(
    /** Owner has breast tissue (drives mammogram cadence). */
    val hasBreastTissue: Boolean? = null,
    /** Owner has a cervix (drives cervical-cancer screening cadence). */
    val hasCervix: Boolean? = null,
    /** Owner has a uterus (drives endometrial-cancer surveillance discussions). */
    val hasUterus: Boolean? = null,
    /** Owner has a prostate (drives PSA-discussion cadence). */
    val hasProstate: Boolean? = null,
    /**
     * Owner has had gender-affirming surgery affecting screened anatomy.
     * Free-text question because surgical history is highly individual;
     * the boolean is a flag the cadence screen can surface as a note
     * ("after top/bottom surgery, screening cadence may shift").
     */
    val hadGenderAffirmingSurgery: Boolean? = null,
) {

    /**
     * True when every field is null. Used by the cadence screen to render
     * a "set your anatomy" prompt without falsely implying the owner has
     * said no to every question.
     */
    val isAllUnset: Boolean
        get() = hasBreastTissue == null && hasCervix == null &&
            hasUterus == null && hasProstate == null &&
            hadGenderAffirmingSurgery == null

    /**
     * Resolve this anatomy profile against a specific screening catalog
     * key. Per-key resolution (rather than per-Applicability bucket) is
     * what makes anatomy-direct routing actually direct: "do you have a
     * cervix?" gates `cervical_cancer`, "breast tissue?" gates `mammogram`,
     * independently. An owner who answers yes to cervix and no to breast
     * sees cervical screening as applicable and mammography as not.
     *
     * Returns [ApplicabilityResolution.Match] if the relevant anatomy
     * question is answered yes, [ApplicabilityResolution.NotApplicable]
     * if answered no, [ApplicabilityResolution.Unset] if not answered.
     * Catalog keys without an anatomy gate (lipid_panel, hba1c,
     * depression_screen, colorectal_cancer) resolve as Match.
     */
    fun resolveForKey(screeningKey: String): ApplicabilityResolution {
        return when (screeningKey) {
            "mammogram" -> resolve(hasBreastTissue)
            "cervical_cancer" -> resolve(hasCervix)
            "endometrial_cancer" -> resolve(hasUterus)
            "dexa_bone_density" -> {
                // DEXA in the USPSTF schedule pins to postmenopausal
                // owners with uterine / ovarian history; we route on
                // [hasUterus] as the closest available anatomy proxy.
                // Owners without a uterus who want bone-density screening
                // can still record it — [Unset] keeps it elective.
                resolve(hasUterus)
            }
            "aaa_one_time", "psa_discussion" -> resolve(hasProstate)
            else -> ApplicabilityResolution.Match
        }
    }

    private fun resolve(answer: Boolean?): ApplicabilityResolution = when (answer) {
        true -> ApplicabilityResolution.Match
        false -> ApplicabilityResolution.NotApplicable
        null -> ApplicabilityResolution.Unset
    }
}

/**
 * Outcome of resolving an [AnatomyProfile] against a recommendation's
 * [Applicability]. Three states because the engine needs to distinguish
 * "owner said no" (filter out) from "owner hasn't said" (show as elective).
 */
sealed class ApplicabilityResolution {
    /** Owner has the anatomy the recommendation targets; pass the gate. */
    object Match : ApplicabilityResolution()
    /** Owner explicitly said they do not have the anatomy; render not-applicable. */
    object NotApplicable : ApplicabilityResolution()
    /** Owner hasn't answered the relevant question; render the entry as elective. */
    object Unset : ApplicabilityResolution()
}
