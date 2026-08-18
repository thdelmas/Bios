package com.bios.app

import com.bios.app.ui.home.VitalsTileStore
import com.bios.app.ui.home.decodeTiles
import com.bios.app.ui.home.encodeTiles
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Serialization contract for the owner-curated vitals grid. The
 * store itself is a thin SharedPreferences wrapper (JVM-only test setup,
 * no Robolectric), so these tests pin the pure encode/decode core: order
 * preservation, unknown-key tolerance across contracts versions, and
 * duplicate collapse.
 */
class VitalsTileStoreTest {

    @Test
    fun `round trip preserves order`() {
        val tiles = listOf(
            MetricType.RESTING_HEART_RATE,
            MetricType.SLEEP_DURATION,
            MetricType.STEPS,
        )
        assertEquals(tiles, decodeTiles(encodeTiles(tiles)))
    }

    @Test
    fun `unknown keys are dropped not fatal`() {
        val raw = "heart_rate,metric_from_the_future,steps"
        assertEquals(
            listOf(MetricType.HEART_RATE, MetricType.STEPS),
            decodeTiles(raw),
        )
    }

    @Test
    fun `duplicates keep first occurrence`() {
        val raw = "steps,heart_rate,steps"
        assertEquals(
            listOf(MetricType.STEPS, MetricType.HEART_RATE),
            decodeTiles(raw),
        )
    }

    @Test
    fun `whitespace around keys is tolerated`() {
        assertEquals(
            listOf(MetricType.HEART_RATE, MetricType.STEPS),
            decodeTiles(" heart_rate , steps "),
        )
    }

    @Test
    fun `default set is the historical eight in grid order`() {
        assertEquals(8, VitalsTileStore.DEFAULT_TILES.size)
        assertEquals(MetricType.SLEEP_DURATION, VitalsTileStore.DEFAULT_TILES.first())
        assertEquals(
            MetricType.SKIN_TEMPERATURE_DEVIATION,
            VitalsTileStore.DEFAULT_TILES.last(),
        )
    }
}
