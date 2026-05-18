package com.bios.app

import com.bios.app.data.BiomarkerContext
import com.bios.app.export.FhirImporter
import com.bios.app.export.buildBundleResource
import com.bios.app.export.buildObservationResource
import com.bios.app.export.bundleEntry
import com.bios.app.model.MetricReading
import com.bios.app.model.Specimen
import com.bios.contracts.MetricType
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip + asymmetric coverage for biomarker provenance (#105).
 *
 *  - Exporter side: every shape `BiomarkerContext` can express must encode
 *    the right FHIR R4 elements (performer.display, fasting component on
 *    LOINC 49541-6, contained Specimen with SCT type) and, critically,
 *    must *not* leak the owner-recall `note` or local `sourceUri`.
 *  - Importer side: every shape we accept from real lab exports
 *    (`valueBoolean`, LOINC answer codes, free text; contained / Bundle /
 *    text-only Specimens; dangling references) must resolve to the
 *    expected `BiomarkerContext` fields.
 *  - Round trip: Bios-emitted Bundle → `FhirImporter.parse` → context
 *    fields survive intact. Guards against export and import drifting
 *    apart even if each side stays internally consistent.
 *
 * Split out of `FhirImporterTest` / `FhirExporterTest` to keep both
 * companion files under the 500-line file-length cap.
 */
class FhirProvenanceRoundTripTest {

    // --- Exporter: empty / missing context emits a clean Observation ---

    @Test
    fun `biomarker Observation with null context emits no performer, component, or specimen`() {
        // Anti-regression: a missing context must produce a clean Observation
        // identical to the pre-#105 shape. Otherwise existing FHIR consumers
        // start seeing empty performer arrays or stub specimens, both invalid.
        val obs = buildObservationResource(hba1c(), MetricType.HBA1C, context = null)
        assertFalse(obs.has("performer"))
        assertFalse(obs.has("component"))
        assertFalse(obs.has("specimen"))
        assertFalse(obs.has("contained"))
    }

    @Test
    fun `empty context emits nothing (matches null-context shape)`() {
        // BiomarkerContext.isEmpty owners — value + date only — produce the
        // same clean Observation. Don't emit empty arrays.
        val obs = buildObservationResource(hba1c(), MetricType.HBA1C, context = BiomarkerContext())
        assertFalse(obs.has("performer"))
        assertFalse(obs.has("component"))
        assertFalse(obs.has("specimen"))
    }

    // --- Exporter: each context field maps to the right FHIR element ---

    @Test
    fun `lab name populates performer display`() {
        val obs = buildObservationResource(
            hba1c(),
            MetricType.HBA1C,
            context = BiomarkerContext(labName = "Synlab Madrid"),
        )
        val performers = obs.optJSONArray("performer")!!
        assertEquals(1, performers.length())
        assertEquals("Synlab Madrid", performers.getJSONObject(0).optString("display"))
        // Lab name alone — fasting/specimen stay off.
        assertFalse(obs.has("component"))
        assertFalse(obs.has("specimen"))
    }

    @Test
    fun `fasting=true emits LOINC 49541-6 component with valueBoolean`() {
        val obs = buildObservationResource(
            hba1c(),
            MetricType.HBA1C,
            context = BiomarkerContext(fasting = true),
        )
        val components = obs.optJSONArray("component")!!
        assertEquals(1, components.length())
        val first = components.getJSONObject(0)
        val coding = first.getJSONObject("code").getJSONArray("coding").getJSONObject(0)
        assertEquals("http://loinc.org", coding.getString("system"))
        assertEquals("49541-6", coding.getString("code"))
        assertEquals(true, first.getBoolean("valueBoolean"))
    }

    @Test
    fun `specimen emits a contained Specimen with the matching SCT code`() {
        val obs = buildObservationResource(
            hba1c(id = "obs-1"),
            MetricType.HBA1C,
            context = BiomarkerContext(specimen = Specimen.SERUM),
        )
        // Observation.specimen is a reference into contained — never a
        // dangling pointer.
        val reference = obs.getJSONObject("specimen").getString("reference")
        assertEquals("#specimen-obs-1", reference)
        val contained = obs.getJSONArray("contained")
        assertEquals(1, contained.length())
        val resource = contained.getJSONObject(0)
        assertEquals("Specimen", resource.getString("resourceType"))
        assertEquals("specimen-obs-1", resource.getString("id"))
        val typeCoding = resource.getJSONObject("type").getJSONArray("coding").getJSONObject(0)
        assertEquals("http://snomed.info/sct", typeCoding.getString("system"))
        assertEquals("119364003", typeCoding.getString("code"))
    }

    // --- Exporter: shareability filter — note and sourceUri stay local ---

    @Test
    fun `note field is intentionally not emitted (owner-recall only)`() {
        // PR #103 framing: the free-text note is owner-recall, never shared.
        // Even when context.note is populated, no Observation field carries it.
        val obs = buildObservationResource(
            hba1c(),
            MetricType.HBA1C,
            context = BiomarkerContext(note = "Had coffee 7am, possibly broke fast"),
        )
        assertFalse(obs.has("note"))
        assertFalse(obs.toString().contains("Had coffee"))
    }

    @Test
    fun `source URI is intentionally not emitted (local URI, useless remotely)`() {
        // A content:// or file:// URI from BiomarkerContext.sourceUri only
        // resolves on the source device. Emitting it would trade a privacy
        // smell for zero remote utility.
        val obs = buildObservationResource(
            hba1c(),
            MetricType.HBA1C,
            context = BiomarkerContext(sourceUri = "content://com.bios.app/labs/123.pdf"),
        )
        assertFalse(obs.toString().contains("content://"))
    }

    // --- Importer: fasting in every encoding clinical systems emit ---

    @Test
    fun `fasting valueBoolean true populates fasting=true`() {
        // What Bios's own exporter emits — the simplest FHIR encoding that
        // round-trips Boolean storage cleanly.
        val summary = FhirImporter.parse(observationWithFastingValueBoolean(true))
        assertEquals(1, summary.acceptedCount)
        assertEquals(true, summary.accepted.single().fasting)
    }

    @Test
    fun `fasting valueBoolean false populates fasting=false`() {
        val summary = FhirImporter.parse(observationWithFastingValueBoolean(false))
        assertEquals(java.lang.Boolean.FALSE, summary.accepted.single().fasting)
    }

    @Test
    fun `fasting valueCodeableConcept LA33-6 maps to true (clinical-systems form)`() {
        val summary = FhirImporter.parse(observationWithFastingCoding("LA33-6"))
        assertEquals(true, summary.accepted.single().fasting)
    }

    @Test
    fun `fasting valueCodeableConcept LA32-8 maps to false`() {
        assertEquals(
            java.lang.Boolean.FALSE,
            FhirImporter.parse(observationWithFastingCoding("LA32-8")).accepted.single().fasting,
        )
    }

    @Test
    fun `fasting LA46-7 (Don't know) leaves fasting null rather than guessing`() {
        // The "Don't know" answer in LOINC LL1815-1. We don't bias toward
        // either truth value when the source explicitly said it didn't know.
        assertNull(FhirImporter.parse(observationWithFastingCoding("LA46-7")).accepted.single().fasting)
    }

    @Test
    fun `fasting text 'Yes' falls through codings to give true`() {
        // Some EU public-system exports skip the LOINC answer code and use
        // free text on valueCodeableConcept.text. Match the human answer.
        assertEquals(true, FhirImporter.parse(observationWithFastingText("Yes, fasting > 8 hrs")).accepted.single().fasting)
    }

    @Test
    fun `missing fasting component leaves fasting null`() {
        assertNull(FhirImporter.parse(plainHbA1c()).accepted.single().fasting)
    }

    // --- Importer: specimen across contained / Bundle / text-only / dangling ---

    @Test
    fun `specimen reference to contained SCT-coded Specimen maps to enum`() {
        val json = """
            {
              "resourceType": "Observation",
              "status": "final",
              "code": { "coding": [{ "system": "http://loinc.org", "code": "4548-4" }] },
              "effectiveDateTime": "2024-12-01T08:30:00Z",
              "valueQuantity": { "value": 5.4 },
              "contained": [{
                "resourceType": "Specimen",
                "id": "spec-1",
                "type": {
                  "coding": [{
                    "system": "http://snomed.info/sct",
                    "code": "119364003",
                    "display": "Serum specimen"
                  }]
                }
              }],
              "specimen": { "reference": "#spec-1" }
            }
        """.trimIndent()
        assertEquals(Specimen.SERUM, FhirImporter.parse(json).accepted.single().specimen)
    }

    @Test
    fun `Bundle-level Specimen referenced by Observation maps to enum`() {
        // External Specimen resource in the same Bundle — common shape from
        // clinical lab systems that don't use contained resources.
        val json = """
            {
              "resourceType": "Bundle",
              "type": "collection",
              "entry": [
                {
                  "resource": {
                    "resourceType": "Specimen",
                    "id": "plasma-ref",
                    "type": {
                      "coding": [{
                        "system": "http://snomed.info/sct",
                        "code": "119361006"
                      }]
                    }
                  }
                },
                {
                  "resource": {
                    "resourceType": "Observation",
                    "status": "final",
                    "code": { "coding": [{ "system": "http://loinc.org", "code": "4548-4" }] },
                    "effectiveDateTime": "2024-12-01T08:30:00Z",
                    "valueQuantity": { "value": 5.4 },
                    "specimen": { "reference": "Specimen/plasma-ref" }
                  }
                }
              ]
            }
        """.trimIndent()
        assertEquals(Specimen.PLASMA, FhirImporter.parse(json).accepted.single().specimen)
    }

    @Test
    fun `Specimen with no codings falls back to type text matching`() {
        // EU public-system exports often skip the coding[] and put the
        // specimen type in free text only.
        assertEquals(
            Specimen.WHOLE_BLOOD,
            FhirImporter.parse(observationWithSpecimenText("Whole blood, EDTA tube")).accepted.single().specimen,
        )
    }

    @Test
    fun `Specimen with unrecognised text falls back to OTHER rather than null`() {
        // Preserves the fact that *some* specimen was reported — better than
        // collapsing to "unknown" silently.
        assertEquals(
            Specimen.OTHER,
            FhirImporter.parse(observationWithSpecimenText("Cerebrospinal fluid")).accepted.single().specimen,
        )
    }

    @Test
    fun `dangling specimen reference resolves to null without crashing`() {
        // Reference points at an id that doesn't exist anywhere in the
        // Bundle or contained list. Don't fabricate a specimen.
        val json = """
            {
              "resourceType": "Observation",
              "status": "final",
              "code": { "coding": [{ "system": "http://loinc.org", "code": "4548-4" }] },
              "effectiveDateTime": "2024-12-01T08:30:00Z",
              "valueQuantity": { "value": 5.4 },
              "specimen": { "reference": "Specimen/does-not-exist" }
            }
        """.trimIndent()
        assertNull(FhirImporter.parse(json).accepted.single().specimen)
    }

    @Test
    fun `missing specimen reference leaves specimen null`() {
        assertNull(FhirImporter.parse(plainHbA1c()).accepted.single().specimen)
    }

    // --- Round trip: Bios-emitted Bundle → FhirImporter.parse → context recovered ---

    @Test
    fun `full-context Observation survives export then reimport`() {
        // Wraps the Observation in a Bundle so the importer's parse() path
        // exercises the same code that processes real lab exports.
        val obs = buildObservationResource(
            hba1c(id = "rt-obs"),
            MetricType.HBA1C,
            context = BiomarkerContext(
                labName = "Quest Diagnostics",
                fasting = true,
                specimen = Specimen.PLASMA,
            ),
        )
        val bundle = buildBundleResource(JSONArray().put(bundleEntry(obs))).toString()

        val summary = FhirImporter.parse(bundle)
        assertEquals(0, summary.skippedCount)
        assertEquals(1, summary.acceptedCount)
        val recovered = summary.accepted.single()
        assertEquals(MetricType.HBA1C, recovered.metricType)
        assertEquals(5.4, recovered.value, 0.0)
        assertEquals("Quest Diagnostics", recovered.labName)
        assertEquals(true, recovered.fasting)
        assertEquals(Specimen.PLASMA, recovered.specimen)
    }

    @Test
    fun `empty-context Observation survives export then reimport with all context null`() {
        // Regression guard for the bare-entry shape — value + date only
        // round-trips cleanly without inventing context.
        val obs = buildObservationResource(hba1c(id = "rt-empty"), MetricType.HBA1C, context = null)
        val bundle = buildBundleResource(JSONArray().put(bundleEntry(obs))).toString()

        val summary = FhirImporter.parse(bundle)
        assertEquals(0, summary.skippedCount)
        val recovered = summary.accepted.single()
        assertNull(recovered.labName)
        assertNull(recovered.fasting)
        assertNull(recovered.specimen)
    }

    // --- Fixtures ---

    private fun hba1c(id: String = "test-id"): MetricReading = MetricReading(
        id = id,
        metricType = MetricType.HBA1C.key,
        value = 5.4,
        timestamp = 1_733_044_200_000L, // 2024-12-01T08:30:00Z
        sourceId = "source-1",
        confidence = 90,
    )

    private fun plainHbA1c(): String = """
        {
          "resourceType": "Observation",
          "status": "final",
          "code": { "coding": [{ "system": "http://loinc.org", "code": "4548-4" }] },
          "effectiveDateTime": "2024-12-01T08:30:00Z",
          "valueQuantity": { "value": 5.4 }
        }
    """.trimIndent()

    private fun observationWithFastingValueBoolean(fasting: Boolean): String = """
        {
          "resourceType": "Observation",
          "status": "final",
          "code": { "coding": [{ "system": "http://loinc.org", "code": "4548-4" }] },
          "effectiveDateTime": "2024-12-01T08:30:00Z",
          "valueQuantity": { "value": 5.4 },
          "component": [{
            "code": { "coding": [{ "system": "http://loinc.org", "code": "49541-6" }] },
            "valueBoolean": $fasting
          }]
        }
    """.trimIndent()

    private fun observationWithFastingCoding(code: String): String = """
        {
          "resourceType": "Observation",
          "status": "final",
          "code": { "coding": [{ "system": "http://loinc.org", "code": "4548-4" }] },
          "effectiveDateTime": "2024-12-01T08:30:00Z",
          "valueQuantity": { "value": 5.4 },
          "component": [{
            "code": { "coding": [{ "system": "http://loinc.org", "code": "49541-6" }] },
            "valueCodeableConcept": {
              "coding": [{ "system": "http://loinc.org", "code": "$code" }]
            }
          }]
        }
    """.trimIndent()

    private fun observationWithFastingText(text: String): String = """
        {
          "resourceType": "Observation",
          "status": "final",
          "code": { "coding": [{ "system": "http://loinc.org", "code": "4548-4" }] },
          "effectiveDateTime": "2024-12-01T08:30:00Z",
          "valueQuantity": { "value": 5.4 },
          "component": [{
            "code": { "coding": [{ "system": "http://loinc.org", "code": "49541-6" }] },
            "valueCodeableConcept": { "text": "$text" }
          }]
        }
    """.trimIndent()

    private fun observationWithSpecimenText(text: String): String = """
        {
          "resourceType": "Observation",
          "status": "final",
          "code": { "coding": [{ "system": "http://loinc.org", "code": "4548-4" }] },
          "effectiveDateTime": "2024-12-01T08:30:00Z",
          "valueQuantity": { "value": 5.4 },
          "contained": [{
            "resourceType": "Specimen",
            "id": "spec-text",
            "type": { "text": "$text" }
          }],
          "specimen": { "reference": "#spec-text" }
        }
    """.trimIndent()
}
