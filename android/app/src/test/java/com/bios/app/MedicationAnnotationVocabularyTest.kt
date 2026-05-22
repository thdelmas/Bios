package com.bios.app

import com.bios.app.data.BeersCriteriaList
import com.bios.app.data.Comorbidity
import com.bios.app.data.CredibleMedsList
import com.bios.app.data.SubstanceSource
import com.bios.app.data.isBeersAvoidIn
import com.bios.app.data.isCredibleMedsQtProlonging
import com.bios.app.model.MedicationAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #194 — extends [MedicationAnnotation] for traditional and
 * functional pharmacopoeias plus surfaces flagging hooks for the
 * cardiology (CredibleMeds) and geriatrics (AGS Beers 2023) audits.
 *
 * The free-text path is the default and must continue to round-trip a
 * pre-#194 row unchanged.
 */
class MedicationAnnotationVocabularyTest {

    private fun med(
        name: String,
        source: SubstanceSource? = null,
        code: String? = null,
        traditional: String? = null,
    ): MedicationAnnotation = MedicationAnnotation(
        id = 0,
        name = name,
        startDate = 1_700_000_000_000L,
        endDate = null,
        note = null,
        substanceSource = source?.toPersistenceString(),
        substanceCode = code,
        traditionalName = traditional,
    )

    // ---- SubstanceSource enum stability ----

    @Test
    fun substance_source_round_trips_through_persistence_string() {
        // The persisted string must be the enum [name], not the ordinal,
        // so reordering values doesn't corrupt existing rows.
        for (source in SubstanceSource.entries) {
            val persisted = source.toPersistenceString()
            assertEquals(source.name, persisted)
            assertEquals(source, SubstanceSource.fromPersistenceString(persisted))
        }
    }

    @Test
    fun substance_source_parse_is_lenient_for_unknown_and_null() {
        // Lenient parse so a future enum removal can't crash an old row.
        assertEquals(SubstanceSource.LOCAL_FREE_TEXT, SubstanceSource.fromPersistenceString(null))
        assertEquals(SubstanceSource.LOCAL_FREE_TEXT, SubstanceSource.fromPersistenceString(""))
        assertEquals(
            SubstanceSource.LOCAL_FREE_TEXT,
            SubstanceSource.fromPersistenceString("FUTURE_REMOVED_VALUE")
        )
    }

    @Test
    fun substance_source_covers_eleven_audits_vocabularies() {
        // The audit asks for: RxNorm, ATC, AYUSH_ICD11 (WHO chapter 26
        // covers Ayurveda / Siddha / Unani / Sowa Rigpa), TCM formula,
        // Kampo NHI, Korean traditional, WHO botanical monograph (covers
        // African Traditional, Indigenous Americas, Modern
        // Non-Allopathic, Vietnamese, Indonesian, Thai), and free text.
        val names = SubstanceSource.entries.map { it.name }.toSet()
        val expected = setOf(
            "RXNORM",
            "ATC",
            "AYUSH_ICD11",
            "TCM_FORMULA_CODE",
            "TSUMURA_NHI_148",
            "KOREAN_AYUSH",
            "WHO_BOTANICAL_MONOGRAPH",
            "LOCAL_FREE_TEXT",
        )
        assertEquals(expected, names)
    }

    // ---- Free-text path is the default and unchanged ----

    @Test
    fun free_text_path_has_all_vocabulary_fields_null() {
        val m = med("metoprolol")
        assertNull("substanceSource defaults to null", m.substanceSource)
        assertNull("substanceCode defaults to null", m.substanceCode)
        assertNull("traditionalName defaults to null", m.traditionalName)
        // And the lenient parse maps null → LOCAL_FREE_TEXT.
        assertEquals(
            SubstanceSource.LOCAL_FREE_TEXT,
            SubstanceSource.fromPersistenceString(m.substanceSource)
        )
    }

    @Test
    fun traditional_substance_persists_in_owner_preferred_script() {
        // The owner records "ashwagandha" with the Devanagari script as
        // the traditional name. Bios must not transliterate or normalise.
        val m = med(
            name = "Ashwagandha",
            source = SubstanceSource.AYUSH_ICD11,
            code = "TM26.AB31",
            traditional = "अश्वगंधा",
        )
        assertEquals("Ashwagandha", m.name)
        assertEquals("AYUSH_ICD11", m.substanceSource)
        assertEquals("TM26.AB31", m.substanceCode)
        assertEquals("अश्वगंधा", m.traditionalName)
    }

