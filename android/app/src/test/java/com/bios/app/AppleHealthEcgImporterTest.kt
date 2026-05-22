package com.bios.app

import com.bios.app.ingest.AppleHealthEcgImporter
import com.bios.app.model.EcgClassification
import com.bios.app.model.LeadPlacement
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Apple Health export XML → [com.bios.app.model.EcgStrip]
 * importer. Covers:
 *  - classification mapping across Apple's iOS-version sprawl (the
 *    HealthKit `HKElectrocardiogramClassification` enum gained
 *    variants across iOS 14 / 16 / 17)
 *  - sampling-rate parsing for the "512.07 Hz" / "300" formats
 *  - timestamp parsing of Apple's "yyyy-MM-dd HH:mm:ss ±HHMM" shape
 *  - voltage encoding to int16 little-endian bytes
 *  - end-to-end XML parse of a synthetic ElectrocardiogramRecord
 */
class AppleHealthEcgImporterTest {

    // -- classification mapping --

    @Test
    fun maps_sinus_rhythm() {
        assertEquals(EcgClassification.SINUS_RHYTHM,
            AppleHealthEcgImporter.mapClassification("SinusRhythm"))
        assertEquals(EcgClassification.SINUS_RHYTHM,
            AppleHealthEcgImporter.mapClassification("sinus rhythm"))
    }

    @Test
    fun maps_atrial_fibrillation_across_apple_variants() {
        // Apple expanded the AFib classification in iOS 16+ to include
        // high-HR and low-HR sub-types. Bios collapses to one bucket.
        assertEquals(EcgClassification.ATRIAL_FIBRILLATION,
            AppleHealthEcgImporter.mapClassification("AtrialFibrillation"))
        assertEquals(EcgClassification.ATRIAL_FIBRILLATION,
            AppleHealthEcgImporter.mapClassification("AtrialFibrillationHighHeartRate"))
        assertEquals(EcgClassification.ATRIAL_FIBRILLATION,
            AppleHealthEcgImporter.mapClassification("Possible AFib"))
    }

    @Test
    fun maps_inconclusive_variants() {
        assertEquals(EcgClassification.INCONCLUSIVE,
            AppleHealthEcgImporter.mapClassification("InconclusiveLowHeartRate"))
        assertEquals(EcgClassification.INCONCLUSIVE,
            AppleHealthEcgImporter.mapClassification("InconclusivePoorReading"))
        assertEquals(EcgClassification.INCONCLUSIVE,
            AppleHealthEcgImporter.mapClassification("Unclassified"))
    }

    @Test
    fun maps_unknown_label_to_other_not_null() {
        // We never silently drop the classification — unknown maps to
        // OTHER so the strip still surfaces with a label, which the
        // owner can flag for a clinician.
        assertEquals(EcgClassification.OTHER,
            AppleHealthEcgImporter.mapClassification("SomethingUnexpected"))
    }

    @Test
    fun maps_null_or_blank_classification_to_null() {
        assertNull(AppleHealthEcgImporter.mapClassification(null))
        assertNull(AppleHealthEcgImporter.mapClassification(""))
        assertNull(AppleHealthEcgImporter.mapClassification("   "))
    }

    // -- sampling-rate parsing --

    @Test
    fun parses_sampling_rate_with_hz_suffix() {
        assertEquals(512, AppleHealthEcgImporter.parseSamplingRate("512.07 Hz"))
        assertEquals(300, AppleHealthEcgImporter.parseSamplingRate("300 Hz"))
    }

    @Test
    fun parses_bare_numeric_sampling_rate() {
        assertEquals(250, AppleHealthEcgImporter.parseSamplingRate("250"))
    }

    @Test
    fun falls_back_to_apple_watch_default_when_unparseable() {
        // 512 Hz is the Apple Watch default; safer to default to that
        // than drop the strip when an export has a malformed field.
        assertEquals(512, AppleHealthEcgImporter.parseSamplingRate(null))
        assertEquals(512, AppleHealthEcgImporter.parseSamplingRate(""))
        assertEquals(512, AppleHealthEcgImporter.parseSamplingRate("garbled"))
    }

    // -- timestamp parsing --

    @Test
    fun parses_apple_timestamp_with_offset() {
        // 2023-09-12 14:30:00 -0700 → 2023-09-12T21:30:00Z
        val expected = 1_694_554_200_000L  // 2023-09-12T21:30:00Z in millis
        val parsed = AppleHealthEcgImporter.parseAppleTimestamp("2023-09-12 14:30:00 -0700")
        assertEquals(expected, parsed)
    }

    @Test
    fun returns_null_for_unparseable_timestamp() {
        assertNull(AppleHealthEcgImporter.parseAppleTimestamp(null))
        assertNull(AppleHealthEcgImporter.parseAppleTimestamp("not-a-date"))
    }

    // -- voltage encoding --

    @Test
    fun encodes_voltage_samples_as_int16_le() {
        val bytes = AppleHealthEcgImporter.encodeVoltageSamples("0.001, 0.002, -0.003")
        // 0.001 mV * 1000 = 1 → 0x0001 → bytes [01, 00]
        // 0.002 mV * 1000 = 2 → 0x0002 → bytes [02, 00]
        // -0.003 mV * 1000 = -3 → 0xFFFD → bytes [FD, FF]
        assertEquals(6, bytes.size)
        assertEquals(1, bytes[0].toInt())
        assertEquals(0, bytes[1].toInt())
        assertEquals(2, bytes[2].toInt())
        assertEquals(0, bytes[3].toInt())
        assertEquals(-3, bytes[4].toInt())  // 0xFD as signed
        assertEquals(-1, bytes[5].toInt())  // 0xFF as signed
    }

