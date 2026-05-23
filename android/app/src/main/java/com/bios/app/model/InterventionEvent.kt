package com.bios.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Owner-recorded intervention event (#192). A single coherent primitive —
 * "an intervention occurred at time T" with optional metadata — serves
 * dozens of converging audit asks across tradition-medicine and modern
 * clinical contexts:
 *
 *  - Modern Non-Allopathic (all 9 modalities: PT, OMM, chiropractic,
 *    acupuncture, IV therapy, manual therapy, etc.)
 *  - Sowa Rigpa (me btsa', gtar ga, bsku mnye)
 *  - Ayurveda (Panchakarma)
 *  - Siddha (Thokkanam)
 *  - Other Asian (cupping, gua sha, hijama)
 *  - African Traditional (bone-setting)
 *  - Indigenous Americas (sobador sessions, ceremony as event)
 *  - Oceanic (Hoʻoponopono, Lomi lomi, Ngangkari sessions)
 *
 * **Manifesto position — recording is not endorsement.** Bios stores what
 * the owner says happened. It never validates "did the intervention
 * work," never auto-classifies, never asks "should you continue this
 * course?", never flags owners as "behind on rehab". The owner records;
 * Bios is the instrument that holds the record. Evaluation belongs to
 * the owner.
 *
 * **No condition patterns.** This entity is not an input to any
 * anomaly detector. It is a dated annotation. The alert-explanation
 * surface may reference it (the way it references medications) so an
 * owner reading an alert sees the wider context they themselves
 * declared — but no pattern fires on the basis of an intervention
 * event, and no judgement is rendered.
 *
 * **Optional course linkage.** When the owner is recording a multi-
 * session series (Panchakarma 14-day course, cardiac rehab phase II,
 * a 12-session chiropractic block), they can attach the event to a
 * parent [TreatmentCourse] via [treatmentCourseId]. The FK is `SET
 * NULL` on delete so removing a course doesn't cascade-destroy the
 * individual session records — the owner's record of what happened
 * survives the bookkeeping wrapper.
 *
 * **FHIR-friendly shape.** Maps to FHIR R4 `Procedure`. Some categories
 * pair with CPT codes (97140 manual therapy, 97810 acupuncture); not
 * every category has a single canonical CPT, which is fine — the
 * structure is FHIR-friendly without forcing a coding system the
 * owner hasn't supplied. See [toFhirProcedure] for the mapping.
 */
@Entity(
    tableName = "intervention_events",
    indices = [
        Index("timestamp"),
        Index("category"),
        Index("treatmentCourseId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = TreatmentCourse::class,
            parentColumns = ["id"],
            childColumns = ["treatmentCourseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class InterventionEvent(
    /** Stable, owner-agnostic id. UUID so we never collide across DBs. */
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Epoch millis when the intervention occurred. Owner-declared. */
    val timestamp: Long,
    /** Coarse-grained category. Free-text [subType] carries the specifics. */
    val category: InterventionCategory,
    /**
     * Owner-supplied refinement — verbatim, never parsed. Examples:
     * "Tuina", "Lomi lomi", "Panchakarma vamana", "OMM lumbar HVLA",
     * "Reiki Level 1", "8-needle Saam", "gua sha", "hijama wet cupping",
     * "Hoʻoponopono session", "sobador adjustment".
     */
    val subType: String? = null,
    /**
     * Optional link to the medical tradition the practitioner works in.
     * Bios doesn't infer this — owner picks. Null means owner didn't
     * say or the tradition isn't represented in the enum yet.
     */
    val practitionerTradition: MedicalTradition? = null,
    /** Owner free-text. Never parsed. */
    val notes: String? = null,
    /**
     * Optional anatomical region — owner free-text. Examples: "lumbar",
     * "left shoulder", "thoracic spine", "right knee". Surfaced as
     * `bodySite.text` in the FHIR export — never coded against SNOMED
     * by Bios because the owner's phrase is the source of truth.
     */
    val bodyRegion: String? = null,
    /**
     * Foreign key into [TreatmentCourse] when this event is part of a
     * multi-session series. Null when standalone.
     */
    val treatmentCourseId: String? = null,
)

/**
 * Coarse intervention categories spanning tradition-medicine and
 * modern-clinical modalities. Owner picks one + adds a free-text
 * [InterventionEvent.subType] for the specifics. The list is
 * deliberately broad: refining further would push toward
 * tradition-specific taxonomies that Bios is not in a position to
 * curate authoritatively.
 *
 * Mappings to FHIR `Procedure.category` (SNOMED CT) and to CPT (where
 * a single code is reasonable) are documented per-value below. Where a
 * single CPT doesn't cleanly apply (e.g. ceremony, breathwork), the
 * FHIR export uses category coding + free-text `code.text` carrying the
 * owner's [InterventionEvent.subType] — which is what makes the export
 * useful even without a coding system.
 */
enum class InterventionCategory(val displayName: String) {
    /**
     * Hands-on bodywork — Tuina, Thokkanam, Lomi lomi, OMM, chiropractic
     * adjustment, sobador session, bsku mnye (Sowa Rigpa massage), PT
     * manual therapy. CPT 97140 ("manual therapy techniques") is the
     * closest single code when one is needed.
     */
    MANUAL_THERAPY("Manual therapy / bodywork"),

    /**
     * Needle-based interventions — TCM acupuncture, Saam, dry needling.
     * CPT 97810 / 97811 (acupuncture without / with electrical
     * stimulation).
     */
    ACUPUNCTURE("Acupuncture / needling"),

    /**
     * Cupping, gua sha, hijama (wet cupping). Distinct enough from
     * manual therapy that grouping the three together reflects how
     * owners and clinicians describe them. No single CPT.
     */
    CUPPING_HIJAMA_GUA_SHA("Cupping / gua sha / hijama"),

    /**
     * Bone-setting — African traditional, ayurvedic, Sowa Rigpa gtar
     * ga, sobador-style joint reduction. CPT 97140 in some U.S.
     * billing contexts, but the manual-therapy category dilutes that;
     * kept distinct for fidelity to how owners describe the event.
     */
    BONE_SETTING("Bone-setting / joint reduction"),

    /**
     * IV therapy — IV hydration, vitamin infusions, chelation, IV
     * antibiotics administered outside hospital. CPT 96365 / 96366
     * (therapeutic infusion, initial / each additional hour).
     */
    INFUSION_IV("Infusion / IV therapy"),

    /**
     * Ceremony or ritual — Hoʻoponopono, Indigenous ceremony, Ngangkari
     * session, prayer healing, energy work, Reiki. Recording is not
     * endorsement of mechanism; what's recorded is that a session
     * occurred. No CPT.
     */
    CEREMONY_RITUAL("Ceremony / ritual / energy work"),

    /**
     * Breathwork or meditation session — pranayama, holotropic
     * breathwork, Vipassana retreat, MBSR session. No CPT.
     */
    BREATHWORK_MEDITATION("Breathwork / meditation"),

    /**
     * Exercise or rehab session — PT exercise visit, cardiac rehab
     * session, supervised pulmonary rehab, yoga therapy. CPT 97110
     * (therapeutic exercise) is the closest single code.
     */
    EXERCISE_REHAB("Exercise / rehab session"),

    /**
     * Anything else the owner records. The free-text
     * [InterventionEvent.subType] does the work here.
     */
    OTHER("Other"),
}

