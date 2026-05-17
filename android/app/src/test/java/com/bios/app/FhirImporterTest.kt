package com.bios.app

import com.bios.app.export.FhirImporter
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FhirImporterTest {

    // -- Reverse LOINC table --

    @Test
    fun `reverse LOINC table is the inverse of FhirExporter loincCode for every biomarker`() {
        // Epigenetic-age clocks (TruDiagnostic / similar) lack widely-adopted
        // standardized LOINC codes, so their FHIR mapping is intentionally
        // deferred. Filter them out of this round-trip check — for every
        // biomarker that *does* have a LOINC mapping, the reverse table must
        // be its inverse.
        val biomarkers = MetricType.entries
            .filter { it.domain == MetricDomain.BIOMARKER }
            .filter { com.bios.app.export.loincCode(it) != null }
        for (type in biomarkers) {
            val pair = com.bios.app.export.loincCode(type)
            assertNotNull("${type.key} should have a LOINC mapping", pair)
            val code = pair!!.first
            assertEquals(
                "Reverse lookup for $code should resolve back to ${type.key}",
                type,
                FhirImporter.loincToMetricType[code]
            )
        }
    }

    @Test
    fun `reverse LOINC table has no duplicate LOINC codes across MetricType entries`() {
        val codes = MetricType.entries.mapNotNull { com.bios.app.export.loincCode(it)?.first }
        assertEquals(
            "Each LOINC code should map to exactly one MetricType",
            codes.toSet().size,
            codes.size
        )
    }

    // -- Single Observation --

    @Test
    fun `parses a single HbA1c Observation`() {
        val json = """
            {
              "resourceType": "Observation",
              "status": "final",
              "code": {
                "coding": [
                  { "system": "http://loinc.org", "code": "4548-4", "display": "HbA1c" }
                ]
              },
              "effectiveDateTime": "2024-12-01T08:30:00Z",
              "valueQuantity": { "value": 5.4, "unit": "%", "code": "%" }
            }
        """.trimIndent()

        val summary = FhirImporter.parse(json)
        assertNull(summary.fileError)
        assertEquals(1, summary.acceptedCount)
        assertEquals(0, summary.skippedCount)
        val r = summary.accepted.single()
        assertEquals(MetricType.HBA1C, r.metricType)
        assertEquals(5.4, r.value, 1e-9)
        assertEquals(java.time.Instant.parse("2024-12-01T08:30:00Z").toEpochMilli(), r.timestamp)
    }

    // -- Bundle --

    @Test
    fun `parses a Bundle containing multiple Observations`() {
        val json = """
            {
              "resourceType": "Bundle",
              "type": "collection",
              "entry": [
                { "resource": ${observation("4548-4", 5.4, "2024-12-01T08:30:00Z")} },
                { "resource": ${observation("30522-7", 1.2, "2024-12-01T08:30:00Z")} },
                { "resource": ${observation("2089-1", 95.0, "2024-12-01T08:30:00Z")} }
              ]
            }
        """.trimIndent()

        val summary = FhirImporter.parse(json)
        assertNull(summary.fileError)
        assertEquals(3, summary.acceptedCount)
        val types = summary.accepted.map { it.metricType }.toSet()
        assertEquals(
            setOf(MetricType.HBA1C, MetricType.HSCRP, MetricType.LDL_CHOLESTEROL),
            types
        )
    }

    @Test
    fun `Bundle entries without resource or non-Observation resources are ignored silently`() {
        val json = """
            {
              "resourceType": "Bundle",
              "entry": [
                { "request": { "method": "GET", "url": "Patient/1" } },
                { "resource": { "resourceType": "Patient", "id": "1" } },
                { "resource": ${observation("4548-4", 5.4, "2024-12-01T00:00:00Z")} }
              ]
            }
        """.trimIndent()

        val summary = FhirImporter.parse(json)
        assertEquals(1, summary.acceptedCount)
        assertEquals(0, summary.skippedCount) // non-Observation entries don't show as "skipped" — they're not labs
    }

    // -- Skip + report --

    @Test
    fun `Observation without LOINC coding is skipped with reason`() {
        val json = """
            {
              "resourceType": "Observation",
              "code": { "coding": [
                { "system": "https://bios.health/metrics", "code": "hba1c" }
              ] },
              "effectiveDateTime": "2024-12-01T08:30:00Z",
              "valueQuantity": { "value": 5.4 }
            }
        """.trimIndent()

        val summary = FhirImporter.parse(json)
        assertEquals(0, summary.acceptedCount)
        assertEquals(1, summary.skippedCount)
        assertTrue(summary.skipped.single().reason.contains("LOINC"))
    }

    @Test
    fun `unmapped LOINC code is skipped and reports the code so the owner can see it`() {
        val json = observation("99999-9", 1.0, "2024-12-01T00:00:00Z")
        val summary = FhirImporter.parse(json)
        assertEquals(0, summary.acceptedCount)
        assertEquals(1, summary.skippedCount)
        val skipped = summary.skipped.single()
        assertEquals("99999-9", skipped.loincCode)
        assertTrue(skipped.reason.contains("99999-9"))
    }

    @Test
    fun `non-biomarker LOINC mapping is rejected by the entry surface`() {
        // 8867-4 maps to HEART_RATE, which is CARDIOVASCULAR not BIOMARKER.
        // The importer should reject it even though there's a valid mapping —
        // labs-only is the contract of this entry surface.
        val json = observation("8867-4", 72.0, "2024-12-01T00:00:00Z")
        val summary = FhirImporter.parse(json)
        assertEquals(0, summary.acceptedCount)
        assertEquals(1, summary.skippedCount)
        assertTrue(summary.skipped.single().reason.contains("not a biomarker"))
    }

    @Test
    fun `Observation without valueQuantity is skipped`() {
        val json = """
            {
              "resourceType": "Observation",
              "code": { "coding": [ { "system": "http://loinc.org", "code": "4548-4" } ] },
              "effectiveDateTime": "2024-12-01T00:00:00Z"
            }
        """.trimIndent()
        val summary = FhirImporter.parse(json)
        assertEquals(0, summary.acceptedCount)
        assertEquals(1, summary.skippedCount)
        assertTrue(summary.skipped.single().reason.contains("valueQuantity"))
    }

    @Test
    fun `Observation with zero or negative value is skipped`() {
        val zero = observation("4548-4", 0.0, "2024-12-01T00:00:00Z")
        val negative = observation("4548-4", -1.5, "2024-12-01T00:00:00Z")
        for (json in listOf(zero, negative)) {
            val summary = FhirImporter.parse(json)
            assertEquals(0, summary.acceptedCount)
            assertEquals(1, summary.skippedCount)
        }
    }

    @Test
    fun `Observation without any parseable date is skipped`() {
        val json = """
            {
              "resourceType": "Observation",
              "code": { "coding": [ { "system": "http://loinc.org", "code": "4548-4" } ] },
              "valueQuantity": { "value": 5.4 }
            }
        """.trimIndent()
        val summary = FhirImporter.parse(json)
        assertEquals(0, summary.acceptedCount)
        assertEquals(1, summary.skippedCount)
        assertTrue(summary.skipped.single().reason.contains("effectiveDateTime"))
    }

    @Test
    fun `falls back from effectiveDateTime to issued when effectiveDateTime is absent`() {
        val json = """
            {
              "resourceType": "Observation",
              "code": { "coding": [ { "system": "http://loinc.org", "code": "4548-4" } ] },
              "issued": "2024-12-01T10:00:00Z",
              "valueQuantity": { "value": 5.4 }
            }
        """.trimIndent()
        val summary = FhirImporter.parse(json)
        assertEquals(1, summary.acceptedCount)
        assertEquals(
            java.time.Instant.parse("2024-12-01T10:00:00Z").toEpochMilli(),
            summary.accepted.single().timestamp
        )
    }

    // -- File-level errors --

    @Test
    fun `malformed JSON returns a fileError`() {
        val summary = FhirImporter.parse("not actually json")
        assertNotNull(summary.fileError)
        assertEquals(0, summary.acceptedCount)
        assertEquals(0, summary.skippedCount)
    }

    @Test
    fun `non-FHIR root resourceType returns a fileError`() {
        val summary = FhirImporter.parse("""{ "resourceType": "Patient", "id": "abc" }""")
        assertNotNull(summary.fileError)
        assertTrue(summary.fileError!!.contains("Bundle or Observation"))
    }

    // -- Fixture helpers --

    private fun observation(loinc: String, value: Double, instant: String) = """
        {
          "resourceType": "Observation",
          "status": "final",
          "code": {
            "coding": [
              { "system": "http://loinc.org", "code": "$loinc" }
            ]
          },
          "effectiveDateTime": "$instant",
          "valueQuantity": { "value": $value }
        }
    """.trimIndent()
}
