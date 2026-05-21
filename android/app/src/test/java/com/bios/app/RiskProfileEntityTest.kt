package com.bios.app

import com.bios.app.model.RiskProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the [RiskProfile] entity shape. The screening-cadence engine
 * (#155) and the future pattern-explanation builder read this surface;
 * field renames or default-value changes would silently shift which
 * screenings the engine adjusts for which owners.
 */
class RiskProfileEntityTest {

    @Test
    fun default_constructor_uses_singleton_id() {
        // Single-row table. Every save must target SINGLETON_ID so the
        // upsert (REPLACE on conflict) always overwrites the previous row.
        assertEquals(RiskProfile.SINGLETON_ID, RiskProfile().id)
        assertEquals(1L, RiskProfile.SINGLETON_ID)
    }

    @Test
    fun default_profile_has_no_risk_flags_set() {
        // Empty defaults so a fresh owner's profile = no claimed risk
        // factors. The engine reads "false" as "owner didn't say yes"
        // rather than "owner said no" — same behavioral effect.
        val p = RiskProfile()
        assertFalse(p.firstDegreeCadEarly)
        assertFalse(p.firstDegreeBreastOvarianCancer)
        assertFalse(p.firstDegreeColorectalCancer)
        assertFalse(p.firstDegreeDiabetes)
        assertFalse(p.firstDegreeOsteoporosisHipFracture)
        assertFalse(p.firstDegreeMelanoma)
    }

    @Test
    fun default_profile_has_null_optional_fields() {
        val p = RiskProfile()
        assertNull(p.personalTobaccoYears)
        assertNull(p.personalTobaccoPackYears)
        assertNull(p.personalTobaccoQuitDate)
        assertNull(p.personalCancerHistory)
        assertNull(p.personalCardiacEventHistory)
        assertNull(p.note)
    }

    @Test
    fun updatedAt_advances_when_a_copy_is_made_with_a_later_time() {
        val a = RiskProfile(updatedAt = 1_000L)
        val b = a.copy(firstDegreeCadEarly = true, updatedAt = 2_000L)
        assertNotEquals(a.updatedAt, b.updatedAt)
        assertTrue(b.updatedAt > a.updatedAt)
    }

    @Test
    fun copy_preserves_singleton_id() {
        // Even when callers .copy() the entity (UI edit flows), the id
        // stays at SINGLETON_ID — the repo's save() further enforces
        // this on the persistence side.
        val edited = RiskProfile().copy(firstDegreeCadEarly = true)
        assertEquals(RiskProfile.SINGLETON_ID, edited.id)
    }
}
