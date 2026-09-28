package com.bios.app

import com.bios.app.alerts.DisconnectDetector
import com.bios.app.alerts.LivenessState
import com.bios.app.alerts.SkipReason
import com.bios.app.alerts.SourceLivenessRow
import com.bios.app.alerts.decidePush
import com.bios.app.ingest.OwnerAction
import com.bios.app.ingest.SourceCadence
import com.bios.app.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Deterministic trigger-contract tests for [DisconnectDetector]
 * (docs/specs/source-liveness.md). Three gates govern the trigger:
 * previously-active, actionable (attention with an owner action, or stale
 * past the push window), past-cool-down. Each test isolates one gate. The
 * full surface (DB reads, NotificationManager, PendingIntent plumbing)
 * requires Android runtime and is covered by build-and-eyeball.
 */
class DisconnectDetectorTest {

    private val now: Long = 1_733_400_000_000L
    private val day: Long = TimeUnit.DAYS.toMillis(1)

    private fun row(
        type: SourceType = SourceType.COROS_API,
        state: LivenessState = LivenessState.STALE,
        last: Long = now - 6 * day,
        first: Long = now - 30 * day,
        action: OwnerAction = OwnerAction.SYNC_DEVICE,
        message: String? = null,
    ) = SourceLivenessRow(
        sourceType = type, label = "COROS", state = state, since = last,
        ownerAction = action, message = message, lastDeliveredAt = last,
        firstDeliveredAt = first, metricTypes = listOf("heart_rate_variability"),
    )

    // --- Gate 1: previously active ---

    @Test
    fun `source that delivered for less than two intervals is skipped as NEVER_ACTIVE even on a refusal`() {
        val decision = decidePush(
            row(state = LivenessState.ATTENTION, action = OwnerAction.REAUTH, first = now - day, last = now - day),
            lastPushedAt = 0L, now = now,
        )
        assertFalse(decision.push)
        assertEquals(SkipReason.NEVER_ACTIVE, decision.reason)
    }

    @Test
    fun `two nights of a two-rows-a-day source count as active`() {
        val decision = decidePush(
            row(state = LivenessState.ATTENTION, action = OwnerAction.REAUTH,
                first = now - 3 * day, last = now - day),
            lastPushedAt = 0L, now = now,
        )
        assertTrue(decision.push)
    }

    // --- Gate 2: actionable ---

    @Test
    fun `a refusal with an owner action pushes on the next sync`() {
        val decision = decidePush(
            row(state = LivenessState.ATTENTION, action = OwnerAction.REAUTH, last = now - day, message = "1019"),
            lastPushedAt = 0L, now = now,
        )
        assertTrue(decision.push)
        assertNull(decision.reason)
    }

    @Test
    fun `a refusal without an owner action does not push`() {
        val decision = decidePush(
            row(state = LivenessState.ATTENTION, action = OwnerAction.NONE, last = now - day),
            lastPushedAt = 0L, now = now,
        )
        assertFalse(decision.push)
        assertEquals(SkipReason.NOT_STALE, decision.reason)
    }

    @Test
    fun `stale at the in-app threshold is NOT_STALE for the push`() {
        val decision = decidePush(
            row(last = now - SourceCadence.STALE_INTERVALS * day),
            lastPushedAt = 0L, now = now,
        )
        assertFalse(decision.push)
        assertEquals(SkipReason.NOT_STALE, decision.reason)
    }

    @Test
    fun `stale for the push window pushes`() {
        val decision = decidePush(
            row(last = now - SourceCadence.PUSH_STALE_INTERVALS * day),
            lastPushedAt = 0L, now = now,
        )
        assertTrue(decision.push)
    }

    @Test
    fun `push window is measured in the source's own interval`() {
        val hour = TimeUnit.HOURS.toMillis(1)
        val phone = row(
            type = SourceType.HEALTH_CONNECT,
            last = now - SourceCadence.PUSH_STALE_INTERVALS * day + hour,
            first = now - 30 * day,
        )
        assertEquals(SkipReason.NOT_STALE, decidePush(phone, 0L, now).reason)
    }

    @Test
    fun `healthy and never-delivered rows never push`() {
        assertEquals(SkipReason.NOT_STALE, decidePush(row(state = LivenessState.HEALTHY, last = now - day), 0L, now).reason)
        assertEquals(
            SkipReason.NEVER_ACTIVE,
            decidePush(row(state = LivenessState.NEVER_DELIVERED, last = 0L, first = 0L), 0L, now).reason,
        )
    }

    // --- Gate 3: cool-down ---

    @Test
    fun `recently-pushed source is IN_COOLDOWN even when persistently stale`() {
        val decision = decidePush(row(last = now - 20 * day), lastPushedAt = now - 6 * day, now = now)
        assertFalse(decision.push)
        assertEquals(SkipReason.IN_COOLDOWN, decision.reason)
    }

    @Test
    fun `cool-down expires after the documented window`() {
        val decision = decidePush(
            row(last = now - 20 * day),
            lastPushedAt = now - DisconnectDetector.COOLDOWN_MILLIS, now = now,
        )
        assertTrue(decision.push)
    }

    @Test
    fun `never-pushed source (lastPushedAt = 0) clears cool-down regardless of clock`() {
        assertTrue(decidePush(row(last = now - 20 * day), lastPushedAt = 0L, now = now).push)
    }

    @Test
    fun `negative lastPushedAt is treated as never-pushed, not as a future timestamp`() {
        assertTrue(decidePush(row(last = now - 20 * day), lastPushedAt = -1L, now = now).push)
    }

    @Test
    fun `gate ordering — not-active beats not-stale beats cool-down`() {
        val r = row(state = LivenessState.HEALTHY, first = now - day, last = now - day)
        assertEquals(SkipReason.NEVER_ACTIVE, decidePush(r, now - day, now).reason)
        val r2 = row(state = LivenessState.HEALTHY, last = now - day)
        assertEquals(SkipReason.NOT_STALE, decidePush(r2, now - day, now).reason)
    }

    // --- Source-type set ---

    @Test
    fun `COROS is a pushable source`() {
        assertTrue(SourceType.COROS_API in DisconnectDetector.PUSHABLE_SOURCE_TYPES)
        assertFalse(SourceType.PHONE_SENSOR in DisconnectDetector.PUSHABLE_SOURCE_TYPES)
        assertFalse(SourceType.SELF_REPORTED in DisconnectDetector.PUSHABLE_SOURCE_TYPES)
    }
}
