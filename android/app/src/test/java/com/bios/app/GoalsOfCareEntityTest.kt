package com.bios.app

import com.bios.app.model.ClinicalDirective
import com.bios.app.model.CprPreference
import com.bios.app.model.GoalsOfCare
import com.bios.app.model.HospitalizationPreference
import com.bios.app.model.InterventionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the [GoalsOfCare] and [ClinicalDirective] entity shapes (#184).
 *
 * Field renames, default-value changes, or enum reorderings would
 * silently change which alerts auto-escalate for which owners. Both
 * entities are read by the AlertManager when deciding whether to
 * short-circuit URGENT-tier escalation — a regression here would
 * either fail to honor the owner's declared preference, or
 * over-suppress alerts when no preference is set.
 */
class GoalsOfCareEntityTest {

    // -- GoalsOfCare --

    @Test
    fun goals_default_constructor_uses_singleton_id() {
        assertEquals(GoalsOfCare.SINGLETON_ID, GoalsOfCare().id)
        assertEquals(1L, GoalsOfCare.SINGLETON_ID)
    }

    @Test
    fun goals_default_values_are_unspecified() {
        // Empty defaults so a fresh owner = no declared preference.
        // The AlertManager reads UNSPECIFIED as "owner hasn't declared"
        // and applies default escalation behaviour.
        val g = GoalsOfCare()
        assertEquals(CprPreference.UNSPECIFIED, g.cprPreference)
        assertEquals(HospitalizationPreference.UNSPECIFIED, g.hospitalizationPreference)
        assertEquals(InterventionLevel.UNSPECIFIED, g.interventionLevel)
        assertNull(g.documentLocation)
        assertNull(g.notes)
    }

    @Test
    fun goals_copy_preserves_singleton_id() {
        val edited = GoalsOfCare().copy(interventionLevel = InterventionLevel.COMFORT_ONLY)
        assertEquals(GoalsOfCare.SINGLETON_ID, edited.id)
    }

    @Test
    fun goals_lastReviewedAt_advances_with_explicit_copy() {
        val a = GoalsOfCare(lastReviewedAt = 1_000L)
        val b = a.copy(lastReviewedAt = 2_000L)
        assertNotEquals(a.lastReviewedAt, b.lastReviewedAt)
        assertTrue(b.lastReviewedAt > a.lastReviewedAt)
    }

    @Test
    fun cpr_preference_enum_includes_all_documented_values() {
        // Pinning the enum surface — DNAR_OK_TO_INTUBATE is the rare
        // option called out by the audit; if it's removed by mistake
        // the test catches it.
        val values = CprPreference.entries.map { it.name }.toSet()
        assertTrue(values.contains("UNSPECIFIED"))
        assertTrue(values.contains("FULL_CODE"))
        assertTrue(values.contains("DNAR_DNI"))
        assertTrue(values.contains("DNAR_OK_TO_INTUBATE"))
    }

    @Test
    fun hospitalization_preference_enum_includes_all_documented_values() {
        val values = HospitalizationPreference.entries.map { it.name }.toSet()
        assertTrue(values.contains("UNSPECIFIED"))
        assertTrue(values.contains("ANY"))
        assertTrue(values.contains("COMFORT_FOCUSED"))
        assertTrue(values.contains("AVOID_UNLESS_PAINFUL"))
        assertTrue(values.contains("NONE"))
    }

    @Test
    fun intervention_level_enum_includes_comfort_only() {
        // COMFORT_ONLY is the signal AlertManager reads to short-
        // circuit URGENT escalation; removing it would silently
        // disable that gate.
        val values = InterventionLevel.entries.map { it.name }.toSet()
        assertTrue(values.contains("UNSPECIFIED"))
        assertTrue(values.contains("FULL"))
        assertTrue(values.contains("SELECTIVE"))
        assertTrue(values.contains("COMFORT_ONLY"))
    }

    // -- ClinicalDirective --

    @Test
    fun directive_default_constructor_uses_singleton_id() {
        assertEquals(ClinicalDirective.SINGLETON_ID, ClinicalDirective().id)
        assertEquals(1L, ClinicalDirective.SINGLETON_ID)
    }

    @Test
    fun directive_default_values_are_false_or_null() {
        val d = ClinicalDirective()
        assertFalse(d.hasAdvanceDirective)
        assertFalse(d.hasPolst)
        assertFalse(d.hasHealthcareProxy)
        assertNull(d.proxyContactName)
        assertNull(d.proxyContactPhone)
    }

    @Test
    fun directive_copy_preserves_singleton_id() {
        val edited = ClinicalDirective().copy(hasAdvanceDirective = true)
        assertEquals(ClinicalDirective.SINGLETON_ID, edited.id)
    }

    @Test
    fun directive_updatedAt_advances_with_explicit_copy() {
        val a = ClinicalDirective(updatedAt = 1_000L)
        val b = a.copy(hasPolst = true, updatedAt = 2_000L)
        assertNotEquals(a.updatedAt, b.updatedAt)
        assertTrue(b.updatedAt > a.updatedAt)
    }
}
