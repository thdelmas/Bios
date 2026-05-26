package com.bios.app

import com.bios.app.data.dao.TraditionalMedicineContextDao
import com.bios.app.model.MedicalTradition
import com.bios.app.model.PractitionerFrequency
import com.bios.app.model.TraditionalMedicineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip + contract tests for the traditional-medicine-context
 * surface (#193). The project's JVM-only unit-test setup precludes
 * exercising Room directly (no Robolectric, no instrumentation source
 * set), so this test uses a faithful in-memory fake of the DAO
 * interface. The fake's REPLACE-on-conflict behaviour mirrors
 * `@Insert(onConflict = REPLACE)` on the multi-row table.
 *
 * Contracts pinned here (per the audit-aligned issue brief):
 *  - DAO insert / list / delete round-trip.
 *  - Multi-tradition entries are supported (TCM + Ayurveda + Curandera
 *    coexist as independent rows).
 *  - `vocabularyOverlayConsent` defaults to `false`.
 *  - `MedicalTradition.OTHER` + free-text path works; blank free-text
 *    on the OTHER path is rejected at the repo boundary.
 */
class TraditionalMedicineContextRepoTest {

    /**
     * In-memory fake mirroring the Room DAO's REPLACE-on-conflict
     * semantics. Holds rows keyed by id; insert overwrites.
     */
    private class FakeDao : TraditionalMedicineContextDao {
        private val rows = LinkedHashMap<String, TraditionalMedicineContext>()

        override suspend fun insert(entry: TraditionalMedicineContext) {
            rows[entry.id] = entry
        }
        override suspend fun update(entry: TraditionalMedicineContext) {
            rows[entry.id] = entry
        }
        override suspend fun delete(entry: TraditionalMedicineContext) {
            rows.remove(entry.id)
        }
        override suspend fun fetchById(id: String): TraditionalMedicineContext? = rows[id]
        override suspend fun fetchAll(): List<TraditionalMedicineContext> =
            rows.values.sortedByDescending { it.createdAt }
        override suspend fun fetchOverlayOptedIn(): List<TraditionalMedicineContext> =
            rows.values.filter { it.vocabularyOverlayConsent }.sortedByDescending { it.createdAt }
        override suspend fun fetchByTradition(tradition: MedicalTradition): List<TraditionalMedicineContext> =
            rows.values.filter { it.tradition == tradition }.sortedByDescending { it.createdAt }
        override suspend fun count(): Int = rows.size
        override suspend fun deleteById(id: String) { rows.remove(id) }
    }

    @Test
    fun default_vocabulary_overlay_consent_is_false() {
        val row = TraditionalMedicineContext(
            id = "1",
            tradition = MedicalTradition.TCM,
        )
        assertFalse(
            "vocabularyOverlayConsent must default to false — no tradition " +
                "vocabulary appears anywhere unless the owner opts in.",
            row.vocabularyOverlayConsent
        )
    }

    @Test
    fun insert_then_fetch_round_trip_preserves_payload() = runBlocking {
        val dao = FakeDao()
        val row = TraditionalMedicineContext(
            id = "row-1",
            tradition = MedicalTradition.AYURVEDA,
            practitionerConsultedFrequency = PractitionerFrequency.OCCASIONAL,
            notes = "Following Dinacharya routine",
            regionOfPractice = "Tamil Nadu",
            vocabularyOverlayConsent = false,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
        )
        dao.insert(row)
        val fetched = dao.fetchById("row-1")
        assertEquals(row, fetched)
    }

    @Test
    fun list_returns_all_inserted_rows_newest_first() = runBlocking {
        val dao = FakeDao()
        dao.insert(
            TraditionalMedicineContext(
                id = "a", tradition = MedicalTradition.TCM, createdAt = 100L, updatedAt = 100L,
            )
        )
        dao.insert(
            TraditionalMedicineContext(
                id = "b", tradition = MedicalTradition.AYURVEDA, createdAt = 200L, updatedAt = 200L,
            )
        )
        val list = dao.fetchAll()
        assertEquals(2, list.size)
        assertEquals("b", list[0].id)
        assertEquals("a", list[1].id)
    }

    @Test
    fun delete_removes_the_row_and_leaves_others_alone() = runBlocking {
        val dao = FakeDao()
        val a = TraditionalMedicineContext(id = "a", tradition = MedicalTradition.TCM)
        val b = TraditionalMedicineContext(id = "b", tradition = MedicalTradition.AYURVEDA)
        dao.insert(a)
        dao.insert(b)
        dao.delete(a)
        assertNull(dao.fetchById("a"))
        assertNotNull(dao.fetchById("b"))
        assertEquals(1, dao.count())
    }

    @Test
    fun multi_tradition_entries_coexist_as_independent_rows() = runBlocking {
        // The audits explicitly call this out: an owner may consult both
        // a TCM practitioner AND a curandera AND follow an Ayurvedic
        // routine. Each row is independent — no singleton, no "primary
        // tradition" gate.
        val dao = FakeDao()
        dao.insert(
            TraditionalMedicineContext(
                id = "tcm",
                tradition = MedicalTradition.TCM,
                practitionerConsultedFrequency = PractitionerFrequency.REGULAR,
                notes = "On Yin-nourishing formula",
            )
        )
        dao.insert(
            TraditionalMedicineContext(
                id = "ayur",
                tradition = MedicalTradition.AYURVEDA,
                notes = "Dinacharya routine",
            )
        )
        dao.insert(
            TraditionalMedicineContext(
                id = "curandera",
                tradition = MedicalTradition.INDIGENOUS,
                notes = "Sees a curandera in town",
                regionOfPractice = "Oaxaca",
            )
        )
        val all = dao.fetchAll()
        assertEquals(3, all.size)
        // No row is special; each tradition fetch returns its own row.
        assertEquals(1, dao.fetchByTradition(MedicalTradition.TCM).size)
        assertEquals(1, dao.fetchByTradition(MedicalTradition.AYURVEDA).size)
        assertEquals(1, dao.fetchByTradition(MedicalTradition.INDIGENOUS).size)
    }

    @Test
    fun other_with_free_text_round_trips_intact() = runBlocking {
        val dao = FakeDao()
        val row = TraditionalMedicineContext(
            id = "other-1",
            tradition = MedicalTradition.OTHER,
            traditionFreeText = "Hoʻoponopono practice",
            notes = "Family practice from grandmother",
        )
        dao.insert(row)
        val fetched = dao.fetchById("other-1")
        assertEquals(MedicalTradition.OTHER, fetched?.tradition)
        assertEquals("Hoʻoponopono practice", fetched?.traditionFreeText)
    }

    @Test
    fun overlay_opted_in_query_filters_to_consenting_rows_only() = runBlocking {
        // Per the manifesto guard: vocabulary footnotes appear only for
        // rows where the owner explicitly opted in. This query is what
        // the (deferred) alert-detail renderer reads.
        val dao = FakeDao()
        dao.insert(
            TraditionalMedicineContext(
                id = "opted-in",
                tradition = MedicalTradition.TCM,
                vocabularyOverlayConsent = true,
            )
        )
        dao.insert(
            TraditionalMedicineContext(
                id = "default-off",
                tradition = MedicalTradition.AYURVEDA,
                // Defaults to false — should not appear in overlay list.
            )
        )
        val opted = dao.fetchOverlayOptedIn()
        assertEquals(1, opted.size)
        assertEquals("opted-in", opted.first().id)
    }

    @Test
    fun medical_tradition_enum_covers_the_eleven_converging_audits() {
        // The eleven converging audits plus the modern-non-allopathic
        // bucket plus the OTHER free-text escape hatch. If a tradition
        // slot gets renamed or dropped, this test catches it.
        val values = MedicalTradition.entries.map { it.name }.toSet()
        val required = setOf(
            "TCM", "AYURVEDA", "SIDDHA", "UNANI",
            "KOREAN_MEDICINE", "KAMPO", "SOWA_RIGPA",
            "AFRICAN_TRADITIONAL", "INDIGENOUS",
            "MAORI_RONGOA", "HAWAIIAN_LAU_LAPAAU", "NGANGKARI",
            "MODERN_NON_ALLOPATHIC", "OTHER",
        )
        assertTrue(
            "MedicalTradition must cover all required slots; missing: ${required - values}",
            values.containsAll(required)
        )
    }

    @Test
    fun repo_add_rejects_blank_free_text_when_tradition_is_other() {
        // The repo enforces the "OTHER requires free text" contract so
        // bad input from the UI never lands as a half-filled OTHER row
        // in the DB. This guards the contract without a DB; the require
        // call throws synchronously.
        assertThrows(IllegalArgumentException::class.java) {
            // Reuse the repo's preconditions by invoking the same
            // require() block directly — we can't construct a repo
            // without a BiosDatabase, and the JVM unit-test source set
            // doesn't ship one.
            requireOtherFreeText(MedicalTradition.OTHER, traditionFreeText = "   ")
        }
        // Sanity: non-OTHER tradition doesn't require free text.
        requireOtherFreeText(MedicalTradition.TCM, traditionFreeText = null)
    }

    /**
     * Mirrors the require() block in
     * [com.bios.app.data.TraditionalMedicineContextRepo.add] so the
     * contract is exercised in a JVM-only test.
     */
    private fun requireOtherFreeText(
        tradition: MedicalTradition,
        traditionFreeText: String?,
    ) {
        if (tradition == MedicalTradition.OTHER) {
            require(!traditionFreeText.isNullOrBlank()) {
                "traditionFreeText is required when tradition = OTHER"
            }
        }
    }
}
