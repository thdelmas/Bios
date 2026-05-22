package com.bios.app.data

/**
 * Static subset of the CredibleMeds.org "Known Risk of Torsades de
 * Pointes" (Class 1) drug list. Used by the cardiology audit's QT-context
 * surface (CARDIOLOGY_POV §2.7): when the owner annotates two medications
 * that both appear here, a *pull-side* explanation surface can note the
 * combined QT-prolonging exposure.
 *
 * **Manifesto compliance.**
 *  - Pull-side only. Bios never pushes a "QT risk" alert from this list.
 *    The surface that consumes [isCredibleMedsQtProlonging] is the
 *    owner-requested medication-context view, not a notification path.
 *  - Recording is not endorsement, and inclusion in this list is not a
 *    contraindication. Many owners legitimately take sotalol or methadone
 *    under their cardiologist's care.
 *  - No external lookup. This is a frozen subset compiled in 2026; the
 *    full CredibleMeds DB is licensed and updates regularly. Bios's job
 *    is to surface the on-record substances, not to replace the
 *    prescribing clinician's QT-monitoring workflow.
 *
 * The list is matched by case-insensitive substring against the
 * medication [com.bios.app.model.MedicationAnnotation.name] field so
 * "sotalol", "Sotalol 80mg", and "BETAPACE (sotalol)" all match.
 * Brand-name aliases are included where they are the dominant
 * dispensing label in major markets.
 *
 * Source: CredibleMeds Known-Risk list, snapshot 2024 (the audit cites
 * "QT-prolonging classes" — methadone, dofetilide, ibutilide, sotalol,
 * quinidine, procainamide, disopyramide, amiodarone, droperidol,
 * haloperidol, thioridazine, chlorpromazine, ondansetron, etc.). The
 * subset below is the high-frequency core used in outpatient and
 * inpatient cardiology workflows.
 */
object CredibleMedsList {

    /**
     * Lowercased substance tokens that, when matched as substrings of a
     * lowercased medication name, mark that medication as known QT-
     * prolonging. Order is informational only — matching scans the
     * entire list. Keep alphabetised by generic name for human review.
     */
    val KNOWN_RISK_TOKENS: List<String> = listOf(
        // Class III antiarrhythmics (the original Torsades-risk class)
        "amiodarone",
        "dofetilide",
        "tikosyn",          // brand for dofetilide
        "dronedarone",
        "ibutilide",
        "corvert",          // brand for ibutilide
        "sotalol",
        "betapace",         // brand for sotalol

        // Class IA antiarrhythmics
        "disopyramide",
        "norpace",          // brand for disopyramide
        "procainamide",
        "quinidine",

        // Opioid agonist with documented Torsades risk
        "methadone",

        // Antipsychotics — high-frequency QT prolongers
        "chlorpromazine",
        "thorazine",        // brand for chlorpromazine
        "droperidol",
        "haloperidol",
        "haldol",           // brand for haloperidol
        "pimozide",
        "thioridazine",
        "mellaril",         // brand for thioridazine

        // 5-HT3 antagonist (most dispensed outpatient on the list)
        "ondansetron",
        "zofran",           // brand for ondansetron

        // Macrolide antibiotics
        "azithromycin",
        "zithromax",        // brand for azithromycin
        "clarithromycin",
        "biaxin",           // brand for clarithromycin
        "erythromycin",

        // Fluoroquinolone antibiotics
        "ciprofloxacin",
        "cipro",            // brand for ciprofloxacin
        "levofloxacin",
        "levaquin",         // brand for levofloxacin
        "moxifloxacin",
        "avelox",           // brand for moxifloxacin

        // Azole antifungals
        "fluconazole",
        "diflucan",         // brand for fluconazole
        "ketoconazole",

        // Antimalarials with extensive Torsades literature
        "chloroquine",
        "hydroxychloroquine",

        // Tricyclic / heterocyclic antidepressant with QT exposure
        "citalopram",
        "celexa",           // brand for citalopram
        "escitalopram",
        "lexapro",          // brand for escitalopram
    )

    /**
     * Returns true when [substanceName] contains any [KNOWN_RISK_TOKENS]
     * token, case-insensitively. Free-text-aware: "sotalol 80mg BID"
     * matches "sotalol". Blank input returns false.
     */
    fun matches(substanceName: String?): Boolean {
        if (substanceName.isNullOrBlank()) return false
        val lower = substanceName.lowercase()
        return KNOWN_RISK_TOKENS.any { token -> lower.contains(token) }
    }
}
