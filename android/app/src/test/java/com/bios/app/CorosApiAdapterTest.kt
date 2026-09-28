package com.bios.app

import com.bios.app.ingest.CorosApiAdapter
import com.bios.app.ingest.OwnerAction
import com.bios.contracts.MetricType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure tests for [CorosApiAdapter]'s parsing surface. The dayDetail fixture
 * mirrors the shape the COROS web dashboard receives (`result`, `data.dayList`
 * with `happenDay`/`avgSleepHrv`/`rhr`). Network calls are not exercised.
 */
class CorosApiAdapterTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")

    private val fixture = """
        {"result":"0000","message":"OK","data":{"dayList":[
          {"happenDay":20260913,"avgSleepHrv":49,"sleepHrvBase":46,"rhr":54},
          {"happenDay":20260914,"avgSleepHrv":39,"sleepHrvBase":46,"rhr":55},
          {"happenDay":20260915,"avgSleepHrv":0,"sleepHrvBase":46,"rhr":0}
        ]}}
    """.trimIndent()

    @Test
    fun `md5 matches the vendor client's hex digest`() {
        assertEquals("5f4dcc3b5aa765d61d8327deb882cf99", CorosApiAdapter.md5Hex("password"))
    }

    @Test
    fun `parses hrv and rhr per wake day and skips zero nights`() {
        val readings = CorosApiAdapter.parseDayList(JSONObject(fixture), "src-1", zone)
        assertEquals(4, readings.size)
        val hrv = readings.filter { it.metricType == MetricType.HEART_RATE_VARIABILITY.key }
        val rhr = readings.filter { it.metricType == MetricType.RESTING_HEART_RATE.key }
        assertEquals(listOf(49.0, 39.0), hrv.map { it.value })
        assertEquals(listOf(54.0, 55.0), rhr.map { it.value })
        assertTrue(readings.all { it.sourceId == "src-1" })
    }

    @Test
    fun `reading is stamped at 06_00 local on its wake day`() {
        val readings = CorosApiAdapter.parseDayList(JSONObject(fixture), "src-1", zone)
        val expected = LocalDate.of(2026, 9, 13).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, readings.first().timestamp)
    }

    @Test
    fun `ids are deterministic per metric and night so re-fetches replace not duplicate`() {
        val a = CorosApiAdapter.parseDayList(JSONObject(fixture), "src-1", zone)
        val b = CorosApiAdapter.parseDayList(JSONObject(fixture), "src-1", zone)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals("coros:heart_rate_variability:20260913", a.first().id)
    }

    @Test
    fun `missing dayList yields nothing`() {
        assertTrue(CorosApiAdapter.parseDayList(JSONObject("""{"result":"0000","data":{}}"""), "s", zone).isEmpty())
        assertTrue(CorosApiAdapter.parseDayList(JSONObject("""{"result":"1030"}"""), "s", zone).isEmpty())
    }

    @Test
    fun `malformed happenDay is dropped`() {
        assertNull(CorosApiAdapter.wakeDayTimestamp(0, zone))
        assertNull(CorosApiAdapter.wakeDayTimestamp(20261399, zone))
    }

    @Test
    fun `a dead session token is the owner's to fix, other refusals are not`() {
        assertEquals(OwnerAction.REAUTH, CorosApiAdapter.ownerActionFor("1019", "Access token is invalid"))
        assertEquals(OwnerAction.REAUTH, CorosApiAdapter.ownerActionFor("9999", "Token expired"))
        assertEquals(OwnerAction.NONE, CorosApiAdapter.ownerActionFor("1030", "Rate limited"))
        assertEquals(OwnerAction.NONE, CorosApiAdapter.ownerActionFor("", null))
    }

    @Test
    fun `eu is the default region and every region has a base url`() {
        assertEquals("eu", CorosApiAdapter.DEFAULT_REGION)
        assertTrue(CorosApiAdapter.REGION_LABELS.keys.all { it in CorosApiAdapter.BASE_URLS })
    }
}
