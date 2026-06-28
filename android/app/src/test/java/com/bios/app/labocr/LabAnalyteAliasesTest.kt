package com.bios.app.labocr

import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the anti-drift contract for lab-report OCR: the alias resolver is
 * derived from [MetricType.entries], so a biomarker key shipped without an
 * alias must fail CI here rather than silently never resolving off a report.
 */
class LabAnalyteAliasesTest {

    private val manualBiomarkers: List<MetricType> =
        MetricType.entries.filter { it.domain == MetricDomain.BIOMARKER && it.allowsManualEntry }

    @Test
    fun everyManualBiomarkerHasAtLeastOneAlias() {
        val missing = manualBiomarkers.filter { LabAnalyteAliases.biomarkerAliases(it).isNullOrEmpty() }
        assertTrue(
            "Biomarker MetricTypes with no OCR alias (add one in LabAnalyteAliases): " +
                missing.joinToString { it.key },
            missing.isEmpty(),
        )
    }

    @Test
    fun aliasesDoNotCollideAcrossMetrics() {
        val owners = HashMap<String, MutableList<MetricType>>()
        for (type in MetricType.entries) {
            for (alias in LabAnalyteAliases.biomarkerAliases(type).orEmpty()) {
                owners.getOrPut(LabAnalyteAliases.normalise(alias)) { mutableListOf() }.add(type)
            }
        }
        val collisions = owners.filterValues { it.size > 1 }
        assertTrue(
            "Aliases normalise to the same key for multiple metrics: " +
                collisions.entries.joinToString { "${it.key} -> ${it.value.map { m -> m.key }}" },
            collisions.isEmpty(),
        )
    }

    @Test
    fun everyManualBiomarkerResolvesFromItsOwnFirstAlias() {
        for (type in manualBiomarkers) {
            val firstAlias = LabAnalyteAliases.biomarkerAliases(type)!!.first()
            val match = LabAnalyteAliases.resolve(firstAlias)
            assertNotNull("No resolve for ${type.key} via \"$firstAlias\"", match)
            assertEquals("Wrong metric for \"$firstAlias\"", type, match!!.metric)
            assertTrue("Own alias should resolve exact for ${type.key}", match.exact)
        }
    }

    @Test
    fun resolvesMultilingualNames() {
        assertEquals(MetricType.TSH, LabAnalyteAliases.resolve("Tirotropina")?.metric)
        assertEquals(MetricType.MONOCYTES_PCT, LabAnalyteAliases.resolve("Monòcits %")?.metric)
        assertEquals(MetricType.ABSOLUTE_MONOCYTE_COUNT, LabAnalyteAliases.resolve("Monòcits absoluts")?.metric)
        assertEquals(MetricType.TOTAL_CHOLESTEROL, LabAnalyteAliases.resolve("Colesterol total")?.metric)
        assertEquals(MetricType.HDL_CHOLESTEROL, LabAnalyteAliases.resolve("Colesterol HDL")?.metric)
    }

    @Test
    fun normaliseFoldsAccentsAndPercent() {
        assertEquals("monocits percent", LabAnalyteAliases.normalise("Monòcits %"))
        assertEquals("tirotropina", LabAnalyteAliases.normalise("  Tirotropina  "))
    }

    @Test
    fun unknownNameDoesNotResolve() {
        assertEquals(null, LabAnalyteAliases.resolve("Nota Real Analyte XYZ"))
    }
}
