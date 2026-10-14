package com.bios.app

import com.bios.app.data.CountSourceOrder
import com.bios.app.model.DataSource
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-JVM checks for the owner's count-source order (docs/specs/source-preference.md, criteria 1 to 3). */
class CountSourceOrderTest {

    private fun source(type: SourceType, model: String? = null, name: String? = null) = DataSource(
        sourceType = type.key, deviceName = name, deviceModel = model, sensorType = SensorType.OPTICAL_HR.name,
    )

    @Test
    fun `health connect sources key on the writing app`() {
        assertEquals("health_connect:com.coros.coros", CountSourceOrder.keyOf(source(SourceType.HEALTH_CONNECT, "com.coros.coros")))
        assertEquals("health_connect", CountSourceOrder.keyOf(source(SourceType.HEALTH_CONNECT)))
        assertEquals("phone_sensor", CountSourceOrder.keyOf(source(SourceType.PHONE_SENSOR, "Pixel")))
    }

    @Test
    fun `default order is owner, worn devices, gadgetbridge, phone apps, phone sensors`() {
        val keys = listOf(
            "phone_sensor", "health_connect:com.fitbit.FitbitMobile", "direct_sensor",
            "health_connect:com.coros.coros", "self_reported", "gadgetbridge", "coros_api", "health_connect",
        )
        val order = CountSourceOrder.effectiveOrder(stored = emptyList(), keys = keys)
        assertEquals(
            listOf(
                "self_reported", "coros_api", "health_connect:com.coros.coros", "gadgetbridge",
                "health_connect", "health_connect:com.fitbit.FitbitMobile", "direct_sensor", "phone_sensor",
            ),
            order,
        )
    }

    @Test
    fun `the real COROS app package ranks as a worn device above Fitbit`() {
        val coros = "health_connect:com.yf.smart.coros.dist"
        val fitbit = "health_connect:com.fitbit.FitbitMobile"
        val order = CountSourceOrder.effectiveOrder(stored = emptyList(), keys = listOf(fitbit, coros))
        assertEquals(listOf(coros, fitbit), order)
    }

    @Test
    fun `stored order comes first and unlisted keys follow by default rank`() {
        val order = CountSourceOrder.effectiveOrder(
            stored = listOf("phone_sensor", "health_connect:com.coros.coros"),
            keys = listOf("self_reported", "health_connect:com.coros.coros", "phone_sensor"),
        )
        assertEquals(listOf("phone_sensor", "health_connect:com.coros.coros", "self_reported"), order)
    }

    @Test
    fun `stored keys without a registered source keep their place`() {
        val order = CountSourceOrder.effectiveOrder(stored = listOf("oura_api"), keys = listOf("phone_sensor"))
        assertEquals(listOf("oura_api", "phone_sensor"), order)
    }

    @Test
    fun `unknown keys rank after every listed one`() {
        val order = listOf("self_reported", "phone_sensor")
        assertEquals(0, CountSourceOrder.rank(order, "self_reported"))
        assertEquals(1, CountSourceOrder.rank(order, "phone_sensor"))
        assert(CountSourceOrder.rank(order, "mystery") > 1)
    }

    @Test
    fun `moved swaps neighbours and ignores the edges`() {
        val order = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), CountSourceOrder.moved(order, 1, -1))
        assertEquals(listOf("a", "c", "b"), CountSourceOrder.moved(order, 1, 1))
        assertEquals(order, CountSourceOrder.moved(order, 0, -1))
        assertEquals(order, CountSourceOrder.moved(order, 2, 1))
    }

    @Test
    fun `label prefers the registered device name`() {
        val sources = listOf(source(SourceType.HEALTH_CONNECT, "com.coros.coros", "COROS via Health Connect"))
        assertEquals("COROS via Health Connect", CountSourceOrder.labelOf("health_connect:com.coros.coros", sources))
        assertEquals("Self-reported", CountSourceOrder.labelOf("self_reported", sources))
        assertEquals("Health Connect, rows older than the split", CountSourceOrder.labelOf("health_connect", sources))
        assertEquals("com.fitbit.FitbitMobile", CountSourceOrder.labelOf("health_connect:com.fitbit.FitbitMobile", sources))
    }
}
