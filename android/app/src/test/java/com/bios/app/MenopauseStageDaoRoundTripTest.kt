package com.bios.app

import com.bios.app.data.ReproductiveDatabase
import com.bios.app.data.dao.MenopauseStageEntryDao
import com.bios.app.model.MenopauseStage
import com.bios.app.model.MenopauseStageEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the menopause-stage DAO contract (#209). STRAW+10
 * staging (Harlow et al. 2012). Like [ContraceptionEntryDaoRoundTripTest],
 * uses a faithful fake of the DAO interface — the JVM unit-test target
 * doesn't include Room — and pins membership on the [ReproductiveDatabase].
 */
class MenopauseStageDaoRoundTripTest {

    private class FakeMenopauseStageEntryDao : MenopauseStageEntryDao {
        private val rows = mutableMapOf<String, MenopauseStageEntry>()
        override suspend fun insert(entry: MenopauseStageEntry) {
            rows[entry.id] = entry
        }

        override suspend fun delete(entry: MenopauseStageEntry) {
            rows.remove(entry.id)
        }

        override suspend fun fetchAll(): List<MenopauseStageEntry> =
            rows.values.sortedByDescending { it.recordedDate }

        override suspend fun fetchLatest(): MenopauseStageEntry? = fetchAll().firstOrNull()
        override suspend fun count(): Int = rows.size
        override suspend fun clear() {
            rows.clear()
        }
    }

    @Test
    fun insert_then_fetchLatest_returns_same_payload() = runBlocking {
        val dao = FakeMenopauseStageEntryDao()
        val entry = MenopauseStageEntry(
            stage = MenopauseStage.PERIMENOPAUSE_LATE.name,
            recordedDate = 1_700_000_000_000L,
            notes = "Hot flashes weekly, last period 2 months ago",
        )
        dao.insert(entry)
        val fetched = dao.fetchLatest()
        assertNotNull(fetched)
        assertEquals(entry, fetched)
    }

    @Test
    fun fetchAll_orders_by_recordedDate_descending() = runBlocking {
        val dao = FakeMenopauseStageEntryDao()
        val older = MenopauseStageEntry(
            stage = MenopauseStage.PERIMENOPAUSE_EARLY.name,
            recordedDate = 1_500_000_000_000L,
        )
        val newer = MenopauseStageEntry(
            stage = MenopauseStage.PERIMENOPAUSE_LATE.name,
            recordedDate = 1_700_000_000_000L,
        )
        dao.insert(older)
        dao.insert(newer)
        val all = dao.fetchAll()
        assertEquals(2, all.size)
        assertEquals(newer.id, all.first().id)
        assertEquals(older.id, all[1].id)
    }

    @Test
    fun delete_removes_specific_entry() = runBlocking {
        val dao = FakeMenopauseStageEntryDao()
        val entry = MenopauseStageEntry(
            stage = MenopauseStage.POSTMENOPAUSE.name,
            recordedDate = 1L,
        )
        dao.insert(entry)
        assertEquals(1, dao.count())
        dao.delete(entry)
        assertEquals(0, dao.count())
        assertNull(dao.fetchLatest())
    }

    @Test
    fun all_straw_plus_10_stages_serialise_to_their_enum_name() = runBlocking {
        val dao = FakeMenopauseStageEntryDao()
        for (s in MenopauseStage.entries) {
            dao.insert(
                MenopauseStageEntry(
                    stage = s.name,
                    recordedDate = s.ordinal.toLong(),
                )
            )
        }
        val fetched = dao.fetchAll().map { it.stage }.toSet()
        assertEquals(MenopauseStage.entries.map { it.name }.toSet(), fetched)
    }

    @Test
    fun menopause_dao_lives_on_the_reproductive_database_not_main() {
        val methods = ReproductiveDatabase::class.java.declaredMethods
        val hasAccessor = methods.any { it.name == "menopauseStageEntryDao" }
        assertTrue(
            "ReproductiveDatabase must expose menopauseStageEntryDao() so the " +
                "table lands in the separately-encrypted reproductive DB",
            hasAccessor,
        )

        val mainMethods = com.bios.app.data.BiosDatabase::class.java.declaredMethods
        val mainHasAccessor = mainMethods.any { it.name == "menopauseStageEntryDao" }
        assertTrue(
            "BiosDatabase must NOT expose menopauseStageEntryDao() — " +
                "menopause data belongs in the reproductive DB only",
            !mainHasAccessor,
        )
    }
}
