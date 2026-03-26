package com.bios.app

import com.bios.app.export.DataExporter
import com.bios.app.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for data export model validation.
 * Full integration tests for DataExporter require an Android context + Room DB.
 * These tests validate the data model properties that the exporter relies on.
 */
class DataExporterTest {

    @Test
    fun `all metric types have unique keys`() {
        val keys = MetricType.entries.map { it.key }
        assertEquals("Duplicate metric type keys found", keys.size, keys.toSet().size)
    }

    @Test
    fun `fromKey resolves all metric types`() {
        for (metricType in MetricType.entries) {
            assertEquals(metricType, MetricType.fromKey(metricType.key))
        }
    }

    @Test
    fun `fromKey returns null for unknown key`() {
        assertNull(MetricType.fromKey("nonexistent_metric"))
    }

    @Test
    fun `metric readable names are human friendly`() {
        assertEquals("Heart rate", MetricType.HEART_RATE.readableName)
        assertEquals("Heart rate variability", MetricType.HEART_RATE_VARIABILITY.readableName)
        assertEquals("Blood oxygen", MetricType.BLOOD_OXYGEN.readableName)
        assertEquals("Skin temperature deviation", MetricType.SKIN_TEMPERATURE_DEVIATION.readableName)
    }

    @Test
    fun `all metric types have a unit`() {
        for (metricType in MetricType.entries) {
            assertNotNull("${metricType.key} should have a unit", metricType.unit)
        }
    }

    @Test
    fun `all metric types have a domain`() {
        for (metricType in MetricType.entries) {
            assertNotNull("${metricType.key} should have a domain", metricType.domain)
        }
    }

    @Test
    fun `cardiovascular domain includes expected metrics`() {
        val cardioMetrics = MetricType.entries.filter { it.domain == MetricDomain.CARDIOVASCULAR }
        val keys = cardioMetrics.map { it.key }
        assertTrue(keys.contains("heart_rate"))
        assertTrue(keys.contains("heart_rate_variability"))
        assertTrue(keys.contains("resting_heart_rate"))
        assertTrue(keys.contains("blood_oxygen"))
    }

    // -- Export schema constants --

    @Test
    fun `schema ID is set`() {
        assertEquals("bios-open-health", DataExporter.SCHEMA_ID)
    }

    @Test
    fun `schema version is semver-like`() {
        assertTrue(DataExporter.SCHEMA_VERSION.matches(Regex("\\d+\\.\\d+")))
    }

    @Test
    fun `schema version reflects health events addition`() {
        assertEquals("1.2", DataExporter.SCHEMA_VERSION)
    }

    // -- CSV escaping edge cases (tested via the export patterns) --

    @Test
    fun `csv values with commas get quoted`() {
        val value = "hello,world"
        val escaped = csvEscape(value)
        assertTrue(escaped.startsWith("\""))
        assertTrue(escaped.endsWith("\""))
    }

    @Test
    fun `csv values with quotes get double-quoted`() {
        val value = "say \"hello\""
        val escaped = csvEscape(value)
        assertTrue(escaped.contains("\"\""))
    }

    @Test
    fun `csv plain values are not modified`() {
        val value = "plain_text"
        assertEquals(value, csvEscape(value))
    }

    private fun csvEscape(value: String): String {
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }
}
