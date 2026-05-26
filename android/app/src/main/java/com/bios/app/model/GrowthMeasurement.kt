package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Owner- (or proxy-) recorded anthropometric measurement (#199, audit gap
 * §2.7 PAEDIATRICS_POV.md, with converging support from the Primary-Care
 * surveillance, Geriatrics (sarcopenia), and Oncology (cachexia) audits).
 *
 * Paediatric growth tracking is the canonical primary-paediatric-care
 * surveillance task: height + weight + head-circumference plotted against
 * age-/sex-appropriate WHO or CDC growth curves, with a failure-to-thrive
 * screen on percentile bands crossing downward over consecutive measurements.
 * The same trajectory primitive — lean-mass and weight against time —
 * carries the adult-side sarcopenia (Cruz-Jentoft 2019 EWGSOP2) and
 * cachexia (Fearon 2011) screens.
 *
 * **Why a dedicated entity** (rather than three separate [MetricReading]
 * rows). A growth measurement is **one event**: the owner steps on a scale,
 * gets measured against a wall, has their head circumference tape-measured
 * by their paediatrician. Tying the three values together — and the
 * derived BMI alongside them — preserves that semantic. The growth-chart
 * percentile engine reads matched height+weight+age tuples; splitting them
 * into independent [MetricReading]s would force the engine to time-join
 * across rows, with all the ambiguity that introduces.
 *
 * **Storage shape**. Mainline encrypted DB (the same SQLCipher store as
 * everything else). Not the reproductive sidecar — anthropometry is not
 * cycle-linked, and the manifesto's separate-DB rule scopes that isolation
 * to reproductive data specifically.
 *
 * Mirror writes to [MetricReading] (HEIGHT_CM / BODY_MASS /
 * HEAD_CIRCUMFERENCE_CM / BMI_KG_PER_M2 / LEAN_BODY_MASS_KG / FAT_MASS_KG)
 * happen via the repository so existing trend / aggregate / FHIR-export
 * surfaces see the data without needing to learn a new entity.
 *
 * @param id stable UUID for the measurement event.
 * @param timestamp epoch millis the measurement was taken.
 * @param ageInDays owner's age in days at [timestamp]. Computed once at
 *   write time so the growth-chart engine can resolve LMS rows without
 *   carrying the owner's DOB. Paediatric LMS tables are indexed by age in
 *   days (0–60 months for WHO 0-5y); adult values may store 0 when no
 *   percentile lookup is intended (sarcopenia / cachexia screens read the
 *   trajectory primitives only).
 * @param heightCm length / standing height in cm. WHO 0–24 mo uses
 *   recumbent length; ≥24 mo uses standing height — the LMS reference
 *   selection handles the recumbent/standing distinction.
 * @param weightKg body weight in kg.
 * @param headCircumferenceCm head circumference in cm (paediatric-only;
 *   null for adults).
 * @param bmiKgPerM2 derived BMI. Computed by [bmiFrom] when [heightCm] and
 *   [weightKg] are both present; persisted to keep historical trends
 *   stable if the derivation formula ever changes. Null when either input
 *   is missing.
 * @param leanBodyMassKg lean body mass (kg), e.g. from a Withings smart
 *   scale or DXA report. Null when not measured.
 * @param fatMassKg fat mass (kg), companion to [leanBodyMassKg].
 * @param growthChartReference which LMS table the percentile lookup should
 *   resolve to. Null for adult measurements (no percentile lookup).
 * @param note free-text owner annotation ("growth check at paediatrician",
 *   "post-illness weight loss", etc.). Never read by the engine.
 */
@Entity(
    tableName = "growth_measurements",
    indices = [Index("timestamp"), Index("ageInDays")],
)
data class GrowthMeasurement(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val ageInDays: Int,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val headCircumferenceCm: Float? = null,
    val bmiKgPerM2: Float? = null,
    val leanBodyMassKg: Float? = null,
    val fatMassKg: Float? = null,
    val growthChartReference: GrowthChartReference? = null,
    val note: String? = null,
) {
    companion object {
        /**
         * Pure BMI derivation. Returns null when either input is missing
         * or non-positive. Extracted from the data-class constructor so
         * it can be unit-tested without a DB and so callers (the
         * repository, the manual-entry surface) share one definition.
         *
         * BMI = weight (kg) / height (m)². Height is expressed in
         * centimetres on input; the conversion to metres is part of this
         * helper.
         */
        fun bmiFrom(heightCm: Float?, weightKg: Float?): Float? {
            if (heightCm == null || weightKg == null) return null
            if (heightCm <= 0f || weightKg <= 0f) return null
            val heightM = heightCm / 100f
            return weightKg / (heightM * heightM)
        }
    }
}

/**
 * Growth-chart reference selection for percentile lookups.
 *
 * Three independent published references cover the paediatric range:
 *
 *  - **WHO 0–5y** — international standard for breastfed-population growth.
 *    The default for the 0–60 month range. Reference: WHO Multicentre
 *    Growth Reference Study, 2006. Tables published at
 *    `https://www.who.int/tools/child-growth-standards`.
 *  - **WHO 5–19y** — WHO reanalysis of the 1977 NCHS reference for the
 *    school-age and adolescent range. Reference: de Onis et al. 2007.
 *  - **CDC 2–20y** — US-population growth charts. Recommended in the US
 *    for ≥2y when local context matters. Reference: Kuczmarski et al.
 *    2002 CDC growth charts.
 *  - **Fenton premature** — for infants born <37 weeks gestation,
 *    corrected for gestational age. Reference: Fenton & Kim 2013.
 *
 * v1 ships LMS values inline for WHO 0–2y weight-for-age and
 * length-for-age (the high-yield slice for failure-to-thrive surveillance);
 * the remaining reference tables are tracked as a follow-up
 * (`docs/audits/PAEDIATRICS_POV.md §2.7`).
 */
enum class GrowthChartReference(val displayName: String) {
    WHO_0_5Y("WHO 0–5y"),
    WHO_5_19Y("WHO 5–19y"),
    CDC_2_20Y("CDC 2–20y"),
    FENTON_PREMATURE("Fenton (premature)"),
}
