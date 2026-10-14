package com.bios.app

import com.bios.app.data.CountWinners
import com.bios.app.data.SourceBucketRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One witness per calendar day (docs/specs/source-preference.md, criteria 2, 4 and 5). */
class CountWinnersTest {

    private val order = listOf("self_reported", "health_connect:com.coros.coros", "health_connect:com.fitbit.FitbitMobile", "phone_sensor")
    private val coros = "health_connect:com.coros.coros"
    private val fitbit = "health_connect:com.fitbit.FitbitMobile"

    @Test
    fun `the first source in the order with readings owns the day`() {
        val rows = listOf(
            SourceBucketRow("2026-10-10", coros, 3798.0),
            SourceBucketRow("2026-10-10", fitbit, 8400.0),
            SourceBucketRow("2026-10-10", "phone_sensor", 9000.0),
        )
        val resolved = CountWinners.resolve(rows, order)
        assertEquals(3798.0, resolved["2026-10-10"]!!.total, 0.0)
        assertEquals(coros, resolved["2026-10-10"]!!.winnerKey)
    }

    @Test
    fun `a day the watch has nothing falls back to the next source`() {
        val rows = listOf(
            SourceBucketRow("2026-10-10", coros, 3798.0),
            SourceBucketRow("2026-10-11", fitbit, 5000.0),
            SourceBucketRow("2026-10-11", "phone_sensor", 5200.0),
        )
        val resolved = CountWinners.resolve(rows, order)
        assertEquals(coros, resolved["2026-10-10"]!!.winnerKey)
        assertEquals(fitbit, resolved["2026-10-11"]!!.winnerKey)
        assertEquals(5000.0, resolved["2026-10-11"]!!.total, 0.0)
    }

    @Test
    fun `hour buckets keep only the day winner so the day view sums to the day`() {
        val rows = listOf(
            SourceBucketRow("2026-10-14T00", coros, 1000.0),
            SourceBucketRow("2026-10-14T00", fitbit, 2259.0),
            SourceBucketRow("2026-10-14T01", fitbit, 4771.0),
            SourceBucketRow("2026-10-14T10", coros, 700.0),
        )
        val resolved = CountWinners.resolve(rows, order)
        assertEquals(setOf("2026-10-14T00", "2026-10-14T10"), resolved.keys)
        assertEquals(1700.0, resolved.values.sumOf { it.total }, 0.0)
        assertNull(resolved["2026-10-14T01"])
    }

    @Test
    fun `a typed count outranks every sensor that day`() {
        val rows = listOf(
            SourceBucketRow("2026-10-12", "self_reported", 6000.0),
            SourceBucketRow("2026-10-12", coros, 2863.0),
        )
        assertEquals("self_reported", CountWinners.resolve(rows, order)["2026-10-12"]!!.winnerKey)
    }

    @Test
    fun `months are sums of day winners with no winner of their own`() {
        val days = CountWinners.resolve(
            listOf(
                SourceBucketRow("2026-10-10", coros, 3798.0),
                SourceBucketRow("2026-10-10", fitbit, 8400.0),
                SourceBucketRow("2026-10-11", fitbit, 5000.0),
                SourceBucketRow("2026-09-30", coros, 100.0),
            ),
            order,
        )
        val months = CountWinners.rollUpToMonths(days)
        assertEquals(8798.0, months["2026-10"]!!.total, 0.0)
        assertEquals(100.0, months["2026-09"]!!.total, 0.0)
        assertNull(months["2026-10"]!!.winnerKey)
    }

    @Test
    fun `a source missing from the order still counts when it is alone`() {
        val rows = listOf(SourceBucketRow("2026-10-09", "mystery", 42.0))
        assertEquals(42.0, CountWinners.resolve(rows, order)["2026-10-09"]!!.total, 0.0)
    }

    @Test
    fun `empty input resolves to nothing`() {
        assertEquals(emptyMap<String, Any>(), CountWinners.resolve(emptyList(), order))
    }
}
