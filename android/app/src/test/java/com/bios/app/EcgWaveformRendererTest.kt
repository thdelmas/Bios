package com.bios.app

import com.bios.app.model.EcgStrip
import com.bios.app.model.LeadPlacement
import com.bios.app.ui.ecg.decodeMillivolts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the ECG waveform renderer's byte-to-millivolt
 * decoder. The Compose draw layer needs the Android runtime; the byte
 * math doesn't and is the part most likely to regress when the storage
 * format shifts.
 */
class EcgWaveformRendererTest {

    @Test
    fun decodes_int16_little_endian_with_scale() {
        // Sample [01, 00] = 1, scaled by 0.001 → 0.001 mV
        // Sample [02, 00] = 2, scaled by 0.001 → 0.002 mV
        // Sample [FD, FF] = -3, scaled by 0.001 → -0.003 mV
        val strip = EcgStrip(
            id = "t1",
            timestamp = 0L,
            durationSeconds = 1,
            samplingRateHz = 512,
            leadPlacement = LeadPlacement.LEAD_I,
            samples = byteArrayOf(0x01, 0x00, 0x02, 0x00, 0xFD.toByte(), 0xFF.toByte()),
            voltageScale = 0.001,
            voltageOffset = 0.0,
            sampleEncoding = "int16_le",
            sourceVendor = "Apple Watch",
        )
        val mv = decodeMillivolts(strip)
        assertEquals(3, mv.size)
        assertEquals(0.001, mv[0], 1e-9)
        assertEquals(0.002, mv[1], 1e-9)
        assertEquals(-0.003, mv[2], 1e-9)
    }

    @Test
    fun unsupported_encoding_returns_empty_not_throws() {
        // The renderer must degrade gracefully for encodings we haven't
        // implemented yet — an unsupported format should not crash the
        // detail screen.
        val strip = EcgStrip(
            id = "t2",
            timestamp = 0L,
            durationSeconds = 1,
            samplingRateHz = 512,
            samples = byteArrayOf(1, 2, 3),
            sampleEncoding = "float32_le",  // not implemented today
            sourceVendor = "Future",
        )
        val mv = decodeMillivolts(strip)
        assertTrue(mv.isEmpty())
    }

    @Test
    fun empty_blob_decodes_to_empty_array() {
        val strip = EcgStrip(
            id = "t3",
            timestamp = 0L,
            durationSeconds = 1,
            samplingRateHz = 512,
            samples = ByteArray(0),
            sampleEncoding = "int16_le",
            sourceVendor = "Apple Watch",
        )
        assertTrue(decodeMillivolts(strip).isEmpty())
    }

    @Test
    fun voltage_offset_is_applied_after_scale() {
        // mv = sample * scale + offset → offset adds a DC bias
        val strip = EcgStrip(
            id = "t4",
            timestamp = 0L,
            durationSeconds = 1,
            samplingRateHz = 512,
            samples = byteArrayOf(0x0A, 0x00),  // sample = 10
            voltageScale = 0.001,
            voltageOffset = 0.5,
            sampleEncoding = "int16_le",
            sourceVendor = "Apple Watch",
        )
        val mv = decodeMillivolts(strip)
        assertEquals(1, mv.size)
        assertEquals(0.5 + 0.01, mv[0], 1e-9)
    }
}
