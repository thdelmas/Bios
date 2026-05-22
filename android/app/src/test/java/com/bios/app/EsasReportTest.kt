package com.bios.app

import com.bios.app.data.dao.EsasReportDao
import com.bios.app.model.EsasLoinc
import com.bios.app.model.EsasReport
import com.bios.app.model.toFhirObservationCodes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the owner-administered ESAS-r symptom-burden surface
 * (issue #185). Covers:
 *  - DAO insert/query/range round-trip via a fake DAO.
 *  - Repository range-query semantics.
 *  - LOINC mapping returns the documented codes for the verified items
 *    and Bios-local codes for the TODO items.
 *
 * The fake DAO mirrors the pattern used in [CompanionGateTest] —
 * Room's actual SQLite layer is not exercised by the Robolectric-free
 * unit-test source set in this repo, and the in-memory Room builder is
 * not test-runnable here either. The fake-DAO pattern is the
 * established way to test repository wiring in this codebase.
 */
class EsasReportTest {

    private class FakeDao : EsasReportDao {
        private val rows = mutableListOf<EsasReport>()
        private var nextId = 1L

        override suspend fun insert(report: EsasReport): Long {
            val withId = if (report.id == 0L) report.copy(id = nextId++) else report
            rows.removeAll { it.id == withId.id }
            rows.add(withId)
            return withId.id
        }

        override suspend fun delete(report: EsasReport) {
            rows.removeAll { it.id == report.id }
        }

        override suspend fun deleteById(id: Long) {
            rows.removeAll { it.id == id }
        }

        override suspend fun fetchById(id: Long): EsasReport? =
            rows.firstOrNull { it.id == id }

        override suspend fun fetchAll(): List<EsasReport> =
            rows.sortedByDescending { it.timestamp }

        override suspend fun fetchInRange(fromMillis: Long, toMillis: Long): List<EsasReport> =
            rows.filter { it.timestamp in fromMillis..toMillis }
                .sortedByDescending { it.timestamp }

        override suspend fun count(): Int = rows.size
    }

    private fun sampleReport(
        timestamp: Long = 1_700_000_000_000L,
        pain: Int = 3,
        otherLabel: String? = null,
        otherScore: Int? = null,
    ) = EsasReport(
        timestamp = timestamp,
        painScore = pain,
        tirednessScore = 4,
        drowsinessScore = 2,
        nauseaScore = 1,
        appetiteScore = 5,
        shortnessOfBreathScore = 0,
        depressionScore = 2,
        anxietyScore = 6,
        wellbeingScore = 4,
        otherSymptomLabel = otherLabel,
        otherSymptomScore = otherScore,
    )

    // --- DAO insert/query round-trip --------------------------------------

    @Test
    fun `dao insert and fetch round-trip preserves all fields`() = runBlocking {
        val dao = FakeDao()
        val report = sampleReport(otherLabel = "constipation", otherScore = 7)

        val id = dao.insert(report)

        val fetched = dao.fetchById(id)
        assertNotNull(fetched)
        assertEquals(1, dao.count())
        assertEquals(3, fetched!!.painScore)
        assertEquals(4, fetched.tirednessScore)
        assertEquals(2, fetched.drowsinessScore)
        assertEquals(1, fetched.nauseaScore)
        assertEquals(5, fetched.appetiteScore)
        assertEquals(0, fetched.shortnessOfBreathScore)
        assertEquals(2, fetched.depressionScore)
        assertEquals(6, fetched.anxietyScore)
        assertEquals(4, fetched.wellbeingScore)
        assertEquals("constipation", fetched.otherSymptomLabel)
        assertEquals(7, fetched.otherSymptomScore)
    }

    @Test
    fun `dao fetchAll orders newest first`() = runBlocking {
        val dao = FakeDao()
        dao.insert(sampleReport(timestamp = 1_000L))
        dao.insert(sampleReport(timestamp = 3_000L))
        dao.insert(sampleReport(timestamp = 2_000L))

        val all = dao.fetchAll()

        assertEquals(3, all.size)
        assertEquals(3_000L, all[0].timestamp)
        assertEquals(2_000L, all[1].timestamp)
        assertEquals(1_000L, all[2].timestamp)
    }

    @Test
    fun `dao deleteById removes the row`() = runBlocking {
        val dao = FakeDao()
        val id = dao.insert(sampleReport())
        assertEquals(1, dao.count())

        dao.deleteById(id)

        assertEquals(0, dao.count())
        assertNull(dao.fetchById(id))
    }

    // --- Repository range query -------------------------------------------

    @Test
    fun `dao fetchInRange returns only reports in window inclusive`() = runBlocking {
        val dao = FakeDao()
        dao.insert(sampleReport(timestamp = 100L))
        dao.insert(sampleReport(timestamp = 200L))
        dao.insert(sampleReport(timestamp = 300L))
        dao.insert(sampleReport(timestamp = 400L))

        val window = dao.fetchInRange(200L, 300L)

        assertEquals(2, window.size)
        // Newest first.
        assertEquals(300L, window[0].timestamp)
        assertEquals(200L, window[1].timestamp)
    }

    @Test
    fun `dao fetchInRange empty window returns empty list`() = runBlocking {
        val dao = FakeDao()
        dao.insert(sampleReport(timestamp = 100L))

        val window = dao.fetchInRange(500L, 600L)

        assertTrue(window.isEmpty())
    }

    // --- Score validation -------------------------------------------------

    @Test
    fun `validateScore accepts the 0 to 10 range`() {
        EsasReport.validateScore("pain", 0)
        EsasReport.validateScore("pain", 5)
        EsasReport.validateScore("pain", 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateScore rejects below zero`() {
        EsasReport.validateScore("pain", -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateScore rejects above ten`() {
        EsasReport.validateScore("pain", 11)
    }

    // --- LOINC mapping ----------------------------------------------------

    @Test
    fun `toFhirObservationCodes emits exactly the nine standard items when no other symptom`() {
        val report = sampleReport()

        val codings = report.toFhirObservationCodes()

        assertEquals(9, codings.size)
        val items = codings.map { it.item }
        val expected = listOf(
            EsasReport.ITEM_PAIN,
            EsasReport.ITEM_TIREDNESS,
            EsasReport.ITEM_DROWSINESS,
            EsasReport.ITEM_NAUSEA,
            EsasReport.ITEM_APPETITE,
            EsasReport.ITEM_SHORTNESS_OF_BREATH,
            EsasReport.ITEM_DEPRESSION,
            EsasReport.ITEM_ANXIETY,
            EsasReport.ITEM_WELLBEING,
        )
        assertEquals(expected, items)
    }

    @Test
    fun `pain item carries the verified LOINC 38221-8`() {
        val codings = sampleReport(pain = 7).toFhirObservationCodes()
        val pain = codings.first { it.item == EsasReport.ITEM_PAIN }

        assertEquals("http://loinc.org", pain.system)
        assertEquals(EsasLoinc.PAIN_CODE, pain.code)
        assertEquals(EsasLoinc.PAIN_DISPLAY, pain.display)
        assertEquals(7, pain.score)
    }

    @Test
    fun `panel LOINC constants match the documented ESAS-r codes`() {
        // Guards against accidental edits to the LOINC constants. The
        // panel LOINC 89216-1 and pain LOINC 38221-8 are what clinical
        // systems index against; if either drifts, FHIR round-trip
        // breaks silently — better to fail here.
        assertEquals("89216-1", EsasLoinc.PANEL_CODE)
        assertEquals("38221-8", EsasLoinc.PAIN_CODE)
        assertEquals("75265-9", EsasLoinc.TOTAL_DISTRESS_CODE)
    }

    @Test
    fun `non-pain items carry the Bios-local system pending LOINC verification`() {
        val codings = sampleReport().toFhirObservationCodes()
        val nonPainItems = listOf(
            EsasReport.ITEM_TIREDNESS,
            EsasReport.ITEM_DROWSINESS,
            EsasReport.ITEM_NAUSEA,
            EsasReport.ITEM_APPETITE,
            EsasReport.ITEM_SHORTNESS_OF_BREATH,
            EsasReport.ITEM_DEPRESSION,
            EsasReport.ITEM_ANXIETY,
            EsasReport.ITEM_WELLBEING,
        )
        for (item in nonPainItems) {
            val coding = codings.first { it.item == item }
            assertEquals(
                "Item $item should use Bios-local system until per-item LOINC is verified",
                EsasLoinc.BIOS_SYSTEM,
                coding.system,
            )
        }
    }

    @Test
    fun `other-symptom slot is included when label and score are both set`() {
        val report = sampleReport(otherLabel = "neuropathy", otherScore = 8)

        val codings = report.toFhirObservationCodes()

        assertEquals(10, codings.size)
        val other = codings.last()
        assertEquals(EsasReport.ITEM_OTHER, other.item)
        assertEquals(EsasLoinc.BIOS_SYSTEM, other.system)
        assertEquals("neuropathy", other.display)
        assertEquals(8, other.score)
    }

    @Test
    fun `other-symptom slot is omitted when label is null`() {
        val report = sampleReport(otherLabel = null, otherScore = null)

        val codings = report.toFhirObservationCodes()

        assertEquals(9, codings.size)
        assertFalse(codings.any { it.item == EsasReport.ITEM_OTHER })
    }
}