    @Test
    fun tcm_formula_code_path_with_hanzi_traditional_name() {
        val m = med(
            name = "Liu Wei Di Huang Wan",
            source = SubstanceSource.TCM_FORMULA_CODE,
            code = "G20.4567",
            traditional = "六味地黄丸",
        )
        assertEquals("六味地黄丸", m.traditionalName)
        assertEquals("TCM_FORMULA_CODE", m.substanceSource)
    }

    @Test
    fun kampo_path_persists_nhi_148_code() {
        val m = med(
            name = "Sho-saiko-to",
            source = SubstanceSource.TSUMURA_NHI_148,
            code = "9",
            traditional = "小柴胡湯",
        )
        assertEquals("TSUMURA_NHI_148", m.substanceSource)
        assertEquals("小柴胡湯", m.traditionalName)
    }

    // ---- CredibleMeds flagging ----

    @Test
    fun credible_meds_flags_sotalol_methadone_amiodarone() {
        // Spot-check the highest-frequency Class 1 drugs.
        assertTrue(med("sotalol").isCredibleMedsQtProlonging())
        assertTrue(med("Sotalol 80mg BID").isCredibleMedsQtProlonging())
        assertTrue(med("Methadone").isCredibleMedsQtProlonging())
        assertTrue(med("amiodarone 200mg daily").isCredibleMedsQtProlonging())
        assertTrue(med("dofetilide").isCredibleMedsQtProlonging())
        assertTrue(med("ondansetron 4mg PRN").isCredibleMedsQtProlonging())
        assertTrue(med("haloperidol").isCredibleMedsQtProlonging())
    }

    @Test
    fun credible_meds_does_not_flag_ibuprofen_or_metoprolol() {
        // Sanity: common non-QT-prolonging drugs must not light up.
        assertFalse(med("ibuprofen").isCredibleMedsQtProlonging())
        assertFalse(med("metoprolol").isCredibleMedsQtProlonging())
        assertFalse(med("acetaminophen").isCredibleMedsQtProlonging())
        assertFalse(med("vitamin D3").isCredibleMedsQtProlonging())
    }

    @Test
    fun credible_meds_handles_brand_names_substring() {
        // Owners often record by brand. Substring match on the
        // CredibleMeds token catches the common dispensing labels.
        assertTrue(med("BETAPACE (sotalol) 80mg").isCredibleMedsQtProlonging())
        assertTrue(med("Zofran ODT").isCredibleMedsQtProlonging())
        assertTrue(med("Haldol decanoate").isCredibleMedsQtProlonging())
    }

    @Test
    fun credible_meds_returns_false_for_blank_and_null_safe() {
        assertFalse(CredibleMedsList.matches(null))
        assertFalse(CredibleMedsList.matches(""))
        assertFalse(CredibleMedsList.matches("   "))
    }

    @Test
    fun credible_meds_subset_is_at_least_the_audit_high_frequency_core() {
        // The audit calls out methadone, dofetilide, ibutilide, sotalol,
        // quinidine, procainamide, disopyramide, amiodarone, droperidol,
        // haloperidol, thioridazine, chlorpromazine, ondansetron, etc.
        // Guard the subset doesn't shrink below that core.
        val mustHave = listOf(
            "methadone", "dofetilide", "ibutilide", "sotalol",
            "quinidine", "procainamide", "disopyramide", "amiodarone",
            "droperidol", "haloperidol", "thioridazine", "chlorpromazine",
            "ondansetron",
        )
        for (token in mustHave) {
            assertTrue(
                "CredibleMeds subset is missing the audit-core token '$token'",
                CredibleMedsList.KNOWN_RISK_TOKENS.contains(token)
            )
        }
        // And the audit asked for ~30-50 — guard the magnitude.
        assertTrue(
            "CredibleMeds subset too thin (${CredibleMedsList.KNOWN_RISK_TOKENS.size})",
            CredibleMedsList.KNOWN_RISK_TOKENS.size >= 30
        )
    }

    // ---- AGS Beers Criteria 2023 ----

    @Test
    fun beers_flags_diphenhydramine_at_65_but_not_younger() {
        // The audit's named test case.
        assertTrue(med("diphenhydramine 25mg").isBeersAvoidIn(age = 65))
        assertTrue(med("diphenhydramine 25mg").isBeersAvoidIn(age = 80))
        assertFalse(med("diphenhydramine 25mg").isBeersAvoidIn(age = 64))
        assertFalse(med("diphenhydramine 25mg").isBeersAvoidIn(age = 30))
    }

