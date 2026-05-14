package com.bios.app

import com.bios.app.ui.components.formatValue
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Test

class MetricCardFormatTest {

    @Test
    fun `sleep duration formats seconds as hours and minutes`() {
        // 7h 23m = 7 * 3600 + 23 * 60 = 26580s
        assertEquals("7h 23m", formatValue(26580.0, MetricType.SLEEP_DURATION))
    }

    @Test
    fun `sleep duration handles whole hours`() {
        assertEquals("8h 0m", formatValue(8.0 * 3600, MetricType.SLEEP_DURATION))
    }

    @Test
    fun `sleep duration handles sub-minute remainder by flooring`() {
        // 6h 30m 45s -> floor to 6h 30m
        assertEquals("6h 30m", formatValue(6 * 3600 + 30 * 60 + 45.0, MetricType.SLEEP_DURATION))
    }

    @Test
    fun `sleep duration handles zero`() {
        assertEquals("0h 0m", formatValue(0.0, MetricType.SLEEP_DURATION))
    }

    @Test
    fun `heart rate formats as integer bpm`() {
        assertEquals("72", formatValue(72.4, MetricType.HEART_RATE))
    }
}
