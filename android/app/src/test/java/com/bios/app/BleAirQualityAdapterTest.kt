package com.bios.app

import com.bios.app.ingest.BleAirQualityAdapter
import com.bios.app.ingest.BleAirQualityAdapter.Companion.toShort16
import com.bios.app.ingest.EnvironmentalSensingParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/**
 * Pure-Kotlin tests for the BLE air-quality adapter's static surface. The
 * Bluetooth runtime itself isn't exercise-able in a JVM unit test (no
 * Robolectric in the project), but the UUID plumbing and constants that
 * tie the parser to the GATT characteristics are.
 */
class BleAirQualityAdapterTest {

    @Test
    fun `ESS service UUID matches the bluetooth SIG short form`() {
        assertEquals(
            UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb"),
            BleAirQualityAdapter.ESS_SERVICE_UUID
        )
    }

    @Test
    fun `PM25 CO2 VOC UUIDs match the parser's short-UUID constants`() {
        assertEquals(
            EnvironmentalSensingParser.CHARACTERISTIC_PM25_SHORT,
            BleAirQualityAdapter.PM25_UUID.toShort16()
        )
        assertEquals(
            EnvironmentalSensingParser.CHARACTERISTIC_CO2_SHORT,
            BleAirQualityAdapter.CO2_UUID.toShort16()
        )
        assertEquals(
            EnvironmentalSensingParser.CHARACTERISTIC_VOC_SHORT,
            BleAirQualityAdapter.VOC_UUID.toShort16()
        )
    }

    @Test
    fun `CCCD UUID is the descriptor used to enable notifications`() {
        // Standard Bluetooth-SIG Client Characteristic Configuration Descriptor.
        assertEquals(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BleAirQualityAdapter.CCCD_UUID
        )
    }

    @Test
    fun `toShort16 decodes the 16-bit form for SIG UUIDs`() {
        val uuid = UUID.fromString("00002bd4-0000-1000-8000-00805f9b34fb")
        assertEquals(0x2BD4, uuid.toShort16())
    }

    @Test
    fun `toShort16 returns null for UUIDs outside the SIG namespace`() {
        // Random vendor UUID — Atmotube / Airthings proprietary form.
        val custom = UUID.fromString("b42e2a68-ade7-11e4-89d3-123b93f75cba")
        assertNull(custom.toShort16())
    }

    @Test
    fun `toShort16 handles the 0x0000 short form`() {
        val zero = UUID.fromString("00000000-0000-1000-8000-00805f9b34fb")
        // Even 0 is a valid short — the function is a decoder, not a filter.
        val decoded = zero.toShort16()
        assertNotNull(decoded)
        assertEquals(0, decoded)
    }
}
