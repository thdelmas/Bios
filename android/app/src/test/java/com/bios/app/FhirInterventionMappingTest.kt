package com.bios.app

import com.bios.app.export.buildInterventionProcedureResource
import com.bios.app.export.cptCode
import com.bios.app.export.toFhirProcedure
import com.bios.app.model.InterventionCategory
import com.bios.app.model.InterventionEvent
import com.bios.app.model.MedicalTradition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FHIR R4 `Procedure` mapping tests for [InterventionEvent] (#192).
 *
 * The mapping must:
 *  - emit `resourceType = Procedure` with `status = completed`
 *  - carry the Bios category coding under our local system
 *  - emit a CPT coding when the category has one (97140, 97810,
 *    96365, 97110) and omit the coding array for categories without
 *    a CPT (ceremony, breathwork, cupping/hijama, bone-setting, other)
 *  - always populate `code.text` so receivers without our category
 *    vocabulary still see a human-readable label
 *  - thread the parent treatment course through `partOf`
 *  - thread the practitioner tradition through a Bios extension
 */
class FhirInterventionMappingTest {

    @Test
    fun mapping_emits_procedure_resource_with_completed_status() {
        val event = InterventionEvent(
            id = "ev-1",
            timestamp = 1_700_000_000_000L,
            category = InterventionCategory.ACUPUNCTURE,
            subType = "8-needle Saam",
        )
        val resource = buildInterventionProcedureResource(event)
        assertEquals("Procedure", resource.getString("resourceType"))
        assertEquals("ev-1", resource.getString("id"))
        assertEquals("completed", resource.getString("status"))
    }

    @Test
    fun mapping_emits_bios_category_coding() {
        val event = InterventionEvent(
            timestamp = 1L,
            category = InterventionCategory.MANUAL_THERAPY,
        )
        val resource = buildInterventionProcedureResource(event)
        val coding = resource.getJSONObject("category").getJSONArray("coding").getJSONObject(0)
        assertEquals("https://bios.health/intervention-category", coding.getString("system"))
        assertEquals("MANUAL_THERAPY", coding.getString("code"))
        assertEquals("Manual therapy / bodywork", coding.getString("display"))
    }

    @Test
    fun mapping_emits_cpt_coding_for_manual_therapy() {
        val event = InterventionEvent(
            id = "ev-mt",
            timestamp = 1L,
            category = InterventionCategory.MANUAL_THERAPY,
            subType = "OMM lumbar HVLA",
        )
        val resource = buildInterventionProcedureResource(event)
        val coding = resource.getJSONObject("code").getJSONArray("coding").getJSONObject(0)
        assertEquals("http://www.ama-assn.org/go/cpt", coding.getString("system"))
        assertEquals("97140", coding.getString("code"))
        // code.text always carries owner subType
        assertEquals("OMM lumbar HVLA", resource.getJSONObject("code").getString("text"))
    }

    @Test
    fun mapping_emits_cpt_coding_for_acupuncture() {
        val event = InterventionEvent(timestamp = 1L, category = InterventionCategory.ACUPUNCTURE)
        val resource = buildInterventionProcedureResource(event)
        assertEquals(
            "97810",
            resource.getJSONObject("code").getJSONArray("coding").getJSONObject(0).getString("code")
        )
    }

    @Test
    fun mapping_omits_cpt_coding_for_ceremony() {
        val event = InterventionEvent(
            timestamp = 1L,
            category = InterventionCategory.CEREMONY_RITUAL,
            subType = "Hoʻoponopono session",
        )
        val resource = buildInterventionProcedureResource(event)
        val code = resource.getJSONObject("code")
        // No coding array because no canonical CPT for ceremony.
        assertFalse(
            "Ceremony must not carry a CPT coding (mis-coding risk)",
            code.has("coding")
        )
        // Owner subType still surfaces via code.text.
        assertEquals("Hoʻoponopono session", code.getString("text"))
    }

    @Test
    fun mapping_omits_cpt_coding_for_cupping_hijama_gua_sha() {
        val event = InterventionEvent(timestamp = 1L, category = InterventionCategory.CUPPING_HIJAMA_GUA_SHA)
        val resource = buildInterventionProcedureResource(event)
        assertFalse(resource.getJSONObject("code").has("coding"))
        // Falls back to category display name when subType is null.
        assertEquals("Cupping / gua sha / hijama", resource.getJSONObject("code").getString("text"))
    }

    @Test
    fun mapping_omits_cpt_coding_for_bone_setting() {
        // BONE_SETTING intentionally has no canonical CPT — the spectrum
        // from traditional bone-setting to chiropractic manipulation
        // makes any single code ambiguous.
        val event = InterventionEvent(timestamp = 1L, category = InterventionCategory.BONE_SETTING)
        val resource = buildInterventionProcedureResource(event)
        assertFalse(resource.getJSONObject("code").has("coding"))
    }

    @Test
    fun mapping_emits_cpt_for_iv_therapy() {
        val event = InterventionEvent(timestamp = 1L, category = InterventionCategory.INFUSION_IV)
        val resource = buildInterventionProcedureResource(event)
        assertEquals(
            "96365",
            resource.getJSONObject("code").getJSONArray("coding").getJSONObject(0).getString("code")
        )
    }

    @Test
    fun mapping_emits_cpt_for_exercise_rehab() {
        val event = InterventionEvent(timestamp = 1L, category = InterventionCategory.EXERCISE_REHAB)
        val resource = buildInterventionProcedureResource(event)
        assertEquals(
            "97110",
            resource.getJSONObject("code").getJSONArray("coding").getJSONObject(0).getString("code")
        )
    }

    @Test
    fun mapping_threads_body_region_and_notes_through_verbatim() {
        val event = InterventionEvent(
            timestamp = 1L,
            category = InterventionCategory.MANUAL_THERAPY,
            bodyRegion = "left shoulder",
            notes = "post-session: improved ROM",
        )
        val resource = buildInterventionProcedureResource(event)
        assertEquals(
            "left shoulder",
            resource.getJSONArray("bodySite").getJSONObject(0).getString("text")
        )
        assertEquals(
            "post-session: improved ROM",
            resource.getJSONArray("note").getJSONObject(0).getString("text")
        )
    }

    @Test
    fun mapping_omits_body_region_and_notes_when_null_or_blank() {
        val event = InterventionEvent(
            timestamp = 1L,
            category = InterventionCategory.OTHER,
            bodyRegion = null,
            notes = "   ",
        )
        val resource = buildInterventionProcedureResource(event)
        assertFalse(resource.has("bodySite"))
        assertFalse(resource.has("note"))
    }

    @Test
    fun mapping_links_to_parent_treatment_course_via_partOf() {
        val event = InterventionEvent(
            timestamp = 1L,
            category = InterventionCategory.MANUAL_THERAPY,
            treatmentCourseId = "course-abc",
        )
        val resource = buildInterventionProcedureResource(event)
        val partOf = resource.getJSONArray("partOf").getJSONObject(0)
        assertEquals("TreatmentCourse/course-abc", partOf.getString("reference"))
    }

    @Test
    fun mapping_emits_practitioner_tradition_as_bios_extension() {
        val event = InterventionEvent(
            timestamp = 1L,
            category = InterventionCategory.MANUAL_THERAPY,
            practitionerTradition = MedicalTradition.SOWA_RIGPA,
        )
        val resource = buildInterventionProcedureResource(event)
        val ext = resource.getJSONArray("extension").getJSONObject(0)
        assertEquals("https://bios.health/fhir/practitioner-tradition", ext.getString("url"))
        assertEquals("SOWA_RIGPA", ext.getString("valueString"))
    }

    @Test
    fun mapping_emits_effective_date_in_iso_format() {
        val event = InterventionEvent(
            timestamp = 1_700_000_000_000L,
            category = InterventionCategory.ACUPUNCTURE,
        )
        val resource = buildInterventionProcedureResource(event)
        val effective = resource.getString("performedDateTime")
        // ISO-8601 with 'T' separator and trailing 'Z' (UTC).
        assertTrue(effective.contains("T"))
        assertTrue("Expected UTC suffix, got $effective", effective.endsWith("Z") || effective.contains("+00:00"))
    }

    @Test
    fun toFhirProcedure_extension_matches_function() {
        val event = InterventionEvent(
            id = "ev-equiv",
            timestamp = 1L,
            category = InterventionCategory.ACUPUNCTURE,
            subType = "Tuina",
        )
        val viaFunction = buildInterventionProcedureResource(event).toString()
        val viaExtension = event.toFhirProcedure().toString()
        assertEquals(viaFunction, viaExtension)
    }

    // -- CPT lookup table sanity --

    @Test
    fun cpt_lookup_returns_null_for_no_canonical_code() {
        assertNull(cptCode(InterventionCategory.CEREMONY_RITUAL))
        assertNull(cptCode(InterventionCategory.BREATHWORK_MEDITATION))
        assertNull(cptCode(InterventionCategory.CUPPING_HIJAMA_GUA_SHA))
        assertNull(cptCode(InterventionCategory.BONE_SETTING))
        assertNull(cptCode(InterventionCategory.OTHER))
    }

    @Test
    fun cpt_lookup_returns_canonical_codes() {
        assertEquals("97140", cptCode(InterventionCategory.MANUAL_THERAPY)?.first)
        assertEquals("97810", cptCode(InterventionCategory.ACUPUNCTURE)?.first)
        assertEquals("96365", cptCode(InterventionCategory.INFUSION_IV)?.first)
        assertEquals("97110", cptCode(InterventionCategory.EXERCISE_REHAB)?.first)
        // Sanity: every emitted display is non-empty.
        assertNotNull(cptCode(InterventionCategory.MANUAL_THERAPY)?.second?.takeIf { it.isNotBlank() })
    }
}
