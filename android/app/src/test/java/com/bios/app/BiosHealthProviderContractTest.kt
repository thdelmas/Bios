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
    fun `W2F may write sleep_duration derived from screen-off (issue 133)`() {
        // W2F's UsageStatsTracker is the unique screen-off producer surface.
        // Without this entry, LETHE owners without a wearable see an empty
        // sleep bus despite W2F holding the derivation locally.
        assertTrue(
            "W2F screen-off sleep must be writable to keep SLEEP_DURATION canonical",
            "sleep_duration" in CompanionContract.WRITABLE_METRICS
        )
        assertTrue(CompanionContract.canWrite("com.w2f.app", "sleep_duration"))
        // Cross-package isolation — only W2F may write the screen-off
        // estimate; other companions have no legitimate sleep producer.
        assertFalse(CompanionContract.canWrite("com.smokless.smokeless", "sleep_duration"))
        assertFalse(CompanionContract.canWrite("com.virgil.app", "sleep_duration"))
        assertFalse(CompanionContract.canWrite("com.unknown.app", "sleep_duration"))
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
    fun `W2F may only write its own mental-health and sleep-derivation keys`() {
        val w2f = "com.w2f.app"
        assertTrue(CompanionContract.canWrite(w2f, "typing_cadence"))
        assertTrue(CompanionContract.canWrite(w2f, "mood_drift_score"))
        assertTrue(CompanionContract.canWrite(w2f, "circadian_phase_shift"))
        assertTrue(CompanionContract.canWrite(w2f, "sleep_duration"))
        // Cross-package isolation — W2F must not write Smokeless keys
        assertFalse(CompanionContract.canWrite(w2f, "tobacco_use"))
        assertFalse(CompanionContract.canWrite(w2f, "tobacco_craving"))
    }

    @Test
    fun `W2F may write caffeine_intake from FuelLog (issue 136)`() {
        // W2F's FuelLog is the unique caffeine-capture surface. Without
        // this entry, the concentration engine has no upstream producer
        // and the dashboard sits empty even when W2F holds the doses.
        assertTrue(
            "caffeine_intake must be writable to feed the concentration engine",
            "caffeine_intake" in CompanionContract.WRITABLE_METRICS
        )
        assertTrue(CompanionContract.canWrite("com.w2f.app", "caffeine_intake"))
        // Cross-package isolation — only W2F's FuelLog is the producer.
        assertFalse(CompanionContract.canWrite("com.smokless.smokeless", "caffeine_intake"))
        assertFalse(CompanionContract.canWrite("com.virgil.app", "caffeine_intake"))
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
    fun `reaction_time_ms is reserved but not writable by any companion`() {
        // Decision 5 of SELF_REPORTED_DATA_HOME: pre-register active-test keys
        // before a second consumer ships, so both apps land on the same shape.
        // The key must round-trip through MetricType but stay out of every
        // companion's writableMetrics until a real producer is wired up.
        assertNotNull(MetricType.fromKey("reaction_time_ms"))
        assertFalse(
            "reaction_time_ms must remain unwritable until a producer is whitelisted",
            "reaction_time_ms" in CompanionContract.WRITABLE_METRICS
        )
        for (pkg in CompanionContract.PACKAGES.keys) {
            assertFalse(
                "$pkg must not be able to write reserved key reaction_time_ms",
                CompanionContract.canWrite(pkg, "reaction_time_ms")
            )
        }
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

    @Test
    fun `payload path constant matches the documented URI shape`() {
        // Companions that read composite events build URIs by appending the
        // parent reading id to PATH_PAYLOAD. Renames break every reader.
        assertEquals("payload", com.bios.contracts.BiosHealthContract.PATH_PAYLOAD)
    }

    @Test
    fun `payload column projection exposes the three typed value columns`() {
        val cols = com.bios.contracts.BiosHealthContract.PAYLOAD_COLUMNS
        assertEquals(5, cols.size)
        assertTrue("reading_id" in cols)
        assertTrue("field_key" in cols)
        // Three typed columns — readers query the one matching the field's
        // documented type rather than parsing a stringly-typed blob.
        assertTrue("string_value" in cols)
        assertTrue("double_value" in cols)
        assertTrue("long_value" in cols)
    }

    // ---- Cert-SHA pinning (issue #90) ----

    @Test
    fun `empty cert pin is the current migration-window default`() {
        // Until each companion ships a release-signing key, the pin stays empty
        // and the package-name check stands alone. Treat this as a transitional
        // contract — populate signingCertSha256 before the first standalone
        // public release.
        for (c in CompanionContract.PACKAGES.values) {
            assertTrue(
                "${c.packageName} unexpectedly has a non-empty cert pin — update test if intentional",
                c.signingCertSha256.isEmpty()
            )
        }
    }

    @Test
    fun `empty pin accepts any cert (migration-window fallback)`() {
        val companion = w2fWithPin(emptySet())
        assertTrue(companion.verifySigningCert("anything"))
        assertTrue(companion.verifySigningCert(null))
        assertTrue(companion.verifySigningCert(""))
    }

    @Test
    fun `non-empty pin accepts the matching SHA`() {
        val sha = "a".repeat(64)
        val companion = w2fWithPin(setOf(sha))
        assertTrue(companion.verifySigningCert(sha))
    }

    @Test
    fun `non-empty pin rejects a non-matching SHA`() {
        val pinned = "a".repeat(64)
        val impostor = "b".repeat(64)
        val companion = w2fWithPin(setOf(pinned))
        assertFalse(companion.verifySigningCert(impostor))
    }

    @Test
    fun `non-empty pin rejects null caller cert`() {
        // Null means we couldn't read the caller's signing info — a malicious
        // unsigned APK is the realistic case. Reject hard.
        val companion = w2fWithPin(setOf("a".repeat(64)))
        assertFalse(companion.verifySigningCert(null))
    }

    @Test
    fun `cert comparison is case-insensitive`() {
        val mixed = "AbCdEf" + "0".repeat(58)
        val companion = w2fWithPin(setOf(mixed))
        assertTrue(companion.verifySigningCert(mixed.lowercase()))
        assertTrue(companion.verifySigningCert(mixed.uppercase()))
    }

    @Test
    fun `rotation window accepts any of the pinned SHAs`() {
        val oldKey = "a".repeat(64)
        val newKey = "b".repeat(64)
        val companion = w2fWithPin(setOf(oldKey, newKey))
        assertTrue(companion.verifySigningCert(oldKey))
        assertTrue(companion.verifySigningCert(newKey))
        assertFalse(companion.verifySigningCert("c".repeat(64)))
    }

    @Test
    fun `verifySigningCert via object lookup rejects unknown packages`() {
        // Unknown package can't be impersonating any known companion — the
        // package-name gate already rejects it; the cert path agrees.
        assertFalse(CompanionContract.verifySigningCert("com.unknown.app", "any"))
        assertFalse(CompanionContract.verifySigningCert(null, "any"))
    }

    @Test
    fun `verifySigningCert via object lookup delegates to the matching companion`() {
        // With the current empty-pin migration default, every known package
        // accepts any cert via the object-level entry point.
        for (pkg in CompanionContract.PACKAGES.keys) {
            assertTrue(
                "$pkg should accept under migration-window fallback",
                CompanionContract.verifySigningCert(pkg, "any")
            )
        }
    }

    private fun w2fWithPin(pin: Set<String>) = CompanionContract.Companion(
        packageName = "com.w2f.app",
        displayName = "W2F",
        sourceId = "companion_w2f",
        writableMetrics = setOf("typing_cadence"),
        defaultReadingKind = ReadingKind.DERIVED,
        signingCertSha256 = pin,
    )
}
