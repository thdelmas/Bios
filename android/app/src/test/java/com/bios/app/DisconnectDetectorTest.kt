package com.bios.app

import com.bios.app.alerts.DisconnectDetector
import com.bios.app.alerts.PushDecision
import com.bios.app.alerts.SkipReason
import com.bios.app.alerts.decidePush
import com.bios.app.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Deterministic trigger-contract tests for [DisconnectDetector] (#113).
 *
 * Three gates govern the trigger: previously-active, currently-stale,
 * past-cool-down. Each test isolates one gate. The full surface (DB
 * reads, NotificationManager, PendingIntent plumbing) requires Android
 * runtime and is covered by build-and-eyeball; the deterministic part
 * is what fails first under regressions to the contract.
 */
class DisconnectDetectorTest {

    private val now: Long = 1_733_400_000_000L // arbitrary fixed "now"
    private val day: Long = TimeUnit.DAYS.toMillis(1)

    // --- Gate 1: previously active ---

    @Test
    fun `source with too few readings is skipped as NEVER_ACTIVE even when very stale`() {
        // A briefly-connected source that produced a handful of readings then
        // dropped off is not "previously active." Pushing about a one-off
        // pairing the owner abandoned would be noise.
        val decision = decidePush(
            sourceType = SourceType.OURA_API,
            readingCount = DisconnectDetector.MIN_READINGS_FOR_ACTIVE - 1,
            lastReadingAt = now - 30 * day,
            lastPushedAt = 0L,
            now = now,
        )
        assertSkipped(decision, SkipReason.NEVER_ACTIVE)
    }

    @Test
    fun `source exactly at the active-threshold count is eligible`() {
        // Boundary check — `≥ MIN_READINGS_FOR_ACTIVE` not `>`.
        val decision = decidePush(
            sourceType = SourceType.OURA_API,
            readingCount = DisconnectDetector.MIN_READINGS_FOR_ACTIVE,
            lastReadingAt = now - 10 * day,
            lastPushedAt = 0L,
            now = now,
        )
        assertTrue("source at exactly the threshold should push", decision.push)
    }

    // --- Gate 2: currently stale (with per-source-type thresholds) ---

    @Test
    fun `OAuth source stale just over 5 days pushes`() {
        // OAuth-backed sources (Oura / Withings / WHOOP / Garmin / Dexcom /
        // Polar) get the tighter 5-day window — token-expiry is the
        // dominant failure mode.
        val decision = decidePush(
            sourceType = SourceType.OURA_API,
            readingCount = 100,
            lastReadingAt = now - (5 * day + 1),
            lastPushedAt = 0L,
            now = now,
        )
        assertTrue(decision.push)
    }

    @Test
    fun `OAuth source stale just under 5 days is NOT_STALE`() {
        // One-millisecond-under-the-threshold doesn't push. The 5-day window
        // exists so a single missed sync doesn't fire — only persistent
        // disconnects do.
        val decision = decidePush(
            sourceType = SourceType.OURA_API,
            readingCount = 100,
            lastReadingAt = now - (5 * day - 1),
            lastPushedAt = 0L,
            now = now,
        )
        assertSkipped(decision, SkipReason.NOT_STALE)
    }

    @Test
    fun `passive source stale at 6 days is NOT_STALE (uses 7-day window)`() {
        // Health Connect / Gadgetbridge get the looser 7-day window. Their
        // failure modes are slower and less owner-actionable (no token to
        // re-grant), so the bar to push is higher.
        val decision = decidePush(
            sourceType = SourceType.HEALTH_CONNECT,
            readingCount = 100,
            lastReadingAt = now - 6 * day,
            lastPushedAt = 0L,
            now = now,
        )
        assertSkipped(decision, SkipReason.NOT_STALE)
    }

    @Test
    fun `passive source stale at 8 days pushes`() {
        val decision = decidePush(
            sourceType = SourceType.HEALTH_CONNECT,
            readingCount = 100,
            lastReadingAt = now - 8 * day,
            lastPushedAt = 0L,
            now = now,
        )
        assertTrue(decision.push)
    }

    @Test
    fun `OAuth + passive thresholds are actually different`() {
        // Anti-regression: this would have caught me lazily reusing the
        // OAuth threshold for everything.
        assertTrue(
            "OAuth threshold must be tighter than passive",
            DisconnectDetector.OAUTH_STALE_THRESHOLD_MILLIS <
                DisconnectDetector.PASSIVE_STALE_THRESHOLD_MILLIS,
        )
    }

    // --- Gate 3: cool-down ---

    @Test
    fun `recently-pushed source is IN_COOLDOWN even when persistently stale`() {
        // The cool-down is the *de-nag* guarantee — owner gets one push
        // per disconnected source per 7 days, no matter how many sync
        // cycles fire in between.
        val decision = decidePush(
            sourceType = SourceType.OURA_API,
            readingCount = 100,
            lastReadingAt = now - 30 * day,
            lastPushedAt = now - 1 * day,
            now = now,
        )
        assertSkipped(decision, SkipReason.IN_COOLDOWN)
    }

    @Test
    fun `cool-down expires after the documented window`() {
        val decision = decidePush(
            sourceType = SourceType.OURA_API,
            readingCount = 100,
            lastReadingAt = now - 30 * day,
            lastPushedAt = now - (DisconnectDetector.COOLDOWN_MILLIS + 1),
            now = now,
        )
        assertTrue(decision.push)
    }

    @Test
    fun `never-pushed source (lastPushedAt = 0) clears cool-down regardless of clock`() {
        // A fresh install: the prefs value is the SharedPreferences default
        // of 0L. Don't trip on "0L was a long time ago" arithmetic.
        val decision = decidePush(
            sourceType = SourceType.OURA_API,
            readingCount = 100,
            lastReadingAt = now - 10 * day,
            lastPushedAt = 0L,
            now = now,
        )
        assertTrue(decision.push)
    }

    @Test
    fun `negative lastPushedAt is treated as never-pushed, not as a future timestamp`() {
        // Defensive: corrupted prefs shouldn't soft-lock the surface.
        val decision = decidePush(
            sourceType = SourceType.OURA_API,
            readingCount = 100,
            lastReadingAt = now - 10 * day,
            lastPushedAt = -1L,
            now = now,
        )
        assertTrue(decision.push)
    }

    // --- Gate ordering / interaction ---

    @Test
    fun `gate ordering — not-active beats not-stale beats cool-down`() {
        // Skip reasons are diagnostic — they tell us *why* a source didn't
        // push, which matters for logging and future tuning. The ordering
        // must be stable so log-mining doesn't lie about prevalence.
        val notActive = decidePush(
            sourceType = SourceType.OURA_API,
            readingCount = 0,
            lastReadingAt = now - 30 * day,
            lastPushedAt = now - 1 * day,
            now = now,
        )
        assertEquals(SkipReason.NEVER_ACTIVE, notActive.reason)

        val notStale = decidePush(
            sourceType = SourceType.OURA_API,
            readingCount = 100,
            lastReadingAt = now - 1 * day,
            lastPushedAt = now - 1 * day,
            now = now,
        )
        assertEquals(SkipReason.NOT_STALE, notStale.reason)
    }

    @Test
    fun `pushable-source-type set excludes SELF_REPORTED and in-process sources`() {
        // SELF_REPORTED: the owner decides cadence, not Bios.
        // CAMERA_PPG: one-shot capture surface, not a continuous source.
        // DIRECT_SENSOR / PHONE_SENSOR: in-process — when these stop, the
        // whole app has stopped and no notification is going to fire anyway.
        assertFalse(SourceType.SELF_REPORTED in DisconnectDetector.PUSHABLE_SOURCE_TYPES)
        assertFalse(SourceType.CAMERA_PPG in DisconnectDetector.PUSHABLE_SOURCE_TYPES)
        assertFalse(SourceType.DIRECT_SENSOR in DisconnectDetector.PUSHABLE_SOURCE_TYPES)
        assertFalse(SourceType.PHONE_SENSOR in DisconnectDetector.PUSHABLE_SOURCE_TYPES)
        // Sanity: every adapter we ship with sync-failure modes IS in the set.
        assertTrue(SourceType.OURA_API in DisconnectDetector.PUSHABLE_SOURCE_TYPES)
        assertTrue(SourceType.HEALTH_CONNECT in DisconnectDetector.PUSHABLE_SOURCE_TYPES)
        assertTrue(SourceType.GADGETBRIDGE in DisconnectDetector.PUSHABLE_SOURCE_TYPES)
    }

    // --- Helpers ---

    private fun assertSkipped(decision: PushDecision, expected: SkipReason) {
        assertFalse("Expected skip", decision.push)
        assertNotNull("Skip must carry a reason for log mining", decision.reason)
        assertEquals(expected, decision.reason)
    }

    @Suppress("unused")
    private fun assertPushed(decision: PushDecision) {
        assertTrue(decision.push)
        assertNull(decision.reason)
    }
}
