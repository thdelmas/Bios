package com.bios.app

import com.bios.app.ingest.HealthConnectStableIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pure-JVM coverage of [HealthConnectStableIds] (#312). Pins the
 * record-id-anchored primary-key contract that keeps re-syncs of the
 * same HC record from producing phantom rows when HC mutates the
 * record's timestamps.
 */
class HealthConnectStableIdsTest {

    @Test
    fun `forRecord returns the same id for the same inputs`() {
        val a = HealthConnectStableIds.forRecord("sleep-duration", "hc-src", "session-abc")
        val b = HealthConnectStableIds.forRecord("sleep-duration", "hc-src", "session-abc")
        assertEquals(a, b)
    }

    @Test
    fun `forRecord disambiguates by kind`() {
        val duration = HealthConnectStableIds.forRecord("sleep-duration", "hc-src", "session-abc")
        val stage = HealthConnectStableIds.forRecord("sleep-stage", "hc-src", "session-abc")
        assertNotEquals(
            "different kinds with the same HC record id must not collide",
            duration, stage,
        )
    }

    @Test
    fun `forRecord disambiguates by sourceId`() {
        val srcA = HealthConnectStableIds.forRecord("sleep-duration", "src-a", "session-abc")
        val srcB = HealthConnectStableIds.forRecord("sleep-duration", "src-b", "session-abc")
        assertNotEquals("same HC record under two sources keeps separate rows", srcA, srcB)
    }

    @Test
    fun `forRecord disambiguates by HC record id`() {
        val a = HealthConnectStableIds.forRecord("sleep-duration", "hc-src", "session-a")
        val b = HealthConnectStableIds.forRecord("sleep-duration", "hc-src", "session-b")
        assertNotEquals(a, b)
    }

    @Test
    fun `forSubRecord returns the same id for the same inputs`() {
        val a = HealthConnectStableIds.forSubRecord("sleep-stage", "hc-src", "session-abc", 1_700_000_000_000L)
        val b = HealthConnectStableIds.forSubRecord("sleep-stage", "hc-src", "session-abc", 1_700_000_000_000L)
        assertEquals(a, b)
    }

    @Test
    fun `forSubRecord disambiguates by subKey`() {
        val first = HealthConnectStableIds.forSubRecord("sleep-stage", "hc-src", "session-abc", 1000L)
        val second = HealthConnectStableIds.forSubRecord("sleep-stage", "hc-src", "session-abc", 2000L)
        assertNotEquals(
            "two sub-records inside the same HC record must keep separate rows",
            first, second,
        )
    }

    @Test
    fun `forRecord and forSubRecord do not collide`() {
        // A record-level id should never collide with a sub-record id from
        // the same HC record — sleep-duration row vs sleep-stage rows live
        // alongside each other.
        val parent = HealthConnectStableIds.forRecord("sleep-stage", "hc-src", "session-abc")
        val child = HealthConnectStableIds.forSubRecord("sleep-stage", "hc-src", "session-abc", 0L)
        assertNotEquals(parent, child)
    }
}
