package com.bios.app

import com.bios.app.engine.SignalQualityFilter
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import org.junit.Assert.*
import org.junit.Test

class SignalQualityFilterTest {

    private fun reading(
        type: MetricType,
        value: Double,
        timestamp: Long = System.currentTimeMillis()
    ) = MetricReading(
        metricType = type.key,
        value = value,
        timestamp = timestamp,
        sourceId = "test",
        confidence = 2
    )

    // -- Absolute range checks --

    @Test
    fun `valid heart rate passes`() {
        val r = reading(MetricType.HEART_RATE, 72.0)
        assertTrue(SignalQualityFilter.checkReading(r))
    }

    @Test
    fun `heart rate below 25 is rejected`() {
        val r = reading(MetricType.HEART_RATE, 10.0)
        assertFalse(SignalQualityFilter.checkReading(r))
    }

    @Test
    fun `heart rate above 250 is rejected`() {
        val r = reading(MetricType.HEART_RATE, 280.0)
        assertFalse(SignalQualityFilter.checkReading(r))
    }

    @Test
    fun `valid HRV passes`() {
        val r = reading(MetricType.HEART_RATE_VARIABILITY, 45.0)
        assertTrue(SignalQualityFilter.checkReading(r))
    }

    @Test
    fun `HRV of 1ms is rejected`() {
        val r = reading(MetricType.HEART_RATE_VARIABILITY, 1.0)
        assertFalse(SignalQualityFilter.checkReading(r))
    }

    @Test
    fun `SpO2 above 100 is rejected`() {
        val r = reading(MetricType.BLOOD_OXYGEN, 105.0)
        assertFalse(SignalQualityFilter.checkReading(r))
    }

    @Test
    fun `SpO2 of 97 passes`() {
        val r = reading(MetricType.BLOOD_OXYGEN, 97.0)
        assertTrue(SignalQualityFilter.checkReading(r))
    }

    @Test
    fun `negative steps rejected`() {
        val r = reading(MetricType.STEPS, -100.0)
        assertFalse(SignalQualityFilter.checkReading(r))
    }

    // -- Rate-of-change checks --

    @Test
    fun `gradual HR increase passes`() {
        val now = System.currentTimeMillis()
        val prev = reading(MetricType.HEART_RATE, 70.0, now - 60_000)
        val curr = reading(MetricType.HEART_RATE, 85.0, now)
        assertTrue(SignalQualityFilter.checkReading(curr, prev))
    }

    @Test
    fun `impossible HR spike is rejected`() {
        val now = System.currentTimeMillis()
        val prev = reading(MetricType.HEART_RATE, 65.0, now - 60_000)
        val curr = reading(MetricType.HEART_RATE, 180.0, now)
        assertFalse(SignalQualityFilter.checkReading(curr, prev))
    }

    @Test
    fun `large HR change over long period passes`() {
        val now = System.currentTimeMillis()
        // 2 hours apart — rate-of-change check only applies within 60 min
        val prev = reading(MetricType.HEART_RATE, 65.0, now - 7_200_000)
        val curr = reading(MetricType.HEART_RATE, 180.0, now)
        assertTrue(SignalQualityFilter.checkReading(curr, prev))
    }

    // -- Batch filtering --

    @Test
    fun `filter removes bad readings from batch`() {
        val readings = listOf(
            reading(MetricType.HEART_RATE, 72.0),
            reading(MetricType.HEART_RATE, 999.0),  // out of range
            reading(MetricType.BLOOD_OXYGEN, 98.0),
            reading(MetricType.BLOOD_OXYGEN, 20.0),  // out of range
        )
        val filtered = SignalQualityFilter.filter(readings)
        assertEquals(2, filtered.size)
        assertEquals(72.0, filtered[0].value, 0.01)
        assertEquals(98.0, filtered[1].value, 0.01)
    }

    // -- Body composition (Withings adapter) --

    @Test
    fun `body mass within 20 to 300 kg passes`() {
        assertTrue(SignalQualityFilter.checkReading(reading(MetricType.BODY_MASS, 70.0)))
        assertTrue(SignalQualityFilter.checkReading(reading(MetricType.BODY_MASS, 20.0)))
        assertTrue(SignalQualityFilter.checkReading(reading(MetricType.BODY_MASS, 300.0)))
    }

    @Test
    fun `body mass outside 20 to 300 kg is rejected`() {
        assertFalse(SignalQualityFilter.checkReading(reading(MetricType.BODY_MASS, 5.0)))
        assertFalse(SignalQualityFilter.checkReading(reading(MetricType.BODY_MASS, 350.0)))
    }

    @Test
    fun `body fat percent within 3 to 60 passes`() {
        assertTrue(SignalQualityFilter.checkReading(reading(MetricType.BODY_FAT_PCT, 18.0)))
        assertTrue(SignalQualityFilter.checkReading(reading(MetricType.BODY_FAT_PCT, 3.0)))
        assertTrue(SignalQualityFilter.checkReading(reading(MetricType.BODY_FAT_PCT, 60.0)))
    }

    @Test
    fun `body fat percent outside 3 to 60 is rejected`() {
        // Below 3% — beyond extreme male athlete; almost certainly artifact.
        assertFalse(SignalQualityFilter.checkReading(reading(MetricType.BODY_FAT_PCT, 1.0)))
        // Above 60% — beyond documented severe obesity range.
        assertFalse(SignalQualityFilter.checkReading(reading(MetricType.BODY_FAT_PCT, 70.0)))
    }

    @Test
    fun `unknown metric type passes through`() {
        val r = MetricReading(
            metricType = "unknown_metric",
            value = 42.0,
            timestamp = System.currentTimeMillis(),
            sourceId = "test",
            confidence = 1
        )
        assertTrue(SignalQualityFilter.checkReading(r))
    }
}
