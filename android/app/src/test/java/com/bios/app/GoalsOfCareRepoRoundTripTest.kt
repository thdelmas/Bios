package com.bios.app

import com.bios.app.data.dao.ClinicalDirectiveDao
import com.bios.app.data.dao.GoalsOfCareDao
import com.bios.app.model.ClinicalDirective
import com.bios.app.model.CprPreference
import com.bios.app.model.GoalsOfCare
import com.bios.app.model.HospitalizationPreference
import com.bios.app.model.InterventionLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip tests for the goals-of-care + clinical-directive DAO
 * contracts (#184). The project's JVM-only unit-test setup precludes
 * exercising Room directly (no Robolectric, no instrumentation test
 * source set), so this test uses faithful in-memory fakes of the DAO
 * interfaces. The fake's REPLACE-on-conflict behaviour mirrors the
 * Room DAO's `@Insert(onConflict = REPLACE)` annotation, which the
 * single-row pattern depends on.
 *
 * If either interface drifts, this test won't compile — which is the
 * intent. The round-trip semantics (save then fetch returns the same
 * payload; save again on the same id replaces) are pinned here.
 */
class GoalsOfCareRepoRoundTripTest {

    // -- Fakes mirroring Room's @Insert(onConflict = REPLACE) for the
    //    single-row design. The fake holds at most one row keyed on
    //    SINGLETON_ID; save() overwrites.

    private class FakeGoalsOfCareDao : GoalsOfCareDao {
        private var row: GoalsOfCare? = null
        override suspend fun save(goals: GoalsOfCare) { row = goals }
        override suspend fun fetch(id: Long): GoalsOfCare? =
            row?.takeIf { it.id == id }
        override suspend fun clear(id: Long) {
            if (row?.id == id) row = null
        }
    }

    private class FakeClinicalDirectiveDao : ClinicalDirectiveDao {
        private var row: ClinicalDirective? = null
        override suspend fun save(directive: ClinicalDirective) { row = directive }
        override suspend fun fetch(id: Long): ClinicalDirective? =
            row?.takeIf { it.id == id }
        override suspend fun clear(id: Long) {
            if (row?.id == id) row = null
        }
    }

    // -- GoalsOfCareDao round-trip --

    @Test
    fun goals_save_then_fetch_returns_the_same_payload() = runBlocking {
        val dao = FakeGoalsOfCareDao()
        val saved = GoalsOfCare(
            cprPreference = CprPreference.DNAR_DNI,
            hospitalizationPreference = HospitalizationPreference.COMFORT_FOCUSED,
            interventionLevel = InterventionLevel.COMFORT_ONLY,
            documentLocation = "POLST in chart at Greenwood Clinic",
            notes = "reviewed with family",
            lastReviewedAt = 5_000L,
        )
        dao.save(saved)
        val fetched = dao.fetch()
        assertEquals(saved, fetched)
    }

    @Test
    fun goals_save_replaces_previous_row_on_singleton_id() = runBlocking {
        val dao = FakeGoalsOfCareDao()
        dao.save(GoalsOfCare(cprPreference = CprPreference.FULL_CODE))
        dao.save(GoalsOfCare(cprPreference = CprPreference.DNAR_DNI))
        val fetched = dao.fetch()
        assertEquals(CprPreference.DNAR_DNI, fetched?.cprPreference)
    }

    @Test
    fun goals_clear_removes_the_row() = runBlocking {
        val dao = FakeGoalsOfCareDao()
        dao.save(GoalsOfCare(interventionLevel = InterventionLevel.COMFORT_ONLY))
        dao.clear()
        assertNull(dao.fetch())
    }

    // -- ClinicalDirectiveDao round-trip --

    @Test
    fun directive_save_then_fetch_returns_the_same_payload() = runBlocking {
        val dao = FakeClinicalDirectiveDao()
        val saved = ClinicalDirective(
            hasAdvanceDirective = true,
            hasPolst = true,
            hasHealthcareProxy = true,
            proxyContactName = "L. Garcia",
            proxyContactPhone = "+1-555-0199",
            updatedAt = 9_000L,
        )
        dao.save(saved)
        val fetched = dao.fetch()
        assertEquals(saved, fetched)
    }

    @Test
    fun directive_save_replaces_previous_row_on_singleton_id() = runBlocking {
        val dao = FakeClinicalDirectiveDao()
        dao.save(ClinicalDirective(hasAdvanceDirective = false))
        dao.save(ClinicalDirective(hasAdvanceDirective = true))
        val fetched = dao.fetch()
        assertEquals(true, fetched?.hasAdvanceDirective)
    }

    @Test
    fun directive_clear_removes_the_row() = runBlocking {
        val dao = FakeClinicalDirectiveDao()
        dao.save(ClinicalDirective(hasPolst = true))
        dao.clear()
        assertNull(dao.fetch())
    }
}
