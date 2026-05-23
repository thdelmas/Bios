package com.bios.app

import com.bios.app.config.RegionConfigProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Guards the `emergencyNumber` field on [com.bios.app.config.RegionConfig]
 * for all six built-in regions (#179, EM/CC §2.1).
 *
 * A typo or accidental null here is a clinical bug: it silently strips the
 * tap-to-call action off the URGENT notification. CI must catch this.
 */
class RegionConfigEmergencyNumberTest {

    @Test
    fun us_emergency_number_is_911() {
        assertEquals("911", RegionConfigProvider.forRegion("US").emergencyNumber)
    }

    @Test
    fun ca_emergency_number_is_911() {
        assertEquals("911", RegionConfigProvider.forRegion("CA").emergencyNumber)
    }

    @Test
    fun gb_emergency_number_is_999() {
        assertEquals("999", RegionConfigProvider.forRegion("GB").emergencyNumber)
    }

    @Test
    fun eu_emergency_number_is_112() {
        assertEquals("112", RegionConfigProvider.forRegion("EU").emergencyNumber)
    }

    @Test
    fun au_emergency_number_is_000() {
        assertEquals("000", RegionConfigProvider.forRegion("AU").emergencyNumber)
    }

    @Test
    fun jp_emergency_number_is_119() {
        // 119 dispatches Japanese ambulance / fire. Police is 110;
        // the audit specifies the medical-emergency number for tap-to-call.
        assertEquals("119", RegionConfigProvider.forRegion("JP").emergencyNumber)
    }

    @Test
    fun every_supported_region_carries_an_emergency_number() {
        // Catches: a future region added without emergencyNumber populated.
        // No supported region should ship with a null — the field is
        // nullable for *unsupported* (fallback) locales only.
        for (code in RegionConfigProvider.supportedRegions()) {
            val cfg = RegionConfigProvider.forRegion(code)
            assertNotNull(
                "Region $code is missing emergencyNumber",
                cfg.emergencyNumber,
            )
        }
    }
}