    @Test
    fun encodes_handles_whitespace_separators() {
        val bytes = AppleHealthEcgImporter.encodeVoltageSamples("0.001\n0.002\t-0.003")
        assertEquals(6, bytes.size)
    }

    @Test
    fun encodes_skips_unparseable_tokens() {
        val bytes = AppleHealthEcgImporter.encodeVoltageSamples("0.001, notanumber, 0.002")
        // Two valid samples = 4 bytes
        assertEquals(4, bytes.size)
    }

    @Test
    fun encodes_clamps_oversized_voltages() {
        // 100 mV * 1000 = 100 000 > Short.MAX_VALUE (32 767) — must clamp,
        // not overflow.
        val bytes = AppleHealthEcgImporter.encodeVoltageSamples("100.0")
        assertEquals(2, bytes.size)
        // Read back as little-endian short: low byte first
        val asShort = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        // Convert to signed Short
        val signed = if (asShort > Short.MAX_VALUE) asShort - 65536 else asShort
        assertEquals(Short.MAX_VALUE.toInt(), signed)
    }

    @Test
    fun encodes_empty_blob_for_blank_input() {
        assertEquals(0, AppleHealthEcgImporter.encodeVoltageSamples("").size)
        assertEquals(0, AppleHealthEcgImporter.encodeVoltageSamples("   ").size)
    }

    // -- end-to-end XML --

    /**
     * Synthetic Apple-Health-export-style ECG record. Real exports
     * carry far more attributes and a much larger VoltageMeasurement
     * payload; this fixture is the minimum the importer must accept.
     */
    private val syntheticXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <HealthData locale="en_US">
            <ElectrocardiogramRecord
                type="HKDataTypeECG"
                startDate="2023-09-12 14:30:00 -0700"
                endDate="2023-09-12 14:30:30 -0700"
                samplingFrequency="512.07 Hz"
                classification="SinusRhythm"
                sourceName="Apple Watch">
                <VoltageMeasurement>0.001, 0.002, 0.003, -0.001</VoltageMeasurement>
            </ElectrocardiogramRecord>
        </HealthData>
    """.trimIndent()

    @Test
    fun parses_synthetic_apple_health_ecg_record() {
        val strips = AppleHealthEcgImporter.parse(
            ByteArrayInputStream(syntheticXml.toByteArray(Charsets.UTF_8))
        )
        assertEquals(1, strips.size)
        val strip = strips.first()
        assertEquals(EcgClassification.SINUS_RHYTHM, strip.classification)
        assertEquals(512, strip.samplingRateHz)
        assertEquals(30, strip.durationSeconds)
        assertEquals(LeadPlacement.LEAD_I, strip.leadPlacement)
        assertEquals("Apple Watch", strip.sourceVendor)
        assertEquals("int16_le", strip.sampleEncoding)
        assertEquals(0.001, strip.voltageScale, 0.0)
        // 4 samples * 2 bytes each = 8 bytes
        assertEquals(8, strip.samples.size)
        // Timestamp is parsed against the offset, not just naively
        assertEquals(1_694_554_200_000L, strip.timestamp)
    }

    @Test
    fun parses_empty_document_to_empty_list() {
        val xml = """<?xml version="1.0"?><HealthData></HealthData>"""
        val strips = AppleHealthEcgImporter.parse(
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        )
        assertTrue(strips.isEmpty())
    }

    @Test
    fun skips_non_ecg_record_entries() {
        // Apple Health exports include thousands of non-ECG `<Record>`
        // entries (steps, heart rate, etc.). The importer must filter
        // those out cleanly.
        val xml = """
            <?xml version="1.0"?>
            <HealthData>
                <Record type="HKQuantityTypeIdentifierHeartRate" value="72"/>
                <Record type="HKQuantityTypeIdentifierStepCount" value="2500"/>
            </HealthData>
        """.trimIndent()
        val strips = AppleHealthEcgImporter.parse(
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        )
        assertTrue(strips.isEmpty())
    }

    @Test
    fun records_with_missing_voltage_are_skipped() {
        // A record with start/end but no voltage payload can't render —
        // silently dropped at parse rather than persisted as empty.
        val xml = """
            <?xml version="1.0"?>
            <HealthData>
                <ElectrocardiogramRecord
                    startDate="2023-09-12 14:30:00 -0700"
                    endDate="2023-09-12 14:30:30 -0700"
                    samplingFrequency="512.07 Hz"
                    classification="SinusRhythm"
                    sourceName="Apple Watch"/>
            </HealthData>
        """.trimIndent()
        val strips = AppleHealthEcgImporter.parse(
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        )
        assertTrue(strips.isEmpty())
    }

    @Test
    fun timestamp_resolves_relative_to_offset_not_naive() {
        // Same wall-clock time, different offsets, must yield different
        // epoch millis — load-bearing for cross-timezone exports.
        val parsedPst = AppleHealthEcgImporter.parseAppleTimestamp(
            "2023-09-12 14:30:00 -0700"
        )
        val parsedUtc = AppleHealthEcgImporter.parseAppleTimestamp(
            "2023-09-12 14:30:00 +0000"
        )
        assertNotNull(parsedPst)
        assertNotNull(parsedUtc)
        // PST is 7 hours behind UTC → PST 14:30 == UTC 21:30 → epoch
        // millis is greater for PST than for the +0000 reading at the
        // same wall time.
        assertEquals(7 * 60 * 60 * 1000L, parsedPst!! - parsedUtc!!)
    }
}
