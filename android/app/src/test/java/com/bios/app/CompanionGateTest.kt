package com.bios.app

import com.bios.app.data.dao.CompanionGrantDao
import com.bios.app.model.CompanionGrant
import com.bios.app.provider.CompanionGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionGateTest {

    private class FakeDao : CompanionGrantDao {
        val rows = mutableMapOf<String, CompanionGrant>()
        override suspend fun upsert(grant: CompanionGrant) { rows[grant.packageName] = grant }
        override suspend fun find(pkg: String): CompanionGrant? = rows[pkg]
        override fun observeAll(): Flow<List<CompanionGrant>> = flowOf(rows.values.toList())
        override suspend fun getAll(): List<CompanionGrant> = rows.values.toList()
        override suspend fun getByState(state: String): List<CompanionGrant> =
            rows.values.filter { it.state == state }
        override suspend fun recordAccess(pkg: String, ts: Long) {
            rows[pkg]?.let { rows[pkg] = it.copy(lastAccessAt = ts, accessCount = it.accessCount + 1) }
        }
        override suspend fun delete(pkg: String) { rows.remove(pkg) }
        override suspend fun deleteAll() { rows.clear() }
    }

    private fun gate(now: Long = 1_000L, dao: FakeDao = FakeDao()) =
        Pair(dao, CompanionGate(dao) { now })

    @Test
    fun `unknown package is recorded as pending and denied`() = runBlocking {
        val (dao, gate) = gate(now = 5_000L)

        val decision = gate.check("com.example.unknown")

        assertEquals(CompanionGate.Decision.Deny(CompanionGate.Decision.Reason.PENDING_APPROVAL), decision)
        val row = dao.find("com.example.unknown")
        assertNotNull(row)
        assertEquals(CompanionGrant.STATE_PENDING, row!!.state)
        assertEquals(5_000L, row.firstSeenAt)
        assertEquals(5_000L, row.lastAccessAt)
        assertEquals(1L, row.accessCount)
    }

    @Test
    fun `repeated pending calls bump access count without changing state`() = runBlocking {
        val (dao, gate) = gate(now = 100L)

        gate.check("com.x")
        gate.check("com.x")
        gate.check("com.x")

        val row = dao.find("com.x")!!
        assertEquals(CompanionGrant.STATE_PENDING, row.state)
        assertEquals(3L, row.accessCount)
    }

    @Test
    fun `granted package is allowed and access is recorded`() = runBlocking {
        val dao = FakeDao()
        val gate = CompanionGate(dao) { 9_000L }
        gate.approve("com.w2f")

        val decision = gate.check("com.w2f")

        assertEquals(CompanionGate.Decision.Allow, decision)
        val row = dao.find("com.w2f")!!
        assertEquals(CompanionGrant.STATE_GRANTED, row.state)
        assertEquals(9_000L, row.lastAccessAt)
        assertEquals(1L, row.accessCount)
    }

    @Test
    fun `revoked package is denied and access is not recorded`() = runBlocking {
        val dao = FakeDao()
        val gate = CompanionGate(dao) { 1_000L }
        gate.approve("com.w2f")
        gate.revoke("com.w2f")

        val decision = gate.check("com.w2f")

        assertEquals(CompanionGate.Decision.Deny(CompanionGate.Decision.Reason.REVOKED), decision)
        val row = dao.find("com.w2f")!!
        assertEquals(CompanionGrant.STATE_REVOKED, row.state)
        assertEquals(0L, row.accessCount)
        assertNull(row.lastAccessAt)
    }

    @Test
    fun `null or blank calling package is denied without persisting`() = runBlocking {
        val (dao, gate) = gate()

        val nullDecision = gate.check(null)
        val blankDecision = gate.check("   ")

        assertEquals(CompanionGate.Decision.Deny(CompanionGate.Decision.Reason.NO_CALLING_PACKAGE), nullDecision)
        assertEquals(CompanionGate.Decision.Deny(CompanionGate.Decision.Reason.NO_CALLING_PACKAGE), blankDecision)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `approve preserves firstSeenAt and clears revokedAt`() = runBlocking {
        val dao = FakeDao()
        val gate = CompanionGate(dao) { 100L }
        gate.check("com.w2f") // creates PENDING at 100
        val gateLater = CompanionGate(dao) { 500L }
        gateLater.revoke("com.w2f")
        val gateLatest = CompanionGate(dao) { 900L }
        gateLatest.approve("com.w2f")

        val row = dao.find("com.w2f")!!
        assertEquals(CompanionGrant.STATE_GRANTED, row.state)
        assertEquals(100L, row.firstSeenAt)
        assertEquals(900L, row.grantedAt)
        assertNull(row.revokedAt)
    }

    @Test
    fun `revoke on unknown package is a no-op`() = runBlocking {
        val (dao, gate) = gate()

        gate.revoke("com.never.seen")

        assertTrue(dao.rows.isEmpty())
    }
}
