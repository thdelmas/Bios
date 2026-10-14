package com.bios.app

import com.bios.app.ingest.CorosApiAdapter
import com.bios.app.ingest.CorosMobileApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/**
 * Pins the mobile sign-in encryption to vectors computed with the community
 * client's reference implementation (XOR, PKCS7, AES-128-CBC, fixed IV),
 * and the daily-total parser (docs/specs/source-preference.md, 2b).
 */
class CorosMobileApiTest {

    @Test
    fun `encrypt matches the reference implementation`() {
        assertEquals(
            "dlx2OPIIGPS1/ti6DEC8GDEeml+em7gEWus8w3rcWp8=",
            CorosMobileApi.encrypt("owner@example.com", "1234567890123456"),
        )
        assertEquals(
            "RWZ1CA0/7AETyDR8h0S63PmgmtsDVv0kFPAVh3zMa2mIlaKyZpqP3MWQbN1AxQAx",
            CorosMobileApi.encrypt(CorosApiAdapter.md5Hex("hunter2"), "1234567890123456"),
        )
    }

    @Test
    fun `app key is sixteen digits with no leading zero`() {
        repeat(20) {
            val k = CorosMobileApi.randomAppKey()
            assertEquals(16, k.length)
            assert(k.all { it.isDigit() } && k[0] != '0')
        }
    }

    @Test
    fun `daily totals become one whole-day steps reading per day`() {
        val body = JSONObject(
            """{"result":"0000","data":{"statisticData":{"dayDataList":[
              {"happenDay":20261013,"step":5464,"calorie":246},
              {"happenDay":20261014,"stepData":{"step":3286}},
              {"happenDay":20261012,"step":0}
            ]}}}"""
        )
        val zone = ZoneId.of("Europe/Madrid")
        val readings = CorosApiAdapter.parseDailyTotals(body, "src", zone)
        assertEquals(listOf(5464.0, 3286.0), readings.map { it.value })
        assertEquals(86_400, readings[0].durationSec)
        assertEquals("coros:steps:20261013", readings[0].id)
        assertEquals(
            java.time.LocalDate.of(2026, 10, 13).atStartOfDay(zone).toInstant().toEpochMilli(),
            readings[0].timestamp,
        )
    }

    @Test
    fun `missing list or steps yields nothing`() {
        assertEquals(0, CorosApiAdapter.parseDailyTotals(JSONObject("""{"result":"0000","data":{}}"""), "s", ZoneId.systemDefault()).size)
        assertNull(CorosApiAdapter.dailySteps(JSONObject("""{"happenDay":20261013,"rhr":54}""")))
    }
}
