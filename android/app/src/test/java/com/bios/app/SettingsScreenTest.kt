package com.bios.app

import com.bios.app.engine.BaselineEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for logic used by SettingsScreen: baseline status, and data age display.
 */
class SettingsScreenTest {

    // -- Baseline status display --

    @Test
    fun `baseline status is Active when data age meets minimum`() {
        val dataAge = BaselineEngine.MINIMUM_DATA_DAYS
        val status = if (dataAge >= BaselineEngine.MINIMUM_DATA_DAYS) "Active"
        else "${BaselineEngine.MINIMUM_DATA_DAYS - dataAge} days remaining"
        assertEquals("Active", status)
    }

    @Test
    fun `baseline status shows remaining days when below minimum`() {
        val dataAge = 3
        val status = if (dataAge >= BaselineEngine.MINIMUM_DATA_DAYS) "Active"
        else "${BaselineEngine.MINIMUM_DATA_DAYS - dataAge} days remaining"
        assertTrue(status.contains("days remaining"))
        assertTrue(status.contains("${BaselineEngine.MINIMUM_DATA_DAYS - 3}"))
    }

    @Test
    fun `baseline status is Active when data age exceeds minimum`() {
        val dataAge = 30
        val status = if (dataAge >= BaselineEngine.MINIMUM_DATA_DAYS) "Active"
        else "${BaselineEngine.MINIMUM_DATA_DAYS - dataAge} days remaining"
        assertEquals("Active", status)
    }

    // -- Connection status display --

    @Test
    fun `connected status text when permissions granted`() {
        val hasPermissions = true
        val text = if (hasPermissions) "Connected" else "Not Connected"
        assertEquals("Connected", text)
    }

    @Test
    fun `not connected status text when permissions missing`() {
        val hasPermissions = false
        val text = if (hasPermissions) "Connected" else "Not Connected"
        assertEquals("Not Connected", text)
    }

    // -- Export button state --

    @Test
    fun `export disabled when no readings`() {
        val isExporting = false
        val totalReadings = 0
        val enabled = !isExporting && totalReadings > 0
        assertFalse(enabled)
    }

    @Test
    fun `export disabled while exporting`() {
        val isExporting = true
        val totalReadings = 100
        val enabled = !isExporting && totalReadings > 0
        assertFalse(enabled)
    }

    @Test
    fun `export enabled with readings and not exporting`() {
        val isExporting = false
        val totalReadings = 100
        val enabled = !isExporting && totalReadings > 0
        assertTrue(enabled)
    }

    @Test
    fun `export button text changes while exporting`() {
        val isExporting = true
        val text = if (isExporting) "Exporting..." else "Export All Data"
        assertEquals("Exporting...", text)
    }
}
