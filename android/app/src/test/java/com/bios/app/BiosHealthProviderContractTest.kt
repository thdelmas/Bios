package com.bios.app

import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import com.bios.app.model.ReadingKind
import com.bios.app.provider.CompanionContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `companion whitelist contains Smokeless cannabis keys (Phase 2_1)`() {
        // Smokeless persists Substance.{TOBACCO,CANNABIS} on each session and
        // routes cannabis events to cannabis_use / cannabis_craving — both keys
        // must be writable for the per-substance routing to land in Bios.
        assertTrue("cannabis_use" in CompanionContract.WRITABLE_METRICS)
        assertTrue("cannabis_craving" in CompanionContract.WRITABLE_METRICS)
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
    fun `cannabis metric types are INTAKE domain with EVENT unit`() {
        assertEquals(MetricDomain.INTAKE, MetricType.CANNABIS_USE.domain)
        assertEquals(MetricUnit.EVENT, MetricType.CANNABIS_USE.unit)
        assertEquals(MetricDomain.INTAKE, MetricType.CANNABIS_CRAVING.domain)
        assertEquals(MetricUnit.EVENT, MetricType.CANNABIS_CRAVING.unit)
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
        assertTrue(CompanionContract.canWrite(sml, "cannabis_use"))
        assertTrue(CompanionContract.canWrite(sml, "cannabis_craving"))
        // Cross-package isolation — Smokeless must not write W2F or Virgil keys
        assertFalse(CompanionContract.canWrite(sml, "mood_drift_score"))
        assertFalse(CompanionContract.canWrite(sml, "typing_cadence"))
        assertFalse(CompanionContract.canWrite(sml, "fall_event"))
    }

    @Test
    fun `Virgil may only write its own safety keys`() {
        val v = "com.virgil.app"
        assertTrue(CompanionContract.canWrite(v, "fall_event"))
        assertTrue(CompanionContract.canWrite(v, "near_miss_fall"))
        assertTrue(CompanionContract.canWrite(v, "check_in_miss"))
        // Cross-package isolation — Virgil must not write W2F or Smokeless keys
        assertFalse(CompanionContract.canWrite(v, "mood_drift_score"))
        assertFalse(CompanionContract.canWrite(v, "tobacco_use"))
    }

    @Test
    fun `safety metric types are SAFETY domain with EVENT unit`() {
        assertEquals(MetricDomain.SAFETY, MetricType.FALL_EVENT.domain)
        assertEquals(MetricUnit.EVENT, MetricType.FALL_EVENT.unit)
        assertEquals(MetricDomain.SAFETY, MetricType.NEAR_MISS_FALL.domain)
        assertEquals(MetricUnit.EVENT, MetricType.NEAR_MISS_FALL.unit)
        assertEquals(MetricDomain.SAFETY, MetricType.CHECK_IN_MISS.domain)
        assertEquals(MetricUnit.EVENT, MetricType.CHECK_IN_MISS.unit)
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

    @Test
    fun `each companion gets a distinct sourceId for provenance`() {
        val w2f = CompanionContract.sourceFor("com.w2f.app")
        val sml = CompanionContract.sourceFor("com.smokless.smokeless")
        assertNotNull(w2f)
        assertNotNull(sml)
        assertEquals("W2F", w2f!!.displayName)
        assertEquals("Smokeless", sml!!.displayName)
        assertNotEquals("Companions must not share a sourceId — provenance would collapse",
            w2f.sourceId, sml.sourceId)
    }

    @Test
    fun `sourceFor returns null for unknown or null packages`() {
        assertNull(CompanionContract.sourceFor("com.unknown.app"))
        assertNull(CompanionContract.sourceFor(null))
    }

    @Test
    fun `Smokeless companion source is tagged SELF_REPORTED`() {
        // Owner-logged tobacco/cannabis events are self-reports, never sensor
        // streams. BaselineEngine must skip these (decision 3 in
        // SELF_REPORTED_DATA_HOME) — that filter keys off readingKind.
        val sml = CompanionContract.sourceFor("com.smokless.smokeless")!!
        assertEquals(ReadingKind.SELF_REPORTED, sml.defaultReadingKind)
    }

    @Test
    fun `W2F and Virgil companion sources are tagged DERIVED`() {
        // W2F/Virgil write algorithmic outputs (cadence, drift, miss
        // detection), not raw owner logs. DERIVED is also excluded from
        // BaselineEngine — but for a different reason (already smoothed).
        val w2f = CompanionContract.sourceFor("com.w2f.app")!!
        val virgil = CompanionContract.sourceFor("com.virgil.app")!!
        assertEquals(ReadingKind.DERIVED, w2f.defaultReadingKind)
        assertEquals(ReadingKind.DERIVED, virgil.defaultReadingKind)
    }
}
