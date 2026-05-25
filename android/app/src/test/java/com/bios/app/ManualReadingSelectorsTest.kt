package com.bios.app

import com.bios.app.data.AvpuLevel
import com.bios.app.data.GcsBounds
import com.bios.app.data.ManualReadingContext
import com.bios.app.ui.clinical.MeasurementPreset
import com.bios.app.ui.clinical.defaultRowsFor
import com.bios.app.ui.clinical.manualEntryUniverse
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the selectors the clinical-reading entry surface depends on:
 *  - the canonical vital-sign set the picker offers;
 *  - the AVPU → GCS lossless mapping the consciousness input applies;
 *  - the [ManualReadingContext.isEmpty] contract that decides whether the
 *    sidecar gets written at all.
 *
 * If these selectors silently drift, the bundle entry screen either drops
 * relevant metrics or overwrites GCS values during AVPU shortcut entries.
 */
class ManualReadingSelectorsTest {

    @Test
    fun clinical_picker_includes_every_canonical_vital() {
        // These are the columns of a Spanish ER triage chart (TAS, TAD,
        // SatO2, FC, Temp, FR, EVA, GCS, Flujo O2). All must opt in to
        // manual entry or the picker is missing the form the screen was
        // built to capture.
        val triage = setOf(
            MetricType.BLOOD_PRESSURE_SYSTOLIC,
            MetricType.BLOOD_PRESSURE_DIASTOLIC,
            MetricType.BLOOD_OXYGEN,
            MetricType.HEART_RATE,
            MetricType.SKIN_TEMPERATURE,
            MetricType.RESPIRATORY_RATE,
            MetricType.PAIN_SCORE,
            MetricType.CONSCIOUSNESS_LEVEL,
            MetricType.OXYGEN_FLOW_RATE,
        )
        for (t in triage) {
            assertTrue(
                "${t.key} must allow manual entry — triage chart column",
                t.allowsManualEntry
            )
        }
    }

    @Test
    fun add_reading_screen_universe_covers_every_clinical_manual_metric() {
        // The FAB → AddReadingScreen search picker writes through
        // ManualReadingRepo.add which require()s allowsManualEntry. The
        // universe filter must cover every manual-entry metric outside the
        // dedicated provenance-shaped domains (biomarker has its lab screen,
        // women's health has the isolated reproductive DB). Sleep stays in —
        // its dedicated screen writes the same shape; excluding it just
        // buried the override path.
        val universe = manualEntryUniverse().toSet()
        val expected = MetricType.entries.filter {
            it.allowsManualEntry &&
                it.domain != MetricDomain.BIOMARKER &&
                it.domain != MetricDomain.WOMENS_HEALTH
        }.toSet()
        assertEquals(expected, universe)
        for (m in universe) {
            assertTrue(
                "${m.key} in add-reading universe must allow manual entry",
                m.allowsManualEntry
            )
        }
    }

    @Test
    fun add_reading_universe_includes_owner_typed_targets() {
        // Pharmacy weigh-in shape + HRV4Training morning reading + cuff/
        // pulse-ox vitals + sleep override must all be findable via the
        // search picker.
        val universe = manualEntryUniverse().toSet()
        val mustFind = setOf(
            MetricType.BODY_MASS, MetricType.HEIGHT_CM, MetricType.BMI_KG_PER_M2,
            MetricType.BODY_FAT_PCT, MetricType.LEAN_BODY_MASS_KG, MetricType.FAT_MASS_KG,
            MetricType.BLOOD_PRESSURE_SYSTOLIC, MetricType.BLOOD_PRESSURE_DIASTOLIC,
            MetricType.HEART_RATE, MetricType.RESTING_HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY, MetricType.BLOOD_OXYGEN,
            MetricType.SKIN_TEMPERATURE,
            MetricType.SLEEP_DURATION,
        )
        for (m in mustFind) {
            assertTrue(
                "${m.key} must appear in the AddReadingScreen search universe",
                m in universe
            )
        }
    }

