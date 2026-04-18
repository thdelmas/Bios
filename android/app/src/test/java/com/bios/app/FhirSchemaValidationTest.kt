package com.bios.app

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.parser.StrictErrorHandler
import com.bios.app.export.buildBaselineObservation
import com.bios.app.export.buildBundleResource
import com.bios.app.export.buildDetectedIssueResource
import com.bios.app.export.buildDeviceResource
import com.bios.app.export.buildObservationResource
import com.bios.app.export.bundleEntry
import com.bios.app.model.Anomaly
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import com.bios.app.model.PersonalBaseline
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.DetectedIssue
import org.hl7.fhir.r4.model.Device
import org.hl7.fhir.r4.model.Observation
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.BeforeClass

/**
 * Validates emitted resources against the FHIR R4 spec by round-tripping them
 * through HAPI FHIR's strict JSON parser. The parser rejects unknown elements,
 * missing required fields, and type mismatches — any regression in
 * FhirExporter that breaks FHIR compliance will fail here.
 */
class FhirSchemaValidationTest {

    private fun assertParsesAsR4(label: String, json: String, expectedType: Class<*>) {
        val resource = try {
            parser.parseResource(json)
        } catch (e: Exception) {
            throw AssertionError(
                "$label failed FHIR R4 strict parse: ${e.message}\n--- JSON ---\n$json",
                e
            )
        }
        assertEquals("$label parsed as wrong resource type", expectedType, resource::class.java)
    }

    private fun sampleSource() = DataSource(
        id = "src-1",
        sourceType = "health_connect",
        deviceName = "Pixel 9a",
        deviceModel = "tegu",
        sensorType = "OPTICAL_HR"
    )

    private fun sampleReading() = MetricReading(
        id = "obs-1",
        metricType = MetricType.HEART_RATE.key,
        value = 72.0,
        timestamp = 1_700_000_000_000L,
        sourceId = "src-1",
        confidence = 2
    )

    private fun sampleBaseline() = PersonalBaseline(
        id = "baseline-1",
        metricType = MetricType.HEART_RATE.key,
        mean = 68.0,
        stdDev = 4.2,
        p5 = 60.0,
        p95 = 78.0
    )

    private fun sampleAnomaly(acknowledged: Boolean = false, withPattern: Boolean = true) = Anomaly(
        id = "anomaly-1",
        metricTypes = "[\"heart_rate\"]",
        deviationScores = "{\"heart_rate\": 2.3}",
        combinedScore = 2.3,
        patternId = if (withPattern) "pattern-cold" else null,
        severity = 2,
        title = "Elevated resting HR",
        explanation = "Resting HR 2.3 sigma above personal baseline for 18h.",
        acknowledged = acknowledged
    )

    @Test
    fun `Device resource conforms to FHIR R4`() {
        assertParsesAsR4("Device", buildDeviceResource(sampleSource()).toString(), Device::class.java)
    }

    @Test
    fun `Observation resource conforms to FHIR R4`() {
        assertParsesAsR4(
            "Observation",
            buildObservationResource(sampleReading(), MetricType.HEART_RATE).toString(),
            Observation::class.java
        )
    }

    @Test
    fun `Observation for metric without LOINC mapping still conforms`() {
        // TYPING_CADENCE is a companion-injected metric — no LOINC code exists.
        val reading = sampleReading().copy(metricType = MetricType.TYPING_CADENCE.key, value = 0.7)
        assertParsesAsR4(
            "Observation (no LOINC)",
            buildObservationResource(reading, MetricType.TYPING_CADENCE).toString(),
            Observation::class.java
        )
    }

    @Test
    fun `Baseline Observation conforms to FHIR R4`() {
        assertParsesAsR4(
            "Baseline Observation",
            buildBaselineObservation(sampleBaseline()).toString(),
            Observation::class.java
        )
    }

    @Test
    fun `DetectedIssue conforms to FHIR R4 (preliminary)`() {
        assertParsesAsR4(
            "DetectedIssue preliminary",
            buildDetectedIssueResource(sampleAnomaly()).toString(),
            DetectedIssue::class.java
        )
    }

    @Test
    fun `DetectedIssue conforms to FHIR R4 (final, no pattern)`() {
        val anomaly = sampleAnomaly(acknowledged = true, withPattern = false)
        assertParsesAsR4(
            "DetectedIssue final",
            buildDetectedIssueResource(anomaly).toString(),
            DetectedIssue::class.java
        )
    }

    @Test
    fun `Empty Bundle conforms to FHIR R4`() {
        assertParsesAsR4(
            "Empty Bundle",
            buildBundleResource(JSONArray()).toString(),
            Bundle::class.java
        )
    }

    @Test
    fun `Full Bundle with every resource type conforms to FHIR R4`() {
        val entries = JSONArray()
            .put(bundleEntry(buildDeviceResource(sampleSource())))
            .put(bundleEntry(buildObservationResource(sampleReading(), MetricType.HEART_RATE)))
            .put(bundleEntry(buildBaselineObservation(sampleBaseline())))
            .put(bundleEntry(buildDetectedIssueResource(sampleAnomaly())))
        assertParsesAsR4(
            "Full Bundle",
            buildBundleResource(entries).toString(),
            Bundle::class.java
        )
    }

    companion object {
        // FhirContext is expensive to create (classpath scan). Share across tests.
        private lateinit var parser: IParser

        @BeforeClass
        @JvmStatic
        fun setUp() {
            parser = FhirContext.forR4()
                .newJsonParser()
                .setParserErrorHandler(StrictErrorHandler())
        }
    }
}
