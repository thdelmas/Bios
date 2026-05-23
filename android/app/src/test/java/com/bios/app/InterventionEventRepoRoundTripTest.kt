package com.bios.app

import com.bios.app.data.dao.InterventionEventDao
import com.bios.app.data.dao.TreatmentCourseDao
import com.bios.app.model.InterventionCategory
import com.bios.app.model.InterventionEvent
import com.bios.app.model.MedicalTradition
import com.bios.app.model.TreatmentCourse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the InterventionEvent + TreatmentCourse DAO
 * contracts (#192). Mirrors the fake-DAO pattern used by
 * [GoalsOfCareRepoRoundTripTest] — the project's JVM-only unit-test
 * setup precludes hitting Room directly, so this test exercises
 * faithful in-memory fakes that mirror the Room DAO semantics
 * (`@Insert(onConflict = REPLACE)`, ordering by `timestamp DESC`,
 * FK linkage from `treatmentCourseId`).
 *
 * If either interface drifts, this test won't compile — which is the
 * intent. The round-trip semantics (save then fetch returns the same
 * payload; insert twice on the same id replaces; FK linkage works)
 * are pinned here.
 */
class InterventionEventRepoRoundTripTest {

    private class FakeInterventionEventDao : InterventionEventDao {
        private val rows = mutableMapOf<String, InterventionEvent>()
        override suspend fun insert(event: InterventionEvent): Long {
            rows[event.id] = event
            return 0L
        }
        override suspend fun update(event: InterventionEvent) { rows[event.id] = event }
        override suspend fun delete(event: InterventionEvent) { rows.remove(event.id) }
        override suspend fun fetchById(id: String): InterventionEvent? = rows[id]
        override suspend fun fetchAll(): List<InterventionEvent> =
            rows.values.sortedByDescending { it.timestamp }
        override suspend fun fetchByCourse(courseId: String): List<InterventionEvent> =
            rows.values.filter { it.treatmentCourseId == courseId }
                .sortedByDescending { it.timestamp }
        override suspend fun fetchSince(since: Long): List<InterventionEvent> =
            rows.values.filter { it.timestamp >= since }
                .sortedByDescending { it.timestamp }
        override suspend fun count(): Int = rows.size
    }

    private class FakeTreatmentCourseDao : TreatmentCourseDao {
        private val rows = mutableMapOf<String, TreatmentCourse>()
        override suspend fun insert(course: TreatmentCourse): Long {
            rows[course.id] = course
            return 0L
        }
        override suspend fun update(course: TreatmentCourse) { rows[course.id] = course }
        override suspend fun delete(course: TreatmentCourse) { rows.remove(course.id) }
        override suspend fun fetchById(id: String): TreatmentCourse? = rows[id]
        override suspend fun fetchAll(): List<TreatmentCourse> =
            rows.values.sortedWith(
                compareBy<TreatmentCourse> { it.actualEndAt != null }
                    .thenByDescending { it.actualEndAt ?: Long.MIN_VALUE }
                    .thenByDescending { it.startedAt }
            )
        override suspend fun fetchOpen(): List<TreatmentCourse> =
            rows.values.filter { it.actualEndAt == null }.sortedByDescending { it.startedAt }
        override suspend fun count(): Int = rows.size
    }

    // -- InterventionEvent round-trip --

    @Test
    fun event_save_then_fetch_returns_same_payload() = runBlocking {
        val dao = FakeInterventionEventDao()
        val saved = InterventionEvent(
            id = "ev-1",
            timestamp = 1_700_000_000_000L,
            category = InterventionCategory.ACUPUNCTURE,
            subType = "8-needle Saam",
            practitionerTradition = MedicalTradition.KOREAN_MEDICINE,
            notes = "post-session: reduced tension",
            bodyRegion = "thoracic spine",
            treatmentCourseId = null,
        )
        dao.insert(saved)
        val fetched = dao.fetchById("ev-1")
        assertEquals(saved, fetched)
    }

    @Test
    fun event_insert_replaces_on_same_id() = runBlocking {
        val dao = FakeInterventionEventDao()
        dao.insert(
            InterventionEvent(id = "ev-1", timestamp = 1L, category = InterventionCategory.MANUAL_THERAPY)
        )
        dao.insert(
            InterventionEvent(id = "ev-1", timestamp = 1L, category = InterventionCategory.CUPPING_HIJAMA_GUA_SHA)
        )
        assertEquals(InterventionCategory.CUPPING_HIJAMA_GUA_SHA, dao.fetchById("ev-1")?.category)
        assertEquals(1, dao.count())
    }

    @Test
    fun event_free_text_subtype_path_round_trips_verbatim() = runBlocking {
        val dao = FakeInterventionEventDao()
        // Owner-supplied free-text subType is never parsed or
        // normalised — verbatim is the contract.
        val saved = InterventionEvent(
            id = "ev-2",
            timestamp = 10L,
            category = InterventionCategory.CEREMONY_RITUAL,
            subType = "Hoʻoponopono session — Auntie Mahealani",
        )
        dao.insert(saved)
        assertEquals(
            "Hoʻoponopono session — Auntie Mahealani",
            dao.fetchById("ev-2")?.subType
        )
    }

    @Test
    fun event_fetch_all_orders_newest_first() = runBlocking {
        val dao = FakeInterventionEventDao()
        dao.insert(InterventionEvent(id = "old", timestamp = 100L, category = InterventionCategory.OTHER))
        dao.insert(InterventionEvent(id = "new", timestamp = 200L, category = InterventionCategory.OTHER))
        dao.insert(InterventionEvent(id = "mid", timestamp = 150L, category = InterventionCategory.OTHER))
        val ids = dao.fetchAll().map { it.id }
        assertEquals(listOf("new", "mid", "old"), ids)
    }

    @Test
    fun event_delete_removes_the_row() = runBlocking {
        val dao = FakeInterventionEventDao()
        dao.insert(InterventionEvent(id = "ev-x", timestamp = 1L, category = InterventionCategory.OTHER))
        dao.delete(dao.fetchById("ev-x")!!)
        assertNull(dao.fetchById("ev-x"))
    }

    // -- TreatmentCourse round-trip --

    @Test
    fun course_save_then_fetch_returns_same_payload() = runBlocking {
        val dao = FakeTreatmentCourseDao()
        val saved = TreatmentCourse(
            id = "course-1",
            label = "Panchakarma — Spring 2026",
            tradition = MedicalTradition.AYURVEDA,
            startedAt = 1_700_000_000_000L,
            expectedEndAt = 1_701_209_600_000L,
            actualEndAt = null,
            goal = "complete vamana protocol",
            notes = "with Dr. Kapur",
        )
        dao.insert(saved)
        assertEquals(saved, dao.fetchById("course-1"))
    }

    @Test
    fun course_fetch_open_excludes_closed_courses() = runBlocking {
        val dao = FakeTreatmentCourseDao()
        dao.insert(TreatmentCourse(id = "open", label = "A", startedAt = 1L, actualEndAt = null))
        dao.insert(TreatmentCourse(id = "closed", label = "B", startedAt = 1L, actualEndAt = 5L))
        val open = dao.fetchOpen()
        assertEquals(1, open.size)
        assertEquals("open", open[0].id)
    }

    @Test
    fun course_fetch_all_puts_open_before_closed() = runBlocking {
        val dao = FakeTreatmentCourseDao()
        dao.insert(TreatmentCourse(id = "closed", label = "B", startedAt = 10L, actualEndAt = 20L))
        dao.insert(TreatmentCourse(id = "open", label = "A", startedAt = 1L, actualEndAt = null))
        val all = dao.fetchAll()
        assertEquals(listOf("open", "closed"), all.map { it.id })
    }

    // -- FK linkage from InterventionEvent → TreatmentCourse --

    @Test
    fun event_can_link_to_a_treatment_course_and_be_fetched_by_course() = runBlocking {
        val courseDao = FakeTreatmentCourseDao()
        val eventDao = FakeInterventionEventDao()
        courseDao.insert(TreatmentCourse(id = "panchakarma-1", label = "PK", startedAt = 0L))
        eventDao.insert(
            InterventionEvent(
                id = "ev-a", timestamp = 5L,
                category = InterventionCategory.MANUAL_THERAPY,
                subType = "Abhyanga",
                treatmentCourseId = "panchakarma-1",
            )
        )
        eventDao.insert(
            InterventionEvent(
                id = "ev-b", timestamp = 10L,
                category = InterventionCategory.OTHER,
                subType = "vamana",
                treatmentCourseId = "panchakarma-1",
            )
        )
        eventDao.insert(
            InterventionEvent(
                id = "standalone", timestamp = 20L,
                category = InterventionCategory.ACUPUNCTURE,
                treatmentCourseId = null,
            )
        )
        val attached = eventDao.fetchByCourse("panchakarma-1")
        assertEquals(2, attached.size)
        assertEquals(listOf("ev-b", "ev-a"), attached.map { it.id })
        assertTrue(attached.all { it.treatmentCourseId == "panchakarma-1" })
    }

    @Test
    fun event_with_null_course_does_not_appear_in_course_fetch() = runBlocking {
        val eventDao = FakeInterventionEventDao()
        eventDao.insert(
            InterventionEvent(
                id = "ev-orphan", timestamp = 1L,
                category = InterventionCategory.OTHER,
                treatmentCourseId = null,
            )
        )
        assertTrue(eventDao.fetchByCourse("any-id").isEmpty())
    }

    @Test
    fun event_default_constructor_generates_unique_uuid() {
        val a = InterventionEvent(timestamp = 1L, category = InterventionCategory.OTHER)
        val b = InterventionEvent(timestamp = 1L, category = InterventionCategory.OTHER)
        assertNotNull(a.id)
        assertNotNull(b.id)
        // UUIDs collide with probability ~0; if this ever fires, the
        // entity stopped generating fresh ids — a regression.
        assertTrue("UUIDs must differ across instances", a.id != b.id)
    }
}
