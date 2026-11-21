package com.bios.app

import com.bios.app.data.SubstanceSource
import com.bios.app.model.MedicationAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #194 — extends [MedicationAnnotation] for traditional and
 * functional pharmacopoeias.
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
}
