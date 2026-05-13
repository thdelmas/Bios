package com.bios.app

import com.bios.app.model.MetricDomain
import com.bios.app.model.MetricType
import com.bios.app.model.MetricUnit
import com.bios.app.provider.CompanionContract
import org.junit.Assert.assertEquals
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
}
