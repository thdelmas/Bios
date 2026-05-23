package com.bios.app.export

import com.bios.app.model.InterventionCategory
import com.bios.app.model.InterventionEvent
import com.bios.app.model.TreatmentCourse
import org.json.JSONArray
import org.json.JSONObject

/**
 * FHIR R4 mapping for the owner-recorded intervention primitives (#192).
 * Kept in its own file because the per-entity exporter logic risks
 * pushing the main [FhirExporter] over the 500-line file cap.
 *
 * Owner-declared by definition — `Procedure.status` is always
 * `completed` because the event represents a session the owner says
 * occurred. The mapping intentionally avoids any clinical claim Bios
 * isn't entitled to make:
 *
 *  - `category` carries the Bios coarse-grained category as a coding
 *    in the local `https://bios.health/intervention-category` system,
 *    so a receiving system that doesn't know our category vocabulary
 *    still sees a typed value.
 *  - `code` carries a CPT code when a single CPT cleanly applies
 *    (manual therapy = 97140, acupuncture = 97810, IV therapy = 96365,
 *    exercise/rehab = 97110). `code.text` always carries the owner's
 *    free-text [InterventionEvent.subType] (or the category display
 *    name if subType is null) so categories without a CPT (ceremony,
 *    breathwork) still export meaningfully.
 *  - `bodySite.text` carries the owner's anatomical region verbatim —
 *    Bios does not auto-code it against SNOMED.
 *  - `note[0].text` carries the owner's notes verbatim.
 *  - `partOf` references the parent `TreatmentCourse` when the event
 *    belongs to one. The course itself is exported as a contained
 *    extension resource in a later PR — for v1, the reference is by
 *    Bios id and is meaningful only inside the Bundle.
 *
 * Practitioner tradition is intentionally **not** mapped to a FHIR
 * `Practitioner` resource — that requires a coding system Bios does
 * not authoritatively curate. Today it lands as an extension under
 * `https://bios.health/fhir/practitioner-tradition` so the data
 * survives the round-trip without claiming clinical-coding fidelity
 * we can't back.
 */
internal fun buildInterventionProcedureResource(event: InterventionEvent): JSONObject = JSONObject().apply {
    put("resourceType", "Procedure")
    put("id", event.id)
    put("status", "completed")
    put("category", JSONObject().apply {
        put("coding", JSONArray().put(JSONObject().apply {
            put("system", "https://bios.health/intervention-category")
            put("code", event.category.name)
            put("display", event.category.displayName)
        }))
    })
    put("code", JSONObject().apply {
        val coding = JSONArray()
        cptCode(event.category)?.let { (code, display) ->
            coding.put(JSONObject().apply {
                put("system", "http://www.ama-assn.org/go/cpt")
                put("code", code)
                put("display", display)
            })
        }
        if (coding.length() > 0) put("coding", coding)
        put("text", event.subType ?: event.category.displayName)
    })
    put("performedDateTime", formatEpochMillis(event.timestamp))
    event.bodyRegion?.takeIf { it.isNotBlank() }?.let { region ->
        put("bodySite", JSONArray().put(JSONObject().apply {
            put("text", region)
        }))
    }
    event.notes?.takeIf { it.isNotBlank() }?.let { note ->
        put("note", JSONArray().put(JSONObject().apply {
            put("text", note)
        }))
    }
    event.treatmentCourseId?.let { courseId ->
        put("partOf", JSONArray().put(JSONObject().apply {
            put("reference", "TreatmentCourse/$courseId")
        }))
    }
    event.practitionerTradition?.let { tradition ->
        put("extension", JSONArray().put(JSONObject().apply {
            put("url", "https://bios.health/fhir/practitioner-tradition")
            put("valueString", tradition.name)
        }))
    }
}

/**
 * Convenience extension mirroring the `toFhir*` naming used elsewhere
 * in the codebase. Pure delegate so tests can target either the
 * function or the method.
 */
internal fun InterventionEvent.toFhirProcedure(): JSONObject =
    buildInterventionProcedureResource(this)

/**
 * CPT codes for intervention categories where a single CPT cleanly
 * applies. Categories without a canonical CPT (ceremony, breathwork,
 * cupping/hijama/gua sha) return null — the FHIR `code.text` path
 * still carries the owner's [InterventionEvent.subType] in those
 * cases, so the export remains useful.
 *
 * BONE_SETTING has no single canonical CPT outside chiropractic
 * manipulation codes; ambiguity left as null to avoid mis-coding.
 */
internal fun cptCode(category: InterventionCategory): Pair<String, String>? = when (category) {
    InterventionCategory.MANUAL_THERAPY -> "97140" to "Manual therapy techniques"
    InterventionCategory.ACUPUNCTURE -> "97810" to "Acupuncture, 1 or more needles; without electrical stimulation"
    InterventionCategory.INFUSION_IV -> "96365" to "Intravenous infusion, for therapy, prophylaxis, or diagnosis; initial, up to 1 hour"
    InterventionCategory.EXERCISE_REHAB -> "97110" to "Therapeutic exercise"
    InterventionCategory.BONE_SETTING,
    InterventionCategory.CUPPING_HIJAMA_GUA_SHA,
    InterventionCategory.CEREMONY_RITUAL,
    InterventionCategory.BREATHWORK_MEDITATION,
    InterventionCategory.OTHER -> null
}

/**
 * TODO(#192): FHIR `CarePlan` is the closest analogue to
 * [TreatmentCourse]. Mapping requires:
 *
 *  - A coding system for `category` (Bios owner-declared traditions
 *    do not have a single canonical SNOMED concept) — pending PR #226.
 *  - Mapping the owner's free-text [TreatmentCourse.goal] to a
 *    `goal` resource with a `description.text`-only path.
 *  - Deciding whether to export contained `Procedure` entries for
 *    member sessions or rely on `Procedure.partOf` references from
 *    the existing intervention-event bundle entries.
 *
 * Deferring until #226 lands and the FHIR-export-design audit covers
 * `CarePlan` semantics versus the Bios manifesto (no "should you
 * continue your course?" advice). Today the entity round-trips inside
 * Bios; FHIR-export of courses is explicitly out of scope.
 */
@Suppress("unused")
internal fun TreatmentCourse.toFhirCarePlanTodo(): Nothing? = null
