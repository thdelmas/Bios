package com.bios.app

import com.bios.app.data.BiomarkerContext
import com.bios.app.data.BiomarkerEntryRepo
import com.bios.app.data.BiomarkerPayloadKeys
import com.bios.app.model.EventPayloadField
import com.bios.app.model.Specimen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-data tests for biomarker provenance round-tripping through the
 * `event_payloads` sidecar. The actual DB write path is exercised by the
 * existing biomarker entry surface + integration of the FhirImporter; this
 * test guards the key vocabulary and the rowsToContext mapping in
 * isolation.
 */
class BiomarkerContextTest {

    @Test
    fun `BiomarkerContext is empty when every field is null or blank`() {
        assertTrue(BiomarkerContext().isEmpty)
        assertTrue(BiomarkerContext(labName = "", note = "").isEmpty)
    }

    @Test
    fun `BiomarkerContext is not empty when any field is populated`() {
        assertFalse(BiomarkerContext(labName = "Synlab Madrid").isEmpty)
        assertFalse(BiomarkerContext(fasting = false).isEmpty)
        assertFalse(BiomarkerContext(specimen = Specimen.SERUM).isEmpty)
        assertFalse(BiomarkerContext(sourceUri = "content://x").isEmpty)
        assertFalse(BiomarkerContext(note = "post-flu").isEmpty)
    }

    @Test
    fun `rowsToContext recovers every field round-trip`() {
        val readingId = "r1"
        val rows = listOf(
            EventPayloadField(readingId, BiomarkerPayloadKeys.LAB_NAME, stringValue = "Synlab Madrid"),
            EventPayloadField(readingId, BiomarkerPayloadKeys.FASTING, longValue = 1L),
            EventPayloadField(readingId, BiomarkerPayloadKeys.SPECIMEN, stringValue = "SERUM"),
            EventPayloadField(readingId, BiomarkerPayloadKeys.SOURCE_URI, stringValue = "content://x")
        )
        val ctx = BiomarkerEntryRepo.rowsToContext(rows, note = "post-flu")
        assertEquals("Synlab Madrid", ctx.labName)
        assertEquals(true, ctx.fasting)
        assertEquals(Specimen.SERUM, ctx.specimen)
        assertEquals("content://x", ctx.sourceUri)
        assertEquals("post-flu", ctx.note)
    }

    @Test
    fun `fasting long 0 maps to false, 1 maps to true`() {
        val rid = "r"
        val fastingFalse = BiomarkerEntryRepo.rowsToContext(
            listOf(EventPayloadField(rid, BiomarkerPayloadKeys.FASTING, longValue = 0L)),
            note = null
        )
        assertEquals(false, fastingFalse.fasting)

        val fastingTrue = BiomarkerEntryRepo.rowsToContext(
            listOf(EventPayloadField(rid, BiomarkerPayloadKeys.FASTING, longValue = 1L)),
            note = null
        )
        assertEquals(true, fastingTrue.fasting)
    }

    @Test
    fun `unknown specimen name resolves to null without crashing`() {
        val ctx = BiomarkerEntryRepo.rowsToContext(
            listOf(EventPayloadField("r", BiomarkerPayloadKeys.SPECIMEN, stringValue = "BOGUS")),
            note = null
        )
        assertNull(ctx.specimen)
    }

    @Test
    fun `Specimen fromName is case-insensitive`() {
        assertEquals(Specimen.SERUM, Specimen.fromName("serum"))
        assertEquals(Specimen.PLASMA, Specimen.fromName("PLASMA"))
        assertEquals(Specimen.WHOLE_BLOOD, Specimen.fromName("Whole_Blood"))
        assertNull(Specimen.fromName(null))
        assertNull(Specimen.fromName("nonsense"))
    }

    @Test
    fun `field key vocabulary is stable`() {
        // These strings are persisted into event_payloads.fieldKey on every
        // biomarker entry. Renaming any of them is a breaking schema change.
        assertEquals("lab_name", BiomarkerPayloadKeys.LAB_NAME)
        assertEquals("fasting", BiomarkerPayloadKeys.FASTING)
        assertEquals("specimen", BiomarkerPayloadKeys.SPECIMEN)
        assertEquals("source_uri", BiomarkerPayloadKeys.SOURCE_URI)
    }
}
