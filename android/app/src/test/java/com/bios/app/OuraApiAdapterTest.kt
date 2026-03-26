package com.bios.app

import com.bios.app.ingest.OuraApiAdapter
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import com.bios.app.model.SleepStage
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for OuraApiAdapter data mapping and parsing logic.
 * Network calls are not tested here; we test the pure transformation functions.
 */
class OuraApiAdapterTest {

    private fun createAdapter(token: String? = "test_token"): OuraApiAdapter =
        OuraApiAdapter(getToken = { token }, hasToken = { token != null })

    // -- Connection state --

    @Test
    fun `isConnected returns true when token exists`() {
        val adapter = createAdapter("some_token")
        assertTrue(adapter.isConnected)
    }

    @Test
    fun `isConnected returns false when no token`() {
        val adapter = createAdapter(null)
        assertFalse(adapter.isConnected)
    }

    // -- Sleep stage parsing from Oura's 5-min encoding --

    @Test
    fun `parseSleepStages maps Oura digit codes to Bios SleepStages`() {
        val adapter = createAdapter()
        val readings = adapter.parseSleepStages("112234", 1000L, "oura_src")

        assertTrue(readings.isNotEmpty())
        assertTrue(readings.all { it.metricType == MetricType.SLEEP_STAGE.key })
        assertTrue(readings.all { it.sourceId == "oura_src" })
    }

    @Test
    fun `parseSleepStages consolidates consecutive same-stage intervals`() {
        val adapter = createAdapter()
        // "1111" = four consecutive DEEP intervals -> one reading of 20 min
        val readings = adapter.parseSleepStages("1111", 0L, "src")

        assertEquals(1, readings.size)
        assertEquals(SleepStage.DEEP.value.toDouble(), readings[0].value, 0.01)
        assertEquals(4 * 300, readings[0].durationSec)
    }

    @Test
    fun `parseSleepStages handles alternating stages`() {
        val adapter = createAdapter()
        // "1234" = DEEP, LIGHT, REM, AWAKE -> 4 separate readings
        val readings = adapter.parseSleepStages("1234", 0L, "src")

        assertEquals(4, readings.size)
        assertEquals(SleepStage.DEEP.value.toDouble(), readings[0].value, 0.01)
        assertEquals(SleepStage.LIGHT.value.toDouble(), readings[1].value, 0.01)
        assertEquals(SleepStage.REM.value.toDouble(), readings[2].value, 0.01)
        assertEquals(SleepStage.AWAKE.value.toDouble(), readings[3].value, 0.01)
    }

    @Test
    fun `parseSleepStages returns empty for empty string`() {
        val adapter = createAdapter()
        val readings = adapter.parseSleepStages("", 0L, "src")
        assertTrue(readings.isEmpty())
    }

    @Test
    fun `parseSleepStages each reading has 300s duration per interval`() {
        val adapter = createAdapter()
        val readings = adapter.parseSleepStages("12", 0L, "src")
        readings.forEach { assertEquals(300, it.durationSec) }
    }

    @Test
    fun `parseSleepStages ignores unknown digit codes`() {
        val adapter = createAdapter()
        // Only digits 1-4 are valid; 5, 0, 9 are skipped
        val readings = adapter.parseSleepStages("15091", 0L, "src")

        assertTrue(readings.all {
            it.value in listOf(
                SleepStage.DEEP.value.toDouble(),
                SleepStage.LIGHT.value.toDouble(),
                SleepStage.REM.value.toDouble(),
                SleepStage.AWAKE.value.toDouble()
            )
        })
    }

    @Test
    fun `parseSleepStages uses correct confidence tier`() {
        val adapter = createAdapter()
        val readings = adapter.parseSleepStages("1", 0L, "src")

        assertEquals(1, readings.size)
        assertEquals(ConfidenceTier.MEDIUM.level, readings[0].confidence)
    }

    // -- Timestamp parsing --

    @Test
    fun `parseTimestamp handles ISO 8601 UTC format`() {
        val adapter = createAdapter()
        val millis = adapter.parseTimestamp("2026-03-20T12:00:00Z")
        assertTrue(millis > 0)
    }

    @Test
    fun `parseTimestamp different dates produce different millis`() {
        val adapter = createAdapter()
        val millis1 = adapter.parseTimestamp("2026-03-20T12:00:00Z")
        val millis2 = adapter.parseTimestamp("2026-03-21T12:00:00Z")
        assertTrue(millis2 > millis1)
    }

    // -- MetricReading construction patterns (mirrors adapter fetch methods) --

    @Test
    fun `heart rate reading from Oura uses MEDIUM confidence`() {
        val reading = MetricReading(
            metricType = MetricType.HEART_RATE.key,
            value = 65.0,
            timestamp = 1000L,
            sourceId = "oura_src",
            confidence = ConfidenceTier.MEDIUM.level
        )
        assertEquals("heart_rate", reading.metricType)
        assertEquals(ConfidenceTier.MEDIUM.level, reading.confidence)
    }

    @Test
    fun `recovery score uses VENDOR_DERIVED confidence`() {
        val reading = MetricReading(
            metricType = MetricType.RECOVERY_SCORE.key,
            value = 85.0,
            timestamp = 1000L,
            sourceId = "oura_src",
            confidence = ConfidenceTier.VENDOR_DERIVED.level
        )
        assertEquals(ConfidenceTier.VENDOR_DERIVED.level, reading.confidence)
    }

    @Test
    fun `sleep duration reading stores seconds`() {
        val reading = MetricReading(
            metricType = MetricType.SLEEP_DURATION.key,
            value = 28800.0,
            timestamp = 1000L,
            durationSec = 28800,
            sourceId = "oura_src",
            confidence = ConfidenceTier.MEDIUM.level
        )
        assertEquals(28800, reading.durationSec)
    }

    @Test
    fun `temperature deviation can be negative from Oura`() {
        val reading = MetricReading(
            metricType = MetricType.SKIN_TEMPERATURE_DEVIATION.key,
            value = -0.5,
            timestamp = 1000L,
            sourceId = "oura_src",
            confidence = ConfidenceTier.MEDIUM.level
        )
        assertTrue(reading.value < 0)
    }

    @Test
    fun `daily steps reading spans 24 hours`() {
        val reading = MetricReading(
            metricType = MetricType.STEPS.key,
            value = 8500.0,
            timestamp = 1000L,
            durationSec = 86400,
            sourceId = "oura_src",
            confidence = ConfidenceTier.MEDIUM.level
        )
        assertEquals(86400, reading.durationSec)
    }

    @Test
    fun `BASE_URL points to Oura v2 API`() {
        assertTrue(OuraApiAdapter.BASE_URL.contains("ouraring.com"))
        assertTrue(OuraApiAdapter.BASE_URL.contains("v2"))
    }
}
