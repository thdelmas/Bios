package com.bios.app.data

import com.bios.app.model.MedicalTradition
import com.bios.app.model.PractitionerFrequency
import com.bios.app.model.TraditionalMedicineContext
import java.util.UUID

/**
 * Repository for owner-declared traditional-medicine context (#193).
 *
 * Eleven audits converge here — TCM, Ayurveda, Siddha, Sowa Rigpa,
 * Unani, Korean Medicine, Kampo, African Traditional, Indigenous
 * Americas, Oceanic/Arctic (Māori rongoā, Hawaiian lāʻau lapaʻau,
 * Ngangkari), and Modern Non-Allopathic. The shape is uniform: a
 * pull-side projection layer where the owner annotates which
 * traditions they practice within, without Bios diagnosing or
 * classifying within any of them.
 *
 * Stored in the main encrypted DB; wiped alongside the rest of the
 * owner's health data via the standard LETHE "Delete all data" path.
 *
 * **No automated classification.** The audits unanimously flag this:
 * no dosha typing, no TCM pulse classifier, no Sasang typing, no
 * Mizaj inference, no fukushin palpation interpreter, no Hahnemannian
 * repertorisation. This repo holds owner-declared context only.
 *
 * **Vocabulary overlay (deferred catalogue).** When a row has
 * [TraditionalMedicineContext.vocabularyOverlayConsent] set to `true`,
 * downstream alert-detail surfaces *may* render one extra footnote
 * line using that tradition's vocabulary. The vocabulary catalogues
 * themselves live in `docs/audits/` and are not present on this
 * branch (PR #223 lands them); the consent flag is persisted today,
 * the renderer is wired up in a follow-up PR. See
 * [overlayCandidatesFor] for the read path that catalogue-lookup
 * code will plug into.
 */
class TraditionalMedicineContextRepo(private val db: BiosDatabase) {

    private val dao get() = db.traditionalMedicineContextDao()

    /**
     * Adds a new context entry. Generates a stable string id when the
     * caller doesn't provide one. Returns the persisted row so callers
     * can immediately surface it.
     *
     * @param tradition the tradition this row records context for.
     * @param traditionFreeText required when [tradition] is
     *   [MedicalTradition.OTHER]; ignored otherwise. Stored verbatim.
     * @param vocabularyOverlayConsent opt-in per row. Defaults to
     *   `false` — the manifesto-aligned default is silence in the
     *   tradition's vocabulary until the owner explicitly enables it.
     */
    suspend fun add(
        tradition: MedicalTradition,
        traditionFreeText: String? = null,
        practitionerConsultedFrequency: PractitionerFrequency? = null,
        notes: String? = null,
        regionOfPractice: String? = null,
        vocabularyOverlayConsent: Boolean = false,
        id: String = UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis(),
    ): TraditionalMedicineContext {
        if (tradition == MedicalTradition.OTHER) {
            require(!traditionFreeText.isNullOrBlank()) {
                "traditionFreeText is required when tradition = OTHER"
            }
        }
        val row = TraditionalMedicineContext(
            id = id,
            tradition = tradition,
            traditionFreeText = traditionFreeText
                ?.takeIf { it.isNotBlank() }
                ?.trim()
                ?.takeIf { tradition == MedicalTradition.OTHER },
            practitionerConsultedFrequency = practitionerConsultedFrequency,
            notes = notes?.takeIf { it.isNotBlank() }?.trim(),
            regionOfPractice = regionOfPractice?.takeIf { it.isNotBlank() }?.trim(),
            vocabularyOverlayConsent = vocabularyOverlayConsent,
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(row)
        return row
    }

    /**
     * Replaces an existing row. Bumps [TraditionalMedicineContext.updatedAt]
     * automatically so the UI can show "last edited" without callers
     * threading timestamps.
     */
    suspend fun save(
        entry: TraditionalMedicineContext,
        now: Long = System.currentTimeMillis(),
    ) {
        dao.insert(entry.copy(updatedAt = now))
    }

    suspend fun remove(id: String) = dao.deleteById(id)

    suspend fun fetchById(id: String): TraditionalMedicineContext? = dao.fetchById(id)

    suspend fun fetchAll(): List<TraditionalMedicineContext> = dao.fetchAll()

    suspend fun fetchByTradition(tradition: MedicalTradition): List<TraditionalMedicineContext> =
        dao.fetchByTradition(tradition)

    suspend fun count(): Int = dao.count()

    /**
     * Rows for which the owner has opted into vocabulary overlay. The
     * alert-detail renderer in a future PR walks this list and asks a
     * (still-deferred) vocabulary catalogue for an equivalent line per
     * tradition. When the catalogue is empty for a given tradition, the
     * renderer simply omits the footnote — silence is the safe default.
     *
     * TODO(#193 follow-up): wire this to the per-tradition vocabulary
     * catalogue in `docs/audits/` once those land (PR #223). For now the
     * consent flag is persisted; the renderer is a no-op.
     */
    suspend fun overlayCandidatesFor(): List<TraditionalMedicineContext> =
        dao.fetchOverlayOptedIn()
}
