package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Owner-administered symptom-burden capture using the **Edmonton Symptom
 * Assessment System — revised** (ESAS-r; Watanabe 2011 J Pain Symptom
 * Manage 41(2):456-468). Nine 0-10 numeric-rating-scale items plus an
 * optional owner-named "other" symptom — the global standard PRO
 * instrument in palliative medicine and cancer-treatment toxicity
 * tracking.
 *
 * Closes audit gap §2.8 of
 * [docs/audits/GERIATRICS_PALLIATIVE_POV.md](../../../../../docs/audits/GERIATRICS_PALLIATIVE_POV.md)
 * (issue #185). Also referenced by the oncology, surgical, and neurology
 * audits as a missing PRO surface.
 *
 * Manifesto guards:
 *  - **Owner-administered, never inferred.** Bios never derives any of
 *    these scores from biomarkers. The owner reports; Bios stores.
 *  - **Pull-side only.** No notification fires on a recorded report; no
 *    reminder fires to fill one in. The owner records on their own terms;
 *    the trend view is visible only when they navigate to it.
 *  - **No streaks, no gamification, no nudges.** The list view is a list
 *    of dated reports, nothing more.
 *  - **Stays on-device.** Lives in the main encrypted DB; leaves only
 *    when the owner explicitly invokes the FHIR exporter.
 *
 * Distinct from the existing [MetricReading]-backed `PAIN_SCORE`: that
 * surface is a single 0-10 NRS row; ESAS-r is a co-temporal panel of
 * nine items that clinical readers read *as a panel*. Storing them as a
 * single row preserves the panel semantics for the FHIR exporter and
 * for the trend view, which compares item scores within a report.
 *
 * Each item is stored as a 0-10 Int. The validity range is enforced at
 * the [com.bios.app.data.EsasReportRepo] entry point — UI sliders are
 * also pinned 0-10, so an out-of-range value would have to come from a
 * test or a direct DAO call.
 */
@Entity(
    tableName = "esas_reports",
    indices = [Index("timestamp")],
)
data class EsasReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis when the report was completed. */
    val timestamp: Long,
    /** ESAS-r item 1 — pain, 0 (no pain) to 10 (worst possible pain). */
    val painScore: Int,
    /** ESAS-r item 2 — tiredness (lack of energy). */
    val tirednessScore: Int,
    /** ESAS-r item 3 — drowsiness (feeling sleepy). */
    val drowsinessScore: Int,
    /** ESAS-r item 4 — nausea. */
    val nauseaScore: Int,
    /** ESAS-r item 5 — lack of appetite. */
    val appetiteScore: Int,
    /** ESAS-r item 6 — shortness of breath. */
    val shortnessOfBreathScore: Int,
    /** ESAS-r item 7 — depression (feeling sad). */
    val depressionScore: Int,
    /** ESAS-r item 8 — anxiety (feeling nervous). */
    val anxietyScore: Int,
    /**
     * ESAS-r item 9 — wellbeing (overall feeling). Note the scale is
     * inverted in the original instrument (0 = best wellbeing, 10 =
     * worst wellbeing) so that "higher is worse" is uniform across all
     * nine items. We store it the same way for clinician-readability.
     */
    val wellbeingScore: Int,
    /**
     * Optional owner-named additional symptom — the ESAS-r "Other
     * problem" slot. `otherSymptomLabel` is the owner's free-text label
     * (e.g. "constipation", "neuropathy"); `otherSymptomScore` is the
     * 0-10 rating. Either both are non-null or both are null.
     */
    val otherSymptomLabel: String? = null,
    val otherSymptomScore: Int? = null,
    /** Optional owner free-text note attached to this report. */
    val note: String? = null,
) {
    companion object {
        const val MIN_SCORE = 0
        const val MAX_SCORE = 10

        /** Validates a 0-10 score; throws if out of range. */
        fun validateScore(name: String, score: Int) {
            require(score in MIN_SCORE..MAX_SCORE) {
                "$name must be in $MIN_SCORE..$MAX_SCORE (got $score)"
            }
        }

        /**
         * Identifier vocabulary for the nine canonical items plus the
         * optional "other" slot. The [toFhirObservationCodes] utility on
         * a report keys its result map by these strings.
         */
        const val ITEM_PAIN = "pain"
        const val ITEM_TIREDNESS = "tiredness"
        const val ITEM_DROWSINESS = "drowsiness"
        const val ITEM_NAUSEA = "nausea"
        const val ITEM_APPETITE = "appetite"
        const val ITEM_SHORTNESS_OF_BREATH = "shortness_of_breath"
        const val ITEM_DEPRESSION = "depression"
        const val ITEM_ANXIETY = "anxiety"
        const val ITEM_WELLBEING = "wellbeing"
        const val ITEM_OTHER = "other"
    }
}

