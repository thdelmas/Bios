package com.bios.app

import com.bios.app.data.BiosDatabaseMigrationsExtras
import com.bios.app.model.AllergyRecord
import com.bios.app.model.AllergySeverity
import com.bios.app.model.ReactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec test for the AllergyRecord entity shape and its v33 -> v34 migration.
 * Issue #355.
 */
class AllergyRecordTest {

    @Test
    fun `allergy record defaults are conservative`() {
        val r = AllergyRecord(substance = "penicillin")
        // Unknown reactionType + severity keep Bios from making clinical
        // claims the owner didn't make — recording is not endorsement.
        assertEquals(ReactionType.UNKNOWN, r.reactionType)
        assertEquals(AllergySeverity.UNKNOWN, r.severity)
        // Coding fields are opt-in.
        assertNull(r.substanceCode)
        assertNull(r.substanceSource)
        assertNull(r.manifestation)
        assertNull(r.onsetDate)
        assertNull(r.note)
        // Owner-asserted is the default; verifiedByClinician is purely
        // informational and stays false unless explicitly set.
        assertEquals(false, r.verifiedByClinician)
    }

    @Test
    fun `allergy record carries full clinical context when provided`() {
        // Sagrat Cor discharge case: "Penicillin allergy with anaphylaxis
        // in 2018" — Bios stores what the owner says, never adjudicates.
        val r = AllergyRecord(
            substance = "penicillin",
            substanceCode = "7980",
            substanceSource = "RxNorm",
            reactionType = ReactionType.ALLERGY,
            severity = AllergySeverity.LIFE_THREATENING,
            manifestation = "throat tightness, urticaria",
            onsetDate = 1514764800000L, // 2018-01-01
            verifiedByClinician = true,
            note = "EpiPen carried at all times",
        )
        assertEquals("penicillin", r.substance)
        assertEquals(ReactionType.ALLERGY, r.reactionType)
        assertEquals(AllergySeverity.LIFE_THREATENING, r.severity)
        assertTrue(r.verifiedByClinician)
    }

    @Test
    fun `MIGRATION_33_34 covers the right schema versions`() {
        val m = BiosDatabaseMigrationsExtras.MIGRATION_33_34
        assertEquals(33, m.startVersion)
        assertEquals(34, m.endVersion)
    }

    @Test
    fun `reaction type and severity enums cover the owner's recording vocabulary`() {
        // The picker UI uses these enum entries verbatim. Renaming any
        // of them would invalidate existing rows (Room persists the name
        // string), so this test pins the values.
        assertEquals(
            setOf("ALLERGY", "INTOLERANCE", "SIDE_EFFECT", "UNKNOWN"),
            ReactionType.entries.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("MILD", "MODERATE", "SEVERE", "LIFE_THREATENING", "UNKNOWN"),
            AllergySeverity.entries.map { it.name }.toSet(),
        )
    }
}
