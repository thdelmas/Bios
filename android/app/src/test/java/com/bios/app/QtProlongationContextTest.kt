package com.bios.app

import com.bios.app.alerts.QtProlongationContext
import com.bios.app.model.MedicationAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the QT-prolongation context helper (#204, CARDIOLOGY_POV §2.7).
 *
 * The helper produces a single-sentence footnote for the AFib screen / cardiac
 * rhythm pattern detail view when the owner's active medication list includes
 * a CredibleMeds Class 1 (Known Risk) QT-prolonging drug. Manifesto stance:
 * pull-side only, factual, prescriber-referral.
 *
 * Reference: CredibleMeds.org / Woosley et al. (2013) Drug Saf 36:1043–1055.
 */
class QtProlongationContextTest {

    private fun med(name: String, active: Boolean = true): MedicationAnnotation =
        MedicationAnnotation(
            name = name,
            startDate = 0L,
            endDate = if (active) null else 1L,
        )

    // ---- classifier ----

    @Test
    fun classifies_amiodarone_as_Class_1_Known_Risk() {
        assertEquals("amiodarone", QtProlongationContext.classifyKnownRisk("amiodarone"))
    }

    @Test
    fun classifier_is_case_insensitive() {
        assertEquals("amiodarone", QtProlongationContext.classifyKnownRisk("Amiodarone"))
        assertEquals("amiodarone", QtProlongationContext.classifyKnownRisk("AMIODARONE 200 mg"))
    }

    @Test
    fun classifier_handles_dose_suffix_in_annotation() {
        assertEquals(
            "methadone",
            QtProlongationContext.classifyKnownRisk("methadone 40mg qd"),
        )
    }

    @Test
    fun classifier_returns_null_for_non_Class_1_drug() {
        assertNull(QtProlongationContext.classifyKnownRisk("paracetamol"))
        assertNull(QtProlongationContext.classifyKnownRisk("Lipitor 10 mg"))
    }

    @Test
    fun classifier_uses_word_boundaries() {
        // "amiodaronefoo" should not match — word-boundary check prevents
        // false positives on look-alike free-text.
        assertNull(QtProlongationContext.classifyKnownRisk("amiodaronefoobar"))
    }

    @Test
    fun classifier_detects_macrolides() {
        assertEquals("azithromycin", QtProlongationContext.classifyKnownRisk("Azithromycin Z-pak"))
        assertEquals("clarithromycin", QtProlongationContext.classifyKnownRisk("clarithromycin 500"))
        assertEquals("erythromycin", QtProlongationContext.classifyKnownRisk("erythromycin tab"))
    }

    @Test
    fun classifier_detects_fluoroquinolones() {
        assertEquals("ciprofloxacin", QtProlongationContext.classifyKnownRisk("ciprofloxacin 500 mg bid"))
        assertEquals("moxifloxacin", QtProlongationContext.classifyKnownRisk("moxifloxacin"))
    }

    @Test
    fun classifier_detects_haloperidol_and_pimozide() {
        assertEquals("haloperidol", QtProlongationContext.classifyKnownRisk("haloperidol 2mg"))
        assertEquals("pimozide", QtProlongationContext.classifyKnownRisk("pimozide"))
    }

    @Test
    fun classifier_detects_citalopram_and_escitalopram() {
        assertEquals("citalopram", QtProlongationContext.classifyKnownRisk("citalopram 20mg"))
        assertEquals("escitalopram", QtProlongationContext.classifyKnownRisk("escitalopram"))
    }

    // ---- footnote ----

    @Test
    fun footnote_returns_null_when_no_Class_1_drug_active() {
        val meds = listOf(med("vitamin D3"), med("metformin"))
        assertNull(QtProlongationContext.footnoteFor(meds))
    }

    @Test
    fun footnote_returns_null_for_empty_medication_list() {
        assertNull(QtProlongationContext.footnoteFor(emptyList()))
    }

    @Test
    fun footnote_includes_drug_name_and_prescriber_referral() {
        val meds = listOf(med("amiodarone"))
        val text = QtProlongationContext.footnoteFor(meds)
        assertNotNull(text)
        assertTrue("footnote must name the drug", text!!.contains("amiodarone"))
        assertTrue(
            "footnote must mention QT-prolongation risk",
            text.lowercase().contains("qt"),
        )
        assertTrue(
            "footnote must direct owner to prescriber",
            text.lowercase().contains("prescriber"),
        )
    }

    @Test
    fun footnote_uses_owner_recorded_phrasing_for_drug_name() {
        // Owner wrote "Cordarone 200mg" (brand). Even though we match the
        // generic "amiodarone" via classifyKnownRisk... actually brand
        // names aren't in the v1 list, so this should fall through. The
        // generic-only owner annotation should be preserved verbatim.
        val meds = listOf(med("Amiodarone 200 mg morning"))
        val text = QtProlongationContext.footnoteFor(meds)
        assertNotNull(text)
        assertTrue(
            "footnote must reuse the owner's recorded annotation",
            text!!.contains("Amiodarone 200 mg morning"),
        )
    }

    @Test
    fun footnote_lists_multiple_active_Class_1_drugs() {
        val meds = listOf(med("amiodarone"), med("methadone 40mg"))
        val text = QtProlongationContext.footnoteFor(meds)
        assertNotNull(text)
        assertTrue(text!!.contains("amiodarone"))
        assertTrue(text.contains("methadone"))
    }

    // ---- AlertContentPolicy compliance ----

    @Test
    fun footnote_text_passes_AlertContentPolicy() {
        // The footnote ships as pull-side text but still must obey the
        // alert content policy — no second-person lifestyle judgments,
        // no guilt mechanics, no gamification. Build a representative
        // multi-drug footnote and run it through the same banned-phrase
        // list AlertContentPolicy uses.
        val meds = listOf(med("amiodarone"), med("haloperidol"), med("methadone 40mg"))
        val text = QtProlongationContext.footnoteFor(meds)!!
        val lower = text.lowercase()
        // The exact policy banned-phrase list — kept in sync with
        // AlertContentPolicy.PROHIBITED_PHRASES.
        val bannedPhrases = listOf(
            "you should", "you need to", "you must", "try to",
            "consider exercising", "you ought to", "you have to",
            "your health score", "grade:", "you're unhealthy",
            "you're doing great", "good job", "well done", "keep it up",
            "you haven't", "you missed", "you failed", "you forgot",
            "you didn't",
            "streak", "achievement", "level up", "points earned",
            "daily goal", "badge", "leaderboard", "ranking",
        )
        for (banned in bannedPhrases) {
            assertTrue(
                "footnote text contains banned phrase '$banned': $text",
                !lower.contains(banned),
            )
        }
    }

    // ---- list of active known-risk drugs ----

    @Test
    fun activeKnownRiskDrugs_lists_matched_generics() {
        val meds = listOf(
            med("amiodarone"),
            med("vitamin D"),
            med("methadone"),
            med("paracetamol"),
        )
        val list = QtProlongationContext.activeKnownRiskDrugs(meds)
        assertEquals(2, list.size)
        assertTrue(list.contains("amiodarone"))
        assertTrue(list.contains("methadone"))
    }

    @Test
    fun activeKnownRiskDrugs_empty_when_no_matches() {
        val meds = listOf(med("vitamin D"), med("paracetamol"))
        assertEquals(0, QtProlongationContext.activeKnownRiskDrugs(meds).size)
    }

    // ---- the curated list is reasonably populated ----

    @Test
    fun CredibleMeds_Class_1_list_contains_key_canonical_examples() {
        val list = QtProlongationContext.CREDIBLE_MEDS_CLASS_1_KNOWN_RISK
        // Canonical examples from CredibleMeds Known Risk (Woosley 2013).
        assertTrue("amiodarone is a canonical Class 1 example", "amiodarone" in list)
        assertTrue("haloperidol is a canonical Class 1 example", "haloperidol" in list)
        assertTrue("methadone is a canonical Class 1 example", "methadone" in list)
        assertTrue("dofetilide is a canonical Class 1 example", "dofetilide" in list)
        assertTrue("sotalol is a canonical Class 1 example", "sotalol" in list)
        assertTrue("citalopram is a canonical Class 1 example", "citalopram" in list)
    }
}