    @Test
    fun beers_flags_benzodiazepines_in_elderly_unconditionally() {
        assertTrue(med("alprazolam 0.25mg").isBeersAvoidIn(age = 70))
        assertTrue(med("Xanax").isBeersAvoidIn(age = 70))
        assertTrue(med("lorazepam").isBeersAvoidIn(age = 75))
        assertFalse(med("alprazolam 0.25mg").isBeersAvoidIn(age = 40))
    }

    @Test
    fun beers_flags_nsaid_conditionally_on_ckd_or_heart_failure() {
        // Ibuprofen alone in an older adult without CKD or HF is not
        // unconditionally Beers-avoid (NSAIDs are conditional).
        val ibu = med("ibuprofen 400mg PRN")
        assertFalse(
            "NSAID should not unconditionally flag without comorbidity",
            ibu.isBeersAvoidIn(age = 70)
        )
        // With CKD or HF, the conditional Beers entry fires.
        assertTrue(ibu.isBeersAvoidIn(age = 70, comorbidities = setOf(Comorbidity.CHRONIC_KIDNEY_DISEASE)))
        assertTrue(ibu.isBeersAvoidIn(age = 70, comorbidities = setOf(Comorbidity.HEART_FAILURE)))
        // Still below age threshold = no flag, even with comorbidity.
        assertFalse(ibu.isBeersAvoidIn(age = 40, comorbidities = setOf(Comorbidity.HEART_FAILURE)))
    }

    @Test
    fun beers_does_not_flag_acetaminophen_or_metoprolol() {
        // Sanity: drugs not on the Beers list don't fire.
        assertFalse(med("acetaminophen 500mg").isBeersAvoidIn(age = 80))
        assertFalse(med("metoprolol succinate 50mg").isBeersAvoidIn(age = 80))
        assertFalse(med("vitamin D3").isBeersAvoidIn(age = 80))
    }

    @Test
    fun beers_handles_blank_and_null_safe() {
        assertFalse(BeersCriteriaList.matches(null, age = 80))
        assertFalse(BeersCriteriaList.matches("", age = 80))
        assertFalse(BeersCriteriaList.matches("   ", age = 80))
    }

    @Test
    fun beers_subset_covers_audit_high_frequency_classes() {
        // The audit calls out: first-gen antihistamines (diphenhydramine),
        // benzodiazepines (in elderly), TCAs, NSAIDs (in CKD or HF), PPIs
        // (long-term), muscle relaxants. Guard each is represented.
        val tokens = BeersCriteriaList.ENTRIES.map { it.token }.toSet()
        val mustHave = listOf(
            "diphenhydramine",  // first-gen antihistamine
            "alprazolam",       // benzodiazepine
            "amitriptyline",    // TCA
            "ibuprofen",        // NSAID
            "omeprazole",       // PPI
            "cyclobenzaprine",  // skeletal muscle relaxant
        )
        for (t in mustHave) {
            assertTrue(
                "Beers subset is missing the audit-core token '$t'",
                t in tokens
            )
        }
        assertTrue(
            "Beers subset too thin (${BeersCriteriaList.ENTRIES.size} entries)",
            BeersCriteriaList.ENTRIES.size >= 30
        )
    }

    @Test
    fun beers_default_age_threshold_is_65() {
        assertEquals(65, BeersCriteriaList.DEFAULT_AGE_THRESHOLD)
    }

    // ---- Repo formatter remains backward-compatible ----

    @Test
    fun formatter_still_returns_null_for_empty_list_after_extension() {
        // Re-pin the formatter contract — the new optional fields must
        // not affect the alert-context formatter path.
        assertNull(com.bios.app.data.MedicationAnnotationRepo.formatMedicationContext(emptyList()))
    }

    @Test
    fun formatter_uses_name_not_traditional_name_for_alert_context() {
        // The alert-context line stays neutral and uses the canonical
        // display string. Traditional names live on the medications
        // screen but not in alert text — keeping the alert line stable
        // and AlertContentPolicy-compliant.
        val m = med(
            name = "Ashwagandha",
            source = SubstanceSource.AYUSH_ICD11,
            traditional = "अश्वगंधा",
        )
        val result = com.bios.app.data.MedicationAnnotationRepo.formatMedicationContext(listOf(m))
        assertNotNull(result)
        assertEquals("Annotated current medications: Ashwagandha.", result)
    }
}
