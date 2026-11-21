package com.bios.app

import com.bios.app.data.dao.HeadacheLogDao
import com.bios.app.data.dao.MigraineAttackDao
import com.bios.app.model.HeadacheLog
import com.bios.app.model.HeadacheType
import com.bios.app.model.MigraineAttack
import com.bios.app.model.MigraineTrigger
import com.bios.app.model.MigraineTriggerConverter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the neurology owner-symptom-logging persistence layer
 * (issue #207). Covers DAO round-trips and the converter for
 * [MigraineAttack] and [HeadacheLog].
 *
 * The fake-DAO pattern mirrors [CompanionGateTest] —
 * the Robolectric-free unit-test source set can't exercise Room SQLite
 * directly.
 */
class NeurologySymptomLoggingTest {

    private class FakeMigraineDao : MigraineAttackDao {
        private val rows = mutableListOf<MigraineAttack>()

        override suspend fun insert(attack: MigraineAttack): Long {
            rows.removeAll { it.id == attack.id }
            rows.add(attack)
            return 1L
        }

        override suspend fun update(attack: MigraineAttack) {
            val idx = rows.indexOfFirst { it.id == attack.id }
            if (idx >= 0) rows[idx] = attack
        }

        override suspend fun delete(attack: MigraineAttack) {
            rows.removeAll { it.id == attack.id }
        }

        override suspend fun deleteById(id: String) {
            rows.removeAll { it.id == id }
        }

        override suspend fun fetchById(id: String): MigraineAttack? =
            rows.firstOrNull { it.id == id }

        override suspend fun fetchAll(): List<MigraineAttack> =
            rows.sortedByDescending { it.onsetTimestamp }

        override suspend fun fetchInRange(fromMillis: Long, toMillis: Long): List<MigraineAttack> =
            rows.filter { it.onsetTimestamp in fromMillis..toMillis }
                .sortedByDescending { it.onsetTimestamp }

        override suspend fun count(): Int = rows.size
    }

    private class FakeHeadacheDao : HeadacheLogDao {
        private val rows = mutableListOf<HeadacheLog>()

        override suspend fun insert(log: HeadacheLog): Long {
            rows.removeAll { it.id == log.id }
            rows.add(log)
            return 1L
        }

        override suspend fun delete(log: HeadacheLog) {
            rows.removeAll { it.id == log.id }
        }

        override suspend fun deleteById(id: String) {
            rows.removeAll { it.id == id }
        }

        override suspend fun fetchById(id: String): HeadacheLog? =
            rows.firstOrNull { it.id == id }

        override suspend fun fetchAll(): List<HeadacheLog> =
            rows.sortedByDescending { it.timestamp }

        override suspend fun fetchInRange(fromMillis: Long, toMillis: Long): List<HeadacheLog> =
            rows.filter { it.timestamp in fromMillis..toMillis }
                .sortedByDescending { it.timestamp }

        override suspend fun count(): Int = rows.size
    }

    @Test
    fun `MigraineAttackDao insert and fetch round-trip preserves all fields`() = runBlocking {
        val dao = FakeMigraineDao()
        val attack = MigraineAttack(
            id = "attack-1",
            onsetTimestamp = 1_700_000_000_000L,
            endTimestamp = 1_700_003_600_000L,
            peakIntensity = 8,
            aura = true,
            triggers = setOf(MigraineTrigger.STRESS, MigraineTrigger.SLEEP_DEPRIVATION),
            medicationTaken = "sumatriptan 50mg",
            notes = "left-sided, photophobia",
        )

        dao.insert(attack)

        val fetched = dao.fetchById("attack-1")
        assertNotNull(fetched)
        assertEquals(1_700_000_000_000L, fetched!!.onsetTimestamp)
        assertEquals(1_700_003_600_000L, fetched.endTimestamp)
        assertEquals(8, fetched.peakIntensity)
        assertTrue(fetched.aura)
        assertEquals(
            setOf(MigraineTrigger.STRESS, MigraineTrigger.SLEEP_DEPRIVATION),
            fetched.triggers,
        )
        assertEquals("sumatriptan 50mg", fetched.medicationTaken)
        assertEquals("left-sided, photophobia", fetched.notes)
    }

    @Test
    fun `MigraineAttackDao fetchAll orders newest first`() = runBlocking {
        val dao = FakeMigraineDao()
        dao.insert(MigraineAttack(id = "a", onsetTimestamp = 1_000L, peakIntensity = 5))
        dao.insert(MigraineAttack(id = "b", onsetTimestamp = 3_000L, peakIntensity = 5))
        dao.insert(MigraineAttack(id = "c", onsetTimestamp = 2_000L, peakIntensity = 5))

        val all = dao.fetchAll()
        assertEquals(listOf("b", "c", "a"), all.map { it.id })
    }

    @Test
    fun `MigraineAttackDao deleteById removes the row`() = runBlocking {
        val dao = FakeMigraineDao()
        dao.insert(MigraineAttack(id = "a", onsetTimestamp = 1_000L, peakIntensity = 5))
        assertEquals(1, dao.count())
        dao.deleteById("a")
        assertEquals(0, dao.count())
        assertNull(dao.fetchById("a"))
    }

    @Test
    fun `HeadacheLogDao insert and fetch round-trip preserves all fields`() = runBlocking {
        val dao = FakeHeadacheDao()
        val log = HeadacheLog(
            id = "log-1",
            timestamp = 1_700_000_000_000L,
            intensity = 4,
            type = HeadacheType.TENSION,
            durationMinutes = 90,
            medicationTaken = "ibuprofen 400mg",
            notes = "screen time",
        )

        dao.insert(log)

        val fetched = dao.fetchById("log-1")
        assertNotNull(fetched)
        assertEquals(1_700_000_000_000L, fetched!!.timestamp)
        assertEquals(4, fetched.intensity)
        assertEquals(HeadacheType.TENSION, fetched.type)
        assertEquals(90, fetched.durationMinutes)
        assertEquals("ibuprofen 400mg", fetched.medicationTaken)
        assertEquals("screen time", fetched.notes)
    }

    @Test
    fun `HeadacheLogDao fetchInRange honours window inclusively`() = runBlocking {
        val dao = FakeHeadacheDao()
        for ((id, ts) in listOf("a" to 100L, "b" to 200L, "c" to 300L, "d" to 400L)) {
            dao.insert(
                HeadacheLog(id = id, timestamp = ts, intensity = 4, type = HeadacheType.TENSION)
            )
        }

        val window = dao.fetchInRange(200L, 300L)
        assertEquals(listOf("c", "b"), window.map { it.id })
    }

    @Test
    fun `MigraineTriggerConverter round-trips known enum values`() {
        val converter = MigraineTriggerConverter()
        val input = setOf(MigraineTrigger.STRESS, MigraineTrigger.WEATHER, MigraineTrigger.OTHER)
        val stored = converter.fromSet(input)
        val restored = converter.toSet(stored)
        assertEquals(input, restored)
    }

    @Test
    fun `MigraineTriggerConverter tolerates unknown trigger values`() {
        val converter = MigraineTriggerConverter()
        val restored = converter.toSet("STRESS,UNKNOWN_FUTURE_TRIGGER,WEATHER")
        assertEquals(setOf(MigraineTrigger.STRESS, MigraineTrigger.WEATHER), restored)
    }

    @Test
    fun `MigraineTriggerConverter handles empty input`() {
        val converter = MigraineTriggerConverter()
        assertEquals(emptySet<MigraineTrigger>(), converter.toSet(""))
        assertEquals("", converter.fromSet(emptySet()))
    }

    @Test
    fun `MigraineAttack validateIntensity rejects out of range values`() {
        MigraineAttack.validateIntensity(0)
        MigraineAttack.validateIntensity(5)
        MigraineAttack.validateIntensity(10)
        try {
            MigraineAttack.validateIntensity(-1)
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // ok
        }
        try {
            MigraineAttack.validateIntensity(11)
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `HeadacheLog validateIntensity rejects out of range values`() {
        HeadacheLog.validateIntensity(0)
        HeadacheLog.validateIntensity(10)
        try {
            HeadacheLog.validateIntensity(-1)
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // ok
        }
    }
}
