package com.bios.app

import com.bios.app.data.ReproductiveDatabase
import com.bios.app.data.dao.ContraceptionEntryDao
import com.bios.app.model.ContraceptionEntry
import com.bios.app.model.ContraceptionMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the contraception DAO contract (#209). The
 * project's JVM-only unit-test setup precludes exercising Room directly,
 * so this test uses a faithful in-memory fake of the DAO interface
 * mirroring Room's `@Insert(onConflict = REPLACE)` semantics. If the
 * DAO interface drifts, this test stops compiling — which is the intent.
 *
 * Crucially, the [ReproductiveDatabase] reflection check pins that the
 * contraception DAO lives on the *separately-encrypted* reproductive DB
 * and not on the main DB. Audit gap §2.11: contraception history is
 * post-Dobbs-sensitive and must inherit the reproductive DB's
 * priority-destruction guarantees.
 */
class ContraceptionEntryDaoRoundTripTest {

    private class FakeContraceptionEntryDao : ContraceptionEntryDao {
        private val rows = mutableMapOf<String, ContraceptionEntry>()
        override suspend fun insert(entry: ContraceptionEntry) {
            rows[entry.id] = entry
        }

        override suspend fun delete(entry: ContraceptionEntry) {
            rows.remove(entry.id)
        }

        override suspend fun fetchAll(): List<ContraceptionEntry> =
            rows.values.sortedByDescending { it.startDate }

        override suspend fun fetchLatest(): ContraceptionEntry? = fetchAll().firstOrNull()
        override suspend fun count(): Int = rows.size
        override suspend fun clear() {
            rows.clear()
        }
    }

    @Test
    fun insert_then_fetchLatest_returns_same_payload() = runBlocking {
        val dao = FakeContraceptionEntryDao()
        val entry = ContraceptionEntry(
            method = ContraceptionMethod.IUD_HORMONAL.name,
            startDate = 1_700_000_000_000L,
            notes = "Mirena placed 2023-06",
        )
        dao.insert(entry)
        val fetched = dao.fetchLatest()
        assertNotNull(fetched)
        assertEquals(entry, fetched)
    }

    @Test
    fun fetchAll_orders_by_startDate_descending() = runBlocking {
        val dao = FakeContraceptionEntryDao()
        val older = ContraceptionEntry(
            method = ContraceptionMethod.HORMONAL_PILL.name,
            startDate = 1_500_000_000_000L,
            endDate = 1_600_000_000_000L,
        )
        val newer = ContraceptionEntry(
            method = ContraceptionMethod.IUD_COPPER.name,
            startDate = 1_700_000_000_000L,
        )
        dao.insert(older)
        dao.insert(newer)
        val all = dao.fetchAll()
        assertEquals(2, all.size)
        // Latest by startDate is first.
        assertEquals(newer.id, all.first().id)
        assertEquals(older.id, all[1].id)
    }

    @Test
    fun delete_removes_specific_entry() = runBlocking {
        val dao = FakeContraceptionEntryDao()
        val entry = ContraceptionEntry(
            method = ContraceptionMethod.NONE.name,
            startDate = 1L,
        )
        dao.insert(entry)
        assertEquals(1, dao.count())
        dao.delete(entry)
        assertEquals(0, dao.count())
        assertNull(dao.fetchLatest())
    }

    @Test
    fun all_methods_serialise_to_their_enum_name() = runBlocking {
        val dao = FakeContraceptionEntryDao()
        for (m in ContraceptionMethod.entries) {
            dao.insert(
                ContraceptionEntry(
                    method = m.name,
                    startDate = m.ordinal.toLong(),
                )
            )
        }
        // Round-trip every enum value through the DAO.
        val fetched = dao.fetchAll().map { it.method }.toSet()
        assertEquals(ContraceptionMethod.entries.map { it.name }.toSet(), fetched)
    }

    @Test
    fun contraception_dao_lives_on_the_reproductive_database_not_main() {
        // Pin storage isolation: the DAO accessor must be declared on
        // [ReproductiveDatabase], not on the main [BiosDatabase]. This
        // catches a regression that would move the table into the main
        // DB and lose the separate encryption envelope + priority
        // destruction on duress.
        val methods = ReproductiveDatabase::class.java.declaredMethods
        val hasContraceptionAccessor = methods.any { it.name == "contraceptionEntryDao" }
        assertTrue(
            "ReproductiveDatabase must expose contraceptionEntryDao() so the " +
                "table lands in the separately-encrypted reproductive DB",
            hasContraceptionAccessor,
        )

        val mainMethods = com.bios.app.data.BiosDatabase::class.java.declaredMethods
        val mainHasContraceptionAccessor = mainMethods.any { it.name == "contraceptionEntryDao" }
        assertTrue(
            "BiosDatabase must NOT expose contraceptionEntryDao() — " +
                "contraception history belongs in the reproductive DB only",
            !mainHasContraceptionAccessor,
        )
    }
}
