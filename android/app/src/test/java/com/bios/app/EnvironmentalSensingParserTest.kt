package com.bios.app

import com.bios.app.alerts.BaselineDeviationPatterns
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.ingest.EnvironmentalSensingParser
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Deterministic byte-format coverage for the Bluetooth SIG ESS parser
 * (#43 phase 1). The runtime BLE adapter is deferred to phase 2; these
 * tests pin the parser surface every BLE sensor will land on.
 *
 * Reference values for the byte fixtures cross-check against the
 * Bluetooth SIG **GATT Specification Supplement 8**, June 2024
 * release — characteristic data formats for `0x2BD4` (PM2.5), `0x2B8C`
 * (CO2), `0x2BE7` (VOC).
 */
class EnvironmentalSensingParserTest {

    private val ts = 1_733_400_000_000L
    private val sourceId = "ble-sensor-1"

    // --- PM2.5 (uint16, resolution 0.1 µg/m³) ---

    @Test
    fun `PM25 0x0096 little-endian decodes to 15_0 ug per m3`() {
        // 0x0096 = 150 raw → 15.0 µg/m³ after the 0.1 resolution scale.
        // Spec: PM2.5 is uint16 with implicit 0.1 µg/m³ resolution.
        val reading = EnvironmentalSensingParser.parsePm25(
            byteArrayOf(0x96.toByte(), 0x00), ts, sourceId,
        )
        assertNotNull(reading)
        assertEquals(MetricType.AIR_PM25.key, reading!!.metricType)
        assertEquals(15.0, reading.value, 0.0)
        assertEquals(ts, reading.timestamp)
        assertEquals(sourceId, reading.sourceId)
    }

    @Test
    fun `PM25 sentinel 0xFFFF is treated as not-known, returns null`() {
        // Spec: 0xFFFF on a uint16 characteristic means "value not known."
        // Surfacing 6553.5 µg/m³ as data would silently poison baselines.
        assertNull(
            EnvironmentalSensingParser.parsePm25(
                byteArrayOf(0xFF.toByte(), 0xFF.toByte()), ts, sourceId,
            ),
        )
    }

    @Test
    fun `PM25 truncated payload returns null rather than crashing`() {
        // Defensive against malformed GATT notifications — adapters can
        // pass through corruption from buggy peripherals.
        assertNull(EnvironmentalSensingParser.parsePm25(byteArrayOf(0x96.toByte()), ts, sourceId))
        assertNull(EnvironmentalSensingParser.parsePm25(byteArrayOf(), ts, sourceId))
    }

    @Test
    fun `PM25 trailing bytes are ignored (some ESS chars carry trailing flags)`() {
        // Some peripherals append a "timestamp" or "uncertainty" field per
        // ESS profile. We only consume the first two bytes; extras don't
        // break the parse.
        val reading = EnvironmentalSensingParser.parsePm25(
            byteArrayOf(0x96.toByte(), 0x00, 0xAA.toByte(), 0xBB.toByte()), ts, sourceId,
        )
        assertEquals(15.0, reading!!.value, 0.0)
    }

    // --- CO2 (uint16, ppm) ---

    @Test
    fun `CO2 0x03E8 little-endian decodes to 1000 ppm`() {
        // 0x03E8 = 1000. CO2 is uint16 in ppm with no scale factor.
        val reading = EnvironmentalSensingParser.parseCo2(
            byteArrayOf(0xE8.toByte(), 0x03), ts, sourceId,
        )
        assertEquals(MetricType.AIR_CO2.key, reading!!.metricType)
        assertEquals(1000.0, reading.value, 0.0)
    }

    @Test
    fun `CO2 sentinel 0xFFFF returns null`() {
        assertNull(
            EnvironmentalSensingParser.parseCo2(
                byteArrayOf(0xFF.toByte(), 0xFF.toByte()), ts, sourceId,
            ),
        )
    }

    // --- VOC (uint16, ppb) ---

    @Test
    fun `VOC 0x012C little-endian decodes to 300 ppb`() {
        val reading = EnvironmentalSensingParser.parseVoc(
            byteArrayOf(0x2C.toByte(), 0x01), ts, sourceId,
        )
        assertEquals(MetricType.AIR_VOC.key, reading!!.metricType)
        assertEquals(300.0, reading.value, 0.0)
    }

    @Test
    fun `VOC sentinel 0xFFFF returns null`() {
        assertNull(
            EnvironmentalSensingParser.parseVoc(
                byteArrayOf(0xFF.toByte(), 0xFF.toByte()), ts, sourceId,
            ),
        )
    }

    // --- parse() dispatch ---

    @Test
    fun `parse dispatches by characteristic UUID short`() {
        val pm25 = EnvironmentalSensingParser.parse(
            EnvironmentalSensingParser.CHARACTERISTIC_PM25_SHORT,
            byteArrayOf(0x96.toByte(), 0x00), ts, sourceId,
        )
        assertEquals(MetricType.AIR_PM25.key, pm25!!.metricType)

        val co2 = EnvironmentalSensingParser.parse(
            EnvironmentalSensingParser.CHARACTERISTIC_CO2_SHORT,
            byteArrayOf(0xE8.toByte(), 0x03), ts, sourceId,
        )
        assertEquals(MetricType.AIR_CO2.key, co2!!.metricType)

        val voc = EnvironmentalSensingParser.parse(
            EnvironmentalSensingParser.CHARACTERISTIC_VOC_SHORT,
            byteArrayOf(0x2C.toByte(), 0x01), ts, sourceId,
        )
        assertEquals(MetricType.AIR_VOC.key, voc!!.metricType)
    }

    @Test
    fun `parse returns null for unsupported characteristic UUIDs`() {
        // ESS has many characteristics Bios doesn't consume (humidity,
        // temperature, wind speed, etc.). The runtime adapter should
        // log-and-skip, not crash, when an unsupported notification
        // arrives — so we return null here.
        assertNull(
            EnvironmentalSensingParser.parse(
                0x2A6F, // Humidity, valid ESS UUID but not in Bios' scope
                byteArrayOf(0x96.toByte(), 0x00), ts, sourceId,
            ),
        )
    }

    // --- Cross-file contract bindings ---

    @Test
    fun `all three air-quality MetricTypes live on the ENVIRONMENT domain`() {
        for (metric in listOf(MetricType.AIR_PM25, MetricType.AIR_VOC, MetricType.AIR_CO2)) {
            assertEquals(MetricDomain.ENVIRONMENT, metric.domain)
        }
    }

    @Test
    fun `air-quality MetricTypes carry the expected display units`() {
        assertEquals(MetricUnit.UG_PER_M3, MetricType.AIR_PM25.unit)
        assertEquals(MetricUnit.PPB, MetricType.AIR_VOC.unit)
        assertEquals(MetricUnit.PPM, MetricType.AIR_CO2.unit)
    }

    @Test
    fun `Sleep Disruption pattern consumes AIR_CO2 as an ABOVE corroborator`() {
        // Bedroom CO2 > 1000 ppm degrades sleep efficiency (Strom-Tejsen
        // 2016). Pinning the SignalRule so a future refactor of the
        // pattern can't silently drop the air-quality corroborator.
        val pattern = BaselineDeviationPatterns.sleepDisruption
        val co2Rule = pattern.signalRules.singleOrNull { it.metricType == MetricType.AIR_CO2 }
        assertNotNull("Sleep Disruption must include the AIR_CO2 SignalRule (#43)", co2Rule)
        assertEquals(DeviationDirection.ABOVE, co2Rule!!.direction)
    }
}
