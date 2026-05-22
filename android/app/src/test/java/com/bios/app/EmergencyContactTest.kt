package com.bios.app

import com.bios.app.data.EmergencyContactRepo
import com.bios.app.model.AlertTier
import com.bios.app.model.EmergencyContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the [EmergencyContact] entity defaults and the
 * [EmergencyContactRepo] clamping helpers (#179, EM/CC §2.1).
 *
 * Field renames or default-value drift here silently change which contacts
 * a fired URGENT alert surfaces — exactly the failure mode the audits flagged.
 */
class EmergencyContactTest {

    @Test
    fun default_min_tier_is_urgent() {
        val c = EmergencyContact(
            name = "n", relationship = "r", phoneNumber = "+15551234567",
        )
        assertEquals(EmergencyContact.MIN_TIER_URGENT, c.minTierLevel)
        assertEquals(AlertTier.URGENT.level, c.minTierLevel)
    }

    @Test
    fun default_ack_timeout_is_null_off_by_default() {
        // Manifesto: nothing escalates unless the owner opts in. A
        // brand-new contact carries a null timeout, which the worker
        // schedule path treats as "never escalate."
        val c = EmergencyContact(
            name = "n", relationship = "r", phoneNumber = "+15551234567",
        )
        assertNull(c.acknowledgementTimeoutMinutes)
    }

    @Test
    fun tier_clamp_below_advisory_clamps_to_advisory() {
        // Owner can pick URGENT-only or ADVISORY+URGENT. NOTICE and
        // OBSERVATION are explicitly excluded by the audit — escalation
        // is for alerts that matter.
        assertEquals(
            EmergencyContact.MIN_TIER_ADVISORY,
            EmergencyContactRepo.clampTier(0),
        )
        assertEquals(
            EmergencyContact.MIN_TIER_ADVISORY,
            EmergencyContactRepo.clampTier(1),
        )
        assertEquals(
            EmergencyContact.MIN_TIER_ADVISORY,
            EmergencyContactRepo.clampTier(2),
        )
    }

    @Test
    fun tier_clamp_above_urgent_clamps_to_urgent() {
        assertEquals(
            EmergencyContact.MIN_TIER_URGENT,
            EmergencyContactRepo.clampTier(99),
        )
    }

    @Test
    fun ack_timeout_null_passes_through() {
        assertNull(EmergencyContactRepo.clampTimeout(null))
    }

    @Test
    fun ack_timeout_below_minimum_clamps_up() {
        assertEquals(
            EmergencyContact.MIN_ACK_TIMEOUT_MIN,
            EmergencyContactRepo.clampTimeout(0),
        )
    }

    @Test
    fun ack_timeout_above_maximum_clamps_down() {
        assertEquals(
            EmergencyContact.MAX_ACK_TIMEOUT_MIN,
            EmergencyContactRepo.clampTimeout(10_000),
        )
    }

    @Test
    fun ack_timeout_in_range_passes_through() {
        assertEquals(15, EmergencyContactRepo.clampTimeout(15))
        assertEquals(60, EmergencyContactRepo.clampTimeout(60))
    }

    @Test
    fun ack_timeout_bounds_are_sane() {
        // Documented bounds — if either drifts, the UI hint text in the
        // Settings screen has to change too. Test pins the contract.
        assertEquals(1, EmergencyContact.MIN_ACK_TIMEOUT_MIN)
        assertEquals(120, EmergencyContact.MAX_ACK_TIMEOUT_MIN)
    }

    @Test
    fun contact_preserves_phone_number_verbatim_on_copy() {
        // Phone-number normalization is explicitly out of scope (the
        // owner knows what reaches their person). copy() must not mutate.
        val original = EmergencyContact(
            name = "n", relationship = "r", phoneNumber = "(555) 123-4567",
        )
        val edited = original.copy(name = "n2")
        assertEquals("(555) 123-4567", edited.phoneNumber)
    }

    @Test
    fun ack_timeout_set_non_null_is_round_tripped() {
        val c = EmergencyContact(
            name = "n", relationship = "r", phoneNumber = "+15551234567",
            acknowledgementTimeoutMinutes = 30,
        )
        assertNotNull(c.acknowledgementTimeoutMinutes)
        assertEquals(30, c.acknowledgementTimeoutMinutes)
    }
}
