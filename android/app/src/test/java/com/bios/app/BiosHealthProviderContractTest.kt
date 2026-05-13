package com.bios.app

import com.bios.app.model.MetricDomain
import com.bios.app.model.MetricType
import com.bios.app.model.MetricUnit
import com.bios.app.provider.CompanionContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the companion-write contract exposed by BiosHealthProvider.
 *
 * The whitelist is the public surface companion apps (W2F, Smokeless) compile
 * against — silently dropping or renaming a key breaks them in the field with
 * no compile-time signal. This test fails loudly when the contract changes.
 */
class BiosHealthProviderContractTest {

    @Test
    fun `companion whitelist contains the documented mental-health keys`() {
        assertTrue("typing_cadence" in CompanionContract.WRITABLE_METRICS)
        assertTrue("circadian_phase_shift" in CompanionContract.WRITABLE_METRICS)
        assertTrue("mood_drift_score" in CompanionContract.WRITABLE_METRICS)
    }

    @Test
    fun `companion whitelist contains Smokeless substance-use keys`() {
        assertTrue(
            "Smokeless tobacco_use must be writable via companion URI",
            "tobacco_use" in CompanionContract.WRITABLE_METRICS
        )
        assertTrue(
            "Smokeless tobacco_craving must be writable via companion URI",
            "tobacco_craving" in CompanionContract.WRITABLE_METRICS
        )
    }

    @Test
    fun `cannabis keys are reserved but not yet whitelisted (YAGNI)`() {
        // Per docs/ECOSYSTEM_BOUNDARIES.md: cannabis keys are reserved in the
        // ROADMAP but not whitelisted until Smokeless actually ships
        // multi-substance support.
        assertTrue("cannabis_use" !in CompanionContract.WRITABLE_METRICS)
        assertTrue("cannabis_craving" !in CompanionContract.WRITABLE_METRICS)
    }

    @Test
    fun `every whitelisted key resolves to a known MetricType`() {
        for (key in CompanionContract.WRITABLE_METRICS) {
            assertTrue("$key is whitelisted but not in MetricType",
                MetricType.fromKey(key) != null)
        }
    }

    @Test
    fun `tobacco metric types are INTAKE domain with EVENT unit`() {
        assertEquals(MetricDomain.INTAKE, MetricType.TOBACCO_USE.domain)
        assertEquals(MetricUnit.EVENT, MetricType.TOBACCO_USE.unit)
        assertEquals(MetricDomain.INTAKE, MetricType.TOBACCO_CRAVING.domain)
        assertEquals(MetricUnit.EVENT, MetricType.TOBACCO_CRAVING.unit)
    }

    @Test
    fun `W2F may only write its own mental-health keys`() {
        val w2f = "com.w2f.app"
        assertTrue(CompanionContract.canWrite(w2f, "typing_cadence"))
        assertTrue(CompanionContract.canWrite(w2f, "mood_drift_score"))
        assertTrue(CompanionContract.canWrite(w2f, "circadian_phase_shift"))
        // Cross-package isolation — W2F must not write Smokeless keys
        assertFalse(CompanionContract.canWrite(w2f, "tobacco_use"))
        assertFalse(CompanionContract.canWrite(w2f, "tobacco_craving"))
    }

    @Test
    fun `Smokeless may only write its own intake keys`() {
        val sml = "com.smokless.smokeless"
        assertTrue(CompanionContract.canWrite(sml, "tobacco_use"))
        assertTrue(CompanionContract.canWrite(sml, "tobacco_craving"))
        // Cross-package isolation — Smokeless must not write W2F keys
        assertFalse(CompanionContract.canWrite(sml, "mood_drift_score"))
        assertFalse(CompanionContract.canWrite(sml, "typing_cadence"))
    }

    @Test
    fun `unknown packages cannot write any whitelisted key`() {
        for (key in CompanionContract.WRITABLE_METRICS) {
            assertFalse(
                "'com.unknown.app' must not be able to write '$key'",
                CompanionContract.canWrite("com.unknown.app", key)
            )
        }
        assertFalse(CompanionContract.canWrite(null, "tobacco_use"))
    }
}
