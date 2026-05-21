package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Owner-recorded immunisation history (#156, audit gap §2.3). Vaccination
 * status is a permanent, co-equal axis with biomarkers — the Dutch
 * integrated preventive network and Japanese Tokutei Kenshin both assume
 * a clinician can see both. This entity is what the screening-cadence
 * engine (#155) reads to surface "due for shingles" / "due for flu this
 * season" prompts.
 *
 * Distinct from [MetricReading] — vaccines are events with a date and a
 * coded identifier, not quantitative measurements. They have no value
 * column, no unit, no baseline, and never feed the anomaly detector.
 *
 * Free-text [vaccineName] + optional [cvxCode] keeps the entity flexible:
 * an owner can enter "flu shot" by hand and a FHIR import can populate
 * the [cvxCode] from a vaccineCode coding. Tests pin both paths.
 */
@Entity(
    tableName = "immunization_records",
    indices = [Index("occurrenceDate"), Index("cvxCode")],
)
data class ImmunizationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Display name of the vaccine — owner-entered free-text, or the
     * `display` field from a FHIR `Immunization.vaccineCode.coding`.
     * Examples: "Influenza (seasonal)", "COVID-19 mRNA", "Tdap booster",
     * "Shingrix dose 1".
     */
    val vaccineName: String,
    /**
     * Optional CDC CVX code (the canonical vaccine identifier — flu = 88,
     * Tdap = 115, COVID-19 = 213, shingles = 187, etc.). Populated by FHIR
     * import; null when the owner free-texts an entry. SNOMED CT codes
     * from non-US systems would land here too — the field is just a
     * coded identifier, agnostic to system. The screening-cadence engine
     * matches on CVX when present.
     */
    val cvxCode: String? = null,
    /** Epoch millis when the vaccine was administered. */
    val occurrenceDate: Long,
    /**
     * Optional dose number for multi-dose series (Shingrix 1/2, HPV
     * 1/2/3, COVID booster N, etc.). Maps to FHIR
     * `Immunization.protocolApplied.doseNumber`.
     */
    val doseNumber: Int? = null,
    /**
     * Optional lot number for traceability — surfaced verbatim, never
     * parsed. Maps to FHIR `Immunization.lotNumber`.
     */
    val lotNumber: String? = null,
    /** Optional owner free-text annotation. */
    val note: String? = null,
)