/**
 * LOINC mapping for the ESAS-r panel and its component items, used by
 * the FHIR exporter so a clinician reader sees the standard codes
 * rather than a Bios-only vocabulary.
 *
 * Codes verified against loinc.org's panel catalog:
 *  - **89216-1** — Edmonton symptom assessment system revised panel
 *    (the LOINC ESAS-r panel code).
 *  - **38221-8** — Pain severity - 0-10 verbal numeric rating
 *    [Score] - Reported.
 *  - **75265-9** — Total symptom distress score [ESAS-r] — used as the
 *    panel-summary observation when an aggregate is reported.
 *
 * Per-item LOINCs are **not** asserted here for tiredness, drowsiness,
 * nausea, lack of appetite, shortness of breath, depression, anxiety,
 * and wellbeing. The LOINC catalog does have ESAS-component codes for
 * several of these (e.g. fatigue, dyspnea, depression have their own
 * "0-10 verbal numeric rating" codes), but the exact code-to-item
 * pairing was not verifiable from inside this environment and the
 * manifesto explicitly prohibits fabricating LOINC codes that a
 * clinical system would then accept as authoritative. The exporter
 * therefore emits a [BIOS_SYSTEM] coding for each item, and the panel
 * code as the wrapping `code` of the FHIR Observation.
 *
 * Owner-named "other" symptoms carry no LOINC by design — the label
 * is free-text and the only honest coding is the owner's own string.
 *
 * **TODO**: verify and add the per-item LOINCs for tiredness,
 * drowsiness, nausea, appetite, shortness of breath, depression,
 * anxiety, and wellbeing — see issue #185 follow-up.
 */
object EsasLoinc {
    /** ESAS-r panel — the wrapping Observation `code`. */
    const val PANEL_CODE = "89216-1"
    const val PANEL_DISPLAY = "Edmonton symptom assessment system revised panel"

    /** Pain severity 0-10 NRS, reported. */
    const val PAIN_CODE = "38221-8"
    const val PAIN_DISPLAY = "Pain severity - 0-10 verbal numeric rating [Score] - Reported"

    /**
     * Total symptom distress score — used for the panel-summary
     * observation that some clinical systems expect.
     */
    const val TOTAL_DISTRESS_CODE = "75265-9"
    const val TOTAL_DISTRESS_DISPLAY = "Total symptom distress score [ESAS-r]"

    /**
     * Bios-local coding system for the eight ESAS-r items whose
     * per-item LOINC was not verifiable at write-time. A clinician
     * reader sees the panel code on the wrapping Observation and the
     * Bios system for the components — the round-trip is honest about
     * what was verified and what wasn't.
     */
    const val BIOS_SYSTEM = "https://bios.health/esas-r"
}

/**
 * Coding for a single ESAS-r item. [system] is either the LOINC URI
 * (`http://loinc.org`) when [code] is a verified LOINC, or
 * [EsasLoinc.BIOS_SYSTEM] when only the Bios-local code is asserted.
 */
data class EsasItemCoding(
    val item: String,
    val system: String,
    val code: String,
    val display: String,
    val score: Int,
)

/**
 * Returns the panel-level and per-item codings for this report. Used
 * by the FHIR exporter to build a single Observation with the panel
 * `code` and per-item `component` codings.
 *
 * The "other" symptom is included when both label and score are
 * non-null; its system is always [EsasLoinc.BIOS_SYSTEM] because
 * owner-named symptoms have no canonical code by construction.
 */
fun EsasReport.toFhirObservationCodes(): List<EsasItemCoding> {
    val items = mutableListOf<EsasItemCoding>()
    items += EsasItemCoding(
        item = EsasReport.ITEM_PAIN,
        system = "http://loinc.org",
        code = EsasLoinc.PAIN_CODE,
        display = EsasLoinc.PAIN_DISPLAY,
        score = painScore,
    )
    items += biosItem(EsasReport.ITEM_TIREDNESS, "Tiredness (ESAS-r)", tirednessScore)
    items += biosItem(EsasReport.ITEM_DROWSINESS, "Drowsiness (ESAS-r)", drowsinessScore)
    items += biosItem(EsasReport.ITEM_NAUSEA, "Nausea (ESAS-r)", nauseaScore)
    items += biosItem(EsasReport.ITEM_APPETITE, "Lack of appetite (ESAS-r)", appetiteScore)
    items += biosItem(
        EsasReport.ITEM_SHORTNESS_OF_BREATH,
        "Shortness of breath (ESAS-r)",
        shortnessOfBreathScore,
    )
    items += biosItem(EsasReport.ITEM_DEPRESSION, "Depression (ESAS-r)", depressionScore)
    items += biosItem(EsasReport.ITEM_ANXIETY, "Anxiety (ESAS-r)", anxietyScore)
    items += biosItem(EsasReport.ITEM_WELLBEING, "Wellbeing (ESAS-r, 0=best)", wellbeingScore)
    if (otherSymptomLabel != null && otherSymptomScore != null) {
        items += EsasItemCoding(
            item = EsasReport.ITEM_OTHER,
            system = EsasLoinc.BIOS_SYSTEM,
            code = "other",
            display = otherSymptomLabel,
            score = otherSymptomScore,
        )
    }
    return items
}

private fun EsasReport.biosItem(item: String, display: String, score: Int) =
    EsasItemCoding(
        item = item,
        system = EsasLoinc.BIOS_SYSTEM,
        code = item,
        display = display,
        score = score,
    )
