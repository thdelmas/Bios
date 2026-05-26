package com.bios.app

import com.bios.app.data.BiosDatabaseMigrationsExtras
import com.bios.app.model.ImagingBodyRegion
import com.bios.app.model.ImagingModality
import com.bios.app.model.ImagingStudy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Spec test for the ImagingStudy entity shape and its v34 -> v35
 * migration. Issue #356.
 */
class ImagingStudyTest {

    @Test
    fun `imaging study generates a stable uuid and createdAt`() {
        val s = ImagingStudy(
            modality = ImagingModality.CT,
            bodyRegion = ImagingBodyRegion.HEAD,
            studyDate = 1714000000000L,
        )
        assertNotNull(s.uuid)
        assertEquals(36, s.uuid.length) // UUID standard length
        assertNotNull(s.createdAt)
    }

    @Test
    fun `imaging study report fields default to null`() {
        val s = ImagingStudy(
            modality = ImagingModality.MRI,
            bodyRegion = ImagingBodyRegion.SPINE,
            studyDate = 0L,
        )
        // All report sections optional — some imports ship only a conclusion.
        assertNull(s.indication)
        assertNull(s.facility)
        assertNull(s.reportTechnique)
        assertNull(s.reportFindings)
        assertNull(s.reportConclusion)
        assertNull(s.reportLanguage)
        assertNull(s.ownerNote)
    }

    @Test
    fun `imaging study round-trips a Sagrat Cor CT head report verbatim`() {
        // Issue #356. The Sagrat Cor discharge contained a non-contrast
        // head CT with full technique / findings / conclusion sections in
        // Spanish. Bios stores them as the clinician wrote them, no
        // translation, no summarisation.
        val s = ImagingStudy(
            modality = ImagingModality.CT,
            bodyRegion = ImagingBodyRegion.HEAD,
            studyDate = 1714000000000L,
            indication = "Cefalea holocraneal de 10 días, sospecha de patología orgánica",
            facility = "Hospital Universitari Sagrat Cor",
            reportTechnique = "Cortes axiales desde base de cráneo a la convexidad en fase simple",
            reportFindings = "Densidad parénquima cerebral y cerebeloso normal y homogénea, sin lesiones focales",
            reportConclusion = "TC cráneo sin evidencia de patología aguda al momento del estudio",
            reportLanguage = "es",
        )
        assertEquals(ImagingModality.CT, s.modality)
        assertEquals(ImagingBodyRegion.HEAD, s.bodyRegion)
        assertEquals("es", s.reportLanguage)
        assertEquals("Hospital Universitari Sagrat Cor", s.facility)
    }

    @Test
    fun `two imaging studies have unique uuids by default`() {
        val a = ImagingStudy(
            modality = ImagingModality.CT,
            bodyRegion = ImagingBodyRegion.HEAD,
            studyDate = 0L,
        )
        val b = ImagingStudy(
            modality = ImagingModality.CT,
            bodyRegion = ImagingBodyRegion.HEAD,
            studyDate = 0L,
        )
        assertNotEquals(a.uuid, b.uuid)
    }

    @Test
    fun `MIGRATION_34_35 covers the right schema versions`() {
        val m = BiosDatabaseMigrationsExtras.MIGRATION_34_35
        assertEquals(34, m.startVersion)
        assertEquals(35, m.endVersion)
    }

    @Test
    fun `modality and body region enums cover the expected vocabulary`() {
        // Room persists enum entries by name; renaming would invalidate
        // existing rows. Pin the value-set.
        assertEquals(
            setOf("CT", "MRI", "XRAY", "ULTRASOUND", "PET", "DEXA", "MAMMOGRAM", "ENDOSCOPY", "OTHER"),
            ImagingModality.entries.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("HEAD", "NECK", "CHEST", "ABDOMEN", "PELVIS", "SPINE", "EXTREMITY", "WHOLE_BODY", "OTHER"),
            ImagingBodyRegion.entries.map { it.name }.toSet(),
        )
    }
}
