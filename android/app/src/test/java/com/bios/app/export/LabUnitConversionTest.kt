package com.bios.app.export

import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared unit-reconciliation table that FHIR import and lab-report OCR
 * both depend on — so a value imports identically whichever door it comes
 * through (issue #354 mg/dL↔mg/L, plus the OCR unit gazetteer).
 */
class LabUnitConversionTest {

    @Test
    fun convertsMgPerDlToMgPerLForCrp() {
        val result = normalizeToCanonicalUnit(MetricType.CRP, "mg/dL", 0.5)
        assertTrue(result is CanonicalUnit.Ok)
        assertEquals(5.0, (result as CanonicalUnit.Ok).value, 1e-9)
    }

    @Test
    fun passesThroughMatchingUnit() {
        val result = normalizeToCanonicalUnit(MetricType.TOTAL_CHOLESTEROL, "mg/dL", 190.0)
        assertEquals(190.0, (result as CanonicalUnit.Ok).value, 1e-9)
    }

    @Test
    fun trustsResolutionWhenUnitAbsent() {
        val result = normalizeToCanonicalUnit(MetricType.FERRITIN, null, 120.0)
        assertEquals(120.0, (result as CanonicalUnit.Ok).value, 1e-9)
    }

    @Test
    fun failsClosedOnIncompatibleUnit() {
        val result = normalizeToCanonicalUnit(MetricType.TSH, "mg/dL", 2.0)
        assertTrue(result is CanonicalUnit.Incompatible)
    }

    @Test
    fun canonicalisesRealReportSpellings() {
        assertEquals("m[IU]/L", canonicaliseUnitToken("mU/L"))
        assertEquals("mg/dL", canonicaliseUnitToken("mg/dl"))
        assertEquals("10*9/L", canonicaliseUnitToken("10E9/L"))
    }

    @Test
    fun distinguishesUnitsFromResultFlags() {
        assertTrue(isKnownUnitToken("mg/dL"))
        assertTrue(isKnownUnitToken("%"))
        assertTrue(isKnownUnitToken("fL"))
        assertFalse(isKnownUnitToken("H"))
        assertFalse(isKnownUnitToken("alto"))
    }
}
