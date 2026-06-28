package com.bios.app.labocr

import com.bios.app.model.Specimen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Header-level extraction: collection date, specimen, lab name. */
class PanelMetadataExtractorTest {

    private fun lines(vararg text: String): List<OcrLine> =
        text.mapIndexed { i, t -> OcrLine(t, page = 0, top = i) }

    private fun epochUtc(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun extractsLabelledCollectionDate() {
        val panel = PanelMetadataExtractor.extract(
            lines(
                "Laboratori Sagrat Cor",
                "Data i hora presa de la mostra: 12/03/2026 08:30",
                "Tirotropina 2.10 mU/L",
            )
        )
        assertEquals(epochUtc(2026, 3, 12), panel.collectionDate)
    }

    @Test
    fun parsesIsoDate() {
        val panel = PanelMetadataExtractor.extract(lines("Collected 2025-11-02", "Glucose 90 mg/dL"))
        assertEquals(epochUtc(2025, 11, 2), panel.collectionDate)
    }

    @Test
    fun extractsSpecimenAndLabName() {
        val panel = PanelMetadataExtractor.extract(
            lines(
                "Laboratori de Referència de Catalunya",
                "Mostra: Sèrum",
                "Fecha de toma: 01/02/2026",
            )
        )
        assertEquals(Specimen.SERUM, panel.specimen)
        assertEquals(epochUtc(2026, 2, 1), panel.collectionDate)
        assertEquals("Laboratori de Referència de Catalunya", panel.labName)
    }

    @Test
    fun neverDefaultsDateWhenAbsent() {
        val panel = PanelMetadataExtractor.extract(lines("Some header", "Hemoglobina 14 g/dL"))
        assertNull(panel.collectionDate)
    }

    @Test
    fun rejectsImpossibleDate() {
        val panel = PanelMetadataExtractor.extract(lines("Fecha de toma: 31/02/2026"))
        assertNull(panel.collectionDate)
    }
}
