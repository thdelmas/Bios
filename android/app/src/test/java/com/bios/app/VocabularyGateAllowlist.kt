package com.bios.app

/**
 * The only places where a word of
 * [com.bios.app.alerts.AlertContentPolicy.MEDICAL_VOCABULARY] may appear in
 * text Bios shows. An entry covers one word, in one file, in a text that
 * contains [text]; anything else fails the gate with the file and the word.
 * An entry that no longer matches anything fails too, so the list cannot
 * outlive what it excuses.
 *
 * Paths are relative to `src/main`.
 */
object VocabularyGateAllowlist {

    data class Entry(val path: String, val term: String, val text: String, val reason: String)

    private const val UI = "java/com/bios/app/ui"
    private const val OWNER_EVENT =
        "Owner's own record: the kind of journal entry the owner chooses for something they were told or given."
    private const val OWNER_HEADACHE =
        "Owner's own record: the headache diary, where the owner names their own headache and what they took."
    private const val OWNER_COURSES =
        "Owner's own record: the list of courses of sessions the owner attended, under the name the owner gives."

    private const val OWNER_VACCINE =
        "Owner's own record: the name of a vaccine in the picker of the immunisation record."

    val entries: List<Entry> = listOf(
        // Journal entry kinds (owner picks one; Bios never assigns it).
        Entry("java/com/bios/app/model/Enums.kt", "symptom", "Symptom", OWNER_EVENT),
        Entry("java/com/bios/app/model/Enums.kt", "diagnos", "Diagnosis", OWNER_EVENT),
        Entry("java/com/bios/app/model/Enums.kt", "treatment", "Treatment", OWNER_EVENT),
        Entry("$UI/journal/HealthEventSheet.kt", "diagnos", "What was diagnosed?", OWNER_EVENT),
        Entry("$UI/journal/HealthEventSheet.kt", "treatment", "Treatment plan", OWNER_EVENT),

        // Headache diary.
        Entry("$UI/headache/HeadacheDiaryScreen.kt", "migraine", "Log migraine attack", OWNER_HEADACHE),
        Entry("$UI/headache/HeadacheDiaryScreen.kt", "migraine", "Migraine attacks", OWNER_HEADACHE),
        Entry("$UI/headache/HeadacheDiaryScreen.kt", "treatment", "Treatment: ", OWNER_HEADACHE),
        Entry("$UI/headache/HeadacheDiaryDialogs.kt", "migraine", "Log migraine attack", OWNER_HEADACHE),
        Entry("$UI/headache/HeadacheDiaryDialogs.kt", "treatment", "Acute treatment (optional)", OWNER_HEADACHE),

        // Courses of sessions.
        Entry("$UI/interventions/TreatmentCoursesScreen.kt", "treatment", "Treatment courses", OWNER_COURSES),
        Entry("$UI/interventions/TreatmentCoursesScreen.kt", "treatment", "A treatment course groups", OWNER_COURSES),
        Entry("$UI/settings/IdentityCard.kt", "treatment", "Treatment courses", OWNER_COURSES),

        // Immunisation record.
        Entry("$UI/immunisations/VaccineCatalog.kt", "typhoid", "Typhoid (inactivated)", OWNER_VACCINE),
        Entry("$UI/immunisations/VaccineCatalog.kt", "fever", "Yellow fever", OWNER_VACCINE),

        // Standard code descriptions quoted in the FHIR export.
        Entry(
            "java/com/bios/app/export/FhirInterventionMapping.kt", "diagnos", "Intravenous infusion, for therapy",
            "Quoted from a source: the standard description of a procedure code, written into the FHIR bundle.",
        ),
        Entry(
            "java/com/bios/app/export/FhirMetricCodings.kt", "apnea", "Sleep apnea hypopnea index",
            "MetricType key: the standard LOINC name of the apnea_hypopnea_index metric, written into the FHIR bundle.",
        ),
    )
}
