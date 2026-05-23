package com.bios.app

import com.bios.app.engine.PpgCalibrationLogger
import com.bios.app.engine.PpgCalibrationRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pure-JVM coverage of the file/format halves of [PpgCalibrationLogger]
 * (#266 Cut 1). The Android-side toggle and `filesDir` resolution are
 * thin wrappers around SharedPreferences and intentionally not exercised
 * here — the unit-test source set has no Android Context.
 *
 * What we lock down:
 *  - Header text is stable (downstream parsers depend on column names).
 *  - Row format is stable (column count, null-as-empty, locale-independent
 *    decimal separator).
 *  - First append writes header + row; subsequent appends add only a row.
 */
class PpgCalibrationLoggerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sampleRow(
        timestampMs: Long = 1_700_000_000_000L,
        peakAmplitudeCov: Double? = 0.42,
        rejectionReason: String? = null,
    ) = PpgCalibrationRow(
        timestampMs = timestampMs,
        deviceModel = "Pixel 9a",
        androidApi = 36,
        samplingRateHz = 20.5,
        recentMedianJumpYP50 = 3.1234,
        recentMedianJumpYP90 = 8.7000,
        peakAmplitudeCov = peakAmplitudeCov,
        saturationRatio = 0.05,
        peakCount = 62,
        durationSec = 60.0,
        rejectionReason = rejectionReason,
    )

    @Test
    fun `header lists the documented columns in order`() {
        // If you change this assertion you must also update any downstream
        // analysis scripts that read the CSV. Column names are the contract.
        assertEquals(
            "timestamp_ms,device_model,android_api,sampling_rate_hz," +
                "recent_median_jump_y_p50,recent_median_jump_y_p90," +
                "peak_amplitude_cov,saturation_ratio,peak_count," +
                "duration_sec,rejection_reason",
            PpgCalibrationLogger.HEADER,
        )
    }

    @Test
    fun `formatRow emits ROOT-locale decimals and the expected column count`() {
        val line = PpgCalibrationLogger.formatRow(sampleRow())
        val columns = line.split(",")
        assertEquals(PpgCalibrationLogger.HEADER.split(",").size, columns.size)
        // No comma-as-decimal-separator leaking from a fr-FR test host:
        assertFalse("decimal separator should be a dot, got: $line", line.contains(",,"))
        assertTrue("sampling rate should render as 20.5000", columns[3] == "20.5000")
    }

    @Test
    fun `formatRow renders null peak_amplitude_cov and null rejection_reason as empty`() {
        val line = PpgCalibrationLogger.formatRow(
            sampleRow(peakAmplitudeCov = null, rejectionReason = null),
        )
        // Append a sentinel so a trailing empty field survives split().
        val columns = (line + ",|").split(",").dropLast(1)
        assertEquals(PpgCalibrationLogger.HEADER.split(",").size, columns.size)
        // peak_amplitude_cov column index = 6 (zero-based).
        assertEquals("", columns[6])
        // rejection_reason is the trailing column.
        assertEquals("", columns.last())
    }

    @Test
    fun `formatRow escapes device models that contain commas`() {
        val tricky = sampleRow().copy(deviceModel = "Weird, Brand")
        val line = PpgCalibrationLogger.formatRow(tricky)
        assertTrue(
            "device_model should be CSV-quoted when it contains a comma: $line",
            line.contains("\"Weird, Brand\""),
        )
    }

    @Test
    fun `appendTo writes header on first call and only the row on subsequent calls`() {
        val file = File(tmp.root, "ppg_calibration.csv")

        PpgCalibrationLogger.appendTo(file, sampleRow(timestampMs = 1L))
        val afterFirst = file.readText()
        assertTrue(
            "first write must include the header",
            afterFirst.startsWith(PpgCalibrationLogger.HEADER + "\n"),
        )
        assertEquals(
            "first write should be header + one row + two trailing newlines",
            2,
            afterFirst.count { it == '\n' },
        )

        PpgCalibrationLogger.appendTo(file, sampleRow(timestampMs = 2L))
        val afterSecond = file.readText()
        // Header still appears exactly once.
        assertEquals(
            "header should appear exactly once across appends",
            1,
            afterSecond.split(PpgCalibrationLogger.HEADER).size - 1,
        )
        // Three newlines: header + two rows.
        assertEquals(3, afterSecond.count { it == '\n' })
    }
}
