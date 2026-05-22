package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Owner-declared traditional-medicine practice context (#193). A pull-side
 * projection layer that lets the owner annotate that they consult a TCM
 * practitioner (and which formula they are on), follow Ayurvedic
 * Dinacharya, practice Hoʻoponopono, see a curandera, etc. Bios records
 * what the owner says about their own practice; Bios does **not** classify
 * within any of these traditions.
 *
 * **Eleven audits converge on this surface.** TCM, Ayurveda, Siddha,
 * Sowa Rigpa, Unani, Korean Medicine, Kampo, African Traditional,
 * Indigenous Americas, Oceanic/Arctic, and Modern Non-Allopathic audits
 * all asked for the same shape: a place to record tradition context
 * without Bios diagnosing within the tradition. The audits unanimously
 * flag that Bios must **not** ship automated tradition-diagnosis: no
 * dosha classifier, no TCM pulse classifier, no fukushin palpation
 * interpreter, no Mizaj inference, no Sasang typing. This entity is for
 * *owner-recorded context only.*
 *
 * **Multi-row by design.** The owner may consult both a TCM practitioner
 * **and** a curandera **and** keep a Dinacharya routine. Each context
 * entry is independent: own [tradition], own [practitionerConsultedFrequency],
 * own [notes], own [vocabularyOverlayConsent]. There is no singleton row.
 *
 * **Recording is not endorsement.** Bios stores what the owner declares
 * about their practice. Bios never validates "does TCM work" or "is this
 * dosha real". Bios never offers to classify within the tradition. The
 * owner is the practitioner of their own life; Bios is the instrument
 * they read.
 *
 * **Vocabulary overlay is opt-in per row.** [vocabularyOverlayConsent]
 * defaults to `false`. When the owner explicitly opts in for a specific
 * tradition, downstream alert-detail surfaces *may* render a single
 * additional footnote line using that tradition's vocabulary (e.g.
 * "RHR pattern: 治未病 territory" or "rLung-disturbance signature"). The
 * footnote is informational, never diagnostic — it carries no claim.
 * Vocabulary text must pass [com.bios.app.alerts.AlertContentPolicy].
 * When the consent flag is `false`, no tradition vocabulary appears
 * anywhere. (Vocabulary catalogues are deferred — see TODO in
 * [com.bios.app.data.TraditionalMedicineContextRepo].)
 *
 * **Manifesto guard: pull-side only.** The owner navigates in, the owner
 * adds, the owner edits, the owner deletes. Nothing on this surface
 * pushes a notification. Storing a context entry never alters which
 * patterns Bios detects — only how (optionally) certain alert-detail
 * views may add an extra footnote line.
 */
@Entity(
    tableName = "traditional_medicine_context",
    indices = [Index("tradition")],
)
data class TraditionalMedicineContext(
    /**
     * Stable string id. Caller supplies (typically a UUID). Multi-row
     * design — each entry is an independent row keyed by this id.
     */
    @PrimaryKey val id: String,
    /**
     * Which tradition the owner is recording context for. Use [OTHER]
     * together with [traditionFreeText] when none of the named enum
     * values match. The 14 listed traditions cover the eleven
     * converging audits plus a generic "modern non-allopathic" slot and
     * a free-text escape hatch.
     */
    val tradition: MedicalTradition,
    /**
     * Free-text name when [tradition] is [MedicalTradition.OTHER]. Left
     * `null` for any other tradition. The owner controls this string;
     * Bios never parses it.
     */
    val traditionFreeText: String? = null,
    /**
     * Owner-declared rough frequency of consultation with a practitioner
     * in this tradition. No judgement attached — just an axis the owner
     * may want to surface alongside their notes. Optional.
     */
    val practitionerConsultedFrequency: PractitionerFrequency? = null,
    /** Owner free-text notes about their practice. Bios never parses. */
    val notes: String? = null,
    /**
     * Owner-set region of practice (e.g. "Tamil Nadu", "Tibet",
     * "South Auckland"). Free text; Bios stores verbatim. Helps the
     * owner remember which lineage / locale a given practitioner or
     * formula traces to. Optional.
     */
    val regionOfPractice: String? = null,
    /**
     * Per-tradition opt-in. When `true`, alert-detail views *may*
     * render an additional footnote line in this tradition's
     * vocabulary. Defaults to **false** — no tradition vocabulary
     * appears anywhere unless the owner opts in for that specific row.
     */
    val vocabularyOverlayConsent: Boolean = false,
    /** Epoch millis when this row was created. */
    val createdAt: Long = System.currentTimeMillis(),
    /** Epoch millis when this row was last edited. */
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Catalogue of traditional / non-allopathic medical traditions the owner
 * may record context for. Listing a tradition here is **not** an
 * endorsement of its claims — it is acknowledgement that the owner may
 * practice within it and may want Bios to hold that fact about
 * themselves. The list reflects the eleven converging audits plus
 * [MODERN_NON_ALLOPATHIC] for traditions that don't fit a named slot,
 * plus [OTHER] for the free-text escape hatch.
 *
 * Per the audits' "do not adopt" sections, Bios does **not** ship
 * automated classifiers within any of these traditions. No dosha
 * typing, no TCM pulse classification, no Sasang typing, no Mizaj
 * inference, no fukushin palpation interpreter, no Hahnemannian
 * repertorisation.
 */
enum class MedicalTradition(val displayName: String) {
    /** Traditional Chinese Medicine. */
    TCM("Traditional Chinese Medicine"),

    /** Ayurveda (Indian subcontinent, classical). */
    AYURVEDA("Ayurveda"),

    /** Siddha medicine (Tamil tradition). */
    SIDDHA("Siddha"),

    /** Unani medicine (Greco-Arabic / South Asian). */
    UNANI("Unani"),

    /** Korean traditional medicine (Hanui / Sasang). */
    KOREAN_MEDICINE("Korean Medicine"),

    /** Kampo (Japanese herbal tradition). */
    KAMPO("Kampo"),

    /** Sowa Rigpa (Tibetan medicine). */
    SOWA_RIGPA("Sowa Rigpa (Tibetan)"),

    /** African Traditional Medicine (broad continental category). */
    AFRICAN_TRADITIONAL("African Traditional Medicine"),

    /** Indigenous Americas traditions (broad continental category). */
    INDIGENOUS("Indigenous American Traditions"),

    /** Māori rongoā (Aotearoa / New Zealand). */
    MAORI_RONGOA("Māori Rongoā"),

    /** Hawaiian lāʻau lapaʻau and lomi lomi practice. */
    HAWAIIAN_LAU_LAPAAU("Hawaiian Lāʻau Lapaʻau"),

    /** Ngangkari healing (Aboriginal Australia, Anangu). */
    NGANGKARI("Ngangkari Healing"),

    /**
     * Modern non-allopathic traditions that don't fit a tradition-named
     * slot (homeopathy, naturopathy, chiropractic, osteopathy, certain
     * energy-medicine practices). Recording is not endorsement.
     */
    MODERN_NON_ALLOPATHIC("Modern Non-Allopathic"),

    /** Free-text escape hatch — use with [TraditionalMedicineContext.traditionFreeText]. */
    OTHER("Other (free-text)"),
}

/**
 * Owner-declared rough frequency of practitioner consultation. Used as
 * an axis only — Bios does not draw conclusions from this. The owner
 * may leave it `null` to skip the axis entirely.
 */
enum class PractitionerFrequency(val displayName: String) {
    NEVER("Never"),
    RARE("Rarely"),
    OCCASIONAL("Occasionally"),
    REGULAR("Regularly"),
}