    @Test
    fun body_composition_preset_matches_pharmacy_weighin_shape() {
        // A pharmacy / scale weigh-in prints weight, height, BMI, body fat %,
        // fat mass, lean mass, plus blood pressure. The preset must cover that
        // bundle so the owner doesn't delete triage rows and re-add body comp
        // rows by hand every visit. Order matches the receipt printout.
        val expected = listOf(
            MetricType.BODY_MASS,
            MetricType.HEIGHT_CM,
            MetricType.BMI_KG_PER_M2,
            MetricType.BODY_FAT_PCT,
            MetricType.LEAN_BODY_MASS_KG,
            MetricType.FAT_MASS_KG,
            MetricType.BLOOD_PRESSURE_SYSTOLIC,
            MetricType.BLOOD_PRESSURE_DIASTOLIC,
        )
        val actual = defaultRowsFor(MeasurementPreset.BODY_COMPOSITION).map { it.metric }
        assertEquals(expected, actual)
        for (m in expected) {
            assertTrue(
                "${m.key} must opt in to manual entry — body composition preset",
                m.allowsManualEntry
            )
        }
    }

    @Test
    fun clinical_picker_excludes_biomarker_and_reproductive_metrics() {
        // Biomarkers route through BiomarkerEntryScreen (lab provenance
        // shape); reproductive metrics route through BbtEntryRepo /
        // PeriodEntryRepo (isolated ReproductiveDatabase). The clinical
        // bundle screen filters them out — verify the filter premise:
        // each domain still has at least one allows-manual-entry entry,
        // so excluding by domain is a meaningful split.
        val biomarkerManual = MetricType.entries
            .filter { it.domain == MetricDomain.BIOMARKER && it.allowsManualEntry }
        assertTrue("Biomarker domain must have manual-entry metrics", biomarkerManual.isNotEmpty())
        val reproductiveManual = MetricType.entries
            .filter { it.domain == MetricDomain.WOMENS_HEALTH && it.allowsManualEntry }
        assertTrue("Womens-health domain must have manual-entry metrics", reproductiveManual.isNotEmpty())
    }

    @Test
    fun avpu_maps_lossless_to_canonical_gcs_totals() {
        // AVPU → GCS mapping is the cited entry-time shortcut for
        // CONSCIOUSNESS_LEVEL. Issue #131 fixed these values; renumbering
        // here would silently rewrite the owner's chart history.
        assertEquals(15, AvpuLevel.ALERT.gcsTotal)
        assertEquals(13, AvpuLevel.VOICE.gcsTotal)
        assertEquals(8, AvpuLevel.PAIN.gcsTotal)
        assertEquals(3, AvpuLevel.UNRESPONSIVE.gcsTotal)
    }

    @Test
    fun gcs_bounds_match_clinical_canonical_range() {
        // 3..15 is the textbook GCS total range. The screen's input field
        // hint depends on this; loosening it would let the owner enter
        // out-of-band values that consumers (FHIR export, trends) won't
        // know how to interpret.
        assertEquals(3, GcsBounds.MIN)
        assertEquals(15, GcsBounds.MAX)
    }

    @Test
    fun manual_reading_context_is_empty_when_all_fields_blank() {
        assertTrue(ManualReadingContext().isEmpty)
        assertTrue(ManualReadingContext(clinicName = "", visitNote = "  ").isEmpty)
    }

    @Test
    fun manual_reading_context_records_clinic_provenance() {
        val context = ManualReadingContext(
            clinicName = "Hospital de Triage",
            originalScale = "AVPU",
            originalValue = "V",
        )
        assertFalse(context.isEmpty)
        assertEquals("AVPU", context.originalScale)
        assertEquals("V", context.originalValue)
    }

    @Test
    fun pain_and_consciousness_units_are_score() {
        // The picker shows the unit suffix next to the entry field; SCORE
        // resolves to an empty symbol intentionally (no display suffix
        // for raw 0–10 / 3–15 numbers).
        assertEquals(MetricUnit.SCORE, MetricType.PAIN_SCORE.unit)
        assertEquals(MetricUnit.SCORE, MetricType.CONSCIOUSNESS_LEVEL.unit)
    }
}
