package com.bios.app.labocr

import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural tests for the per-line extractor — the parsing heart of
 * lab-report OCR. Covers analyte resolution past digits-in-names, value
 * selection, the unit-guard skip, locale decimals, and confidence tiers.
 */
class LabLineExtractorTest {

    private fun reading(line: String): ExtractedReading {
        val outcome = LabLineExtractor.extract(line)
        assertTrue("Expected a Reading for: $line (got $outcome)", outcome is LabLineExtractor.LineOutcome.Reading)
        return (outcome as LabLineExtractor.LineOutcome.Reading).reading
    }

    @Test
    fun parsesValueUnitAndReferenceRange() {
        val r = reading("Tirotropina 2.10 mU/L 0.550 - 4.780")
        assertEquals(MetricType.TSH, r.metricType)
        assertEquals(2.10, r.value, 1e-9)
        assertEquals(Confidence.HIGH, r.confidence)
    }

    @Test
    fun resolvesLongerNameOverShorter() {
        val r = reading("Colesterol HDL 55 mg/dL")
        assertEquals(MetricType.HDL_CHOLESTEROL, r.metricType)
        assertEquals(55.0, r.value, 1e-9)
    }

    @Test
    fun digitsInAnalyteNameAreNotTheValue() {
        val r = reading("Vitamina B12 350 pg/mL")
        assertEquals(MetricType.VITAMIN_B12, r.metricType)
        assertEquals(350.0, r.value, 1e-9)
    }

    @Test
    fun honoursLocaleDecimalComma() {
        val r = reading("Hematocrit 41,5 %")
        assertEquals(MetricType.HEMATOCRIT, r.metricType)
        assertEquals(41.5, r.value, 1e-9)
    }

    @Test
    fun convertsThroughSharedUnitTable() {
        // CRP stored in mg/L; a report in mg/dL must convert ×10, same as FHIR.
        val r = reading("PCR 0.5 mg/dL")
        assertEquals(MetricType.CRP, r.metricType)
        assertEquals(5.0, r.value, 1e-9)
    }

    @Test
    fun incompatibleUnitSkipsAndReports() {
        val outcome = LabLineExtractor.extract("TSH 2.1 mg/dL")
        assertTrue(outcome is LabLineExtractor.LineOutcome.Skipped)
        val reason = (outcome as LabLineExtractor.LineOutcome.Skipped).skipped.reason
        assertTrue("Reason should name the unit clash: $reason", reason.contains("not compatible"))
    }

    @Test
    fun unitlessLineIsLowConfidence() {
        val r = reading("Ferritina 120")
        assertEquals(MetricType.FERRITIN, r.metricType)
        assertEquals(Confidence.LOW, r.confidence)
    }

    @Test
    fun magnitudeOutlierDropsToLowConfidence() {
        // A value three orders outside the printed range = dropped decimal point.
        val r = reading("Creatinina 880 mg/dL 0.6 - 1.2")
        assertEquals(MetricType.CREATININE, r.metricType)
        assertEquals(Confidence.LOW, r.confidence)
    }

    @Test
    fun unmappedAnalyteWithNumberIsSkipped() {
        val outcome = LabLineExtractor.extract("Glucosa 95 mg/dL")
        assertTrue(outcome is LabLineExtractor.LineOutcome.Skipped)
    }

    @Test
    fun structuralLineIsIgnored() {
        assertEquals(LabLineExtractor.LineOutcome.Ignored, LabLineExtractor.extract("Hemograma"))
    }

    @Test
    fun blankLineIsIgnored() {
        assertEquals(LabLineExtractor.LineOutcome.Ignored, LabLineExtractor.extract("   "))
    }
}
