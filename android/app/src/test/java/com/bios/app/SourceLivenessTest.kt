package com.bios.app

import com.bios.app.alerts.LivenessState
import com.bios.app.alerts.assessLiveness
import com.bios.app.ingest.OwnerAction
import com.bios.app.ingest.SourceCadence
import com.bios.app.ingest.SourceHealth
import com.bios.app.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Pins docs/specs/source-liveness.md criteria 1 to 3: staleness in the
 * source's own interval, attention only from a refusal with an owner action
 * newer than the last delivery, transport errors never flip state.
 */
class SourceLivenessTest {

    private val now = 1_790_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)
    private val hour = TimeUnit.HOURS.toMillis(1)

    private fun assess(
        type: SourceType = SourceType.COROS_API,
        first: Long = now - 10 * day,
        last: Long = now - hour,
        health: SourceHealth = SourceHealth(),
        metrics: List<String> = listOf("heart_rate_variability", "resting_heart_rate"),
    ) = assessLiveness(type, "COROS", first, last, metrics, health, now)

    @Test
    fun `no primary reading yet is NEVER_DELIVERED`() {
        val row = assess(first = 0L, last = 0L)
        assertEquals(LivenessState.NEVER_DELIVERED, row.state)
        assertFalse(row.previouslyActive)
    }

    @Test
    fun `delivered within one interval is HEALTHY`() {
        val row = assess(last = now - day + hour)
        assertEquals(LivenessState.HEALTHY, row.state)
        assertEquals(OwnerAction.NONE, row.ownerAction)
        assertNull(row.message)
    }

    @Test
    fun `nightly source is STALE after two missed nights and names the device sync`() {
        val row = assess(last = now - SourceCadence.STALE_INTERVALS * day)
        assertEquals(LivenessState.STALE, row.state)
        assertEquals(OwnerAction.SYNC_DEVICE, row.ownerAction)
        assertEquals(now - 2 * day, row.since)
    }

    @Test
    fun `one missed night is still HEALTHY`() {
        assertEquals(LivenessState.HEALTHY, assess(last = now - 2 * day + hour).state)
    }

    @Test
    fun `in-process sensor is measured in hours not days`() {
        val row = assess(type = SourceType.PHONE_SENSOR, last = now - 2 * hour)
        assertEquals(LivenessState.STALE, row.state)
    }

    @Test
    fun `a refusal with an owner action newer than the last delivery is ATTENTION`() {
        val health = SourceHealth(
            lastOkAt = now - 5 * day,
            lastErrorAt = now - hour,
            lastError = "Access token is invalid (1019)",
            ownerAction = OwnerAction.REAUTH,
        )
        val row = assess(last = now - 5 * day, health = health)
        assertEquals(LivenessState.ATTENTION, row.state)
        assertEquals(OwnerAction.REAUTH, row.ownerAction)
        assertEquals("Access token is invalid (1019)", row.message)
        assertEquals(now - hour, row.since)
    }

    @Test
    fun `attention wins over staleness while the refusal stands`() {
        val health = SourceHealth(lastErrorAt = now - hour, lastError = "1019", ownerAction = OwnerAction.REAUTH)
        assertEquals(LivenessState.ATTENTION, assess(last = now - 9 * day, health = health).state)
    }

    @Test
    fun `a delivery newer than the refusal clears attention`() {
        val health = SourceHealth(lastErrorAt = now - 3 * day, lastError = "1019", ownerAction = OwnerAction.REAUTH)
        assertEquals(LivenessState.HEALTHY, assess(last = now - hour, health = health).state)
    }

    @Test
    fun `a recorded ok newer than the refusal clears attention`() {
        val health = SourceHealth(
            lastOkAt = now - hour, lastErrorAt = now - 2 * hour,
            lastError = "1019", ownerAction = OwnerAction.REAUTH,
        )
        assertFalse(health.hasOpenError)
        assertEquals(LivenessState.HEALTHY, assess(health = health).state)
    }

    @Test
    fun `a transport error carries no owner action and does not flip state`() {
        val health = SourceHealth(lastErrorAt = now - hour, lastError = "network: timeout", ownerAction = OwnerAction.NONE)
        val fresh = assess(health = health)
        assertEquals(LivenessState.HEALTHY, fresh.state)
        val stale = assess(last = now - 3 * day, health = health)
        assertEquals(LivenessState.STALE, stale.state)
        assertEquals("network: timeout", stale.message)
    }

    @Test
    fun `sources without a cadence are never stale`() {
        val row = assess(type = SourceType.SELF_REPORTED, last = now - 40 * day)
        assertEquals(LivenessState.HEALTHY, row.state)
        assertEquals(0L, row.missedIntervals(now))
    }

    @Test
    fun `previously active means a delivery span of two intervals not a row count`() {
        assertFalse(assess(first = now - day, last = now - hour).previouslyActive)
        assertTrue(assess(first = now - 3 * day, last = now - day).previouslyActive)
    }

    @Test
    fun `missed intervals count whole intervals since the last delivery`() {
        assertEquals(5L, assess(last = now - 5 * day - hour).missedIntervals(now))
        assertEquals(0L, assess(last = now - hour).missedIntervals(now))
    }

    @Test
    fun `metric coverage is carried through for consumers`() {
        assertEquals(listOf("heart_rate_variability", "resting_heart_rate"), assess().metricTypes)
    }
}
