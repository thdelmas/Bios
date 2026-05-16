package com.bios.app

import com.bios.app.engine.AdapterReadiness
import com.bios.app.engine.CoverageRouteKind
import com.bios.app.engine.MetricCoverageEngine
import com.bios.app.engine.MetricCoverageRegistry
import com.bios.app.engine.MetricCoverageStatus
import com.bios.contracts.MetricType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricCoverageEngineTest {

    private val noReadiness = AdapterReadiness(
        healthConnectGranted = false,
        ouraConfigured = false,
        withingsConfigured = false,
        polarConfigured = false,
        dexcomConfigured = false,
        directSensorAvailable = false,
        phoneSensorAvailable = false,
        ppgCaptureAvailable = true,
    )

    private val now = 1_700_000_000_000L
    private val H = 60L * 60_000L
    private val D = 24L * H

    @Test
    fun `null timestamp classifies MISSING regardless of budget`() {
        assertEquals(
            MetricCoverageStatus.MISSING,
            MetricCoverageEngine.classifyStatus(null, now, D)
        )
    }

    @Test
    fun `reading within budget classifies LIVE`() {
        assertEquals(
            MetricCoverageStatus.LIVE,
            MetricCoverageEngine.classifyStatus(now - H, now, D)
        )
    }

    @Test
    fun `reading older than budget classifies STALE`() {
        assertEquals(
            MetricCoverageStatus.STALE,
            MetricCoverageEngine.classifyStatus(now - 2 * D, now, D)
        )
    }

    @Test
    fun `reading exactly at the budget edge is still LIVE`() {
        assertEquals(
            MetricCoverageStatus.LIVE,
            MetricCoverageEngine.classifyStatus(now - D, now, D)
        )
    }

    @Test
    fun `engine produces a row for every spec in the registry`() = runTest {
        val rows = MetricCoverageEngine.compute(
            readiness = noReadiness,
            nowMillis = now,
            lastTimestampFor = { null }
        )
        assertEquals(MetricCoverageRegistry.specs.size, rows.size)
        // Every spec is represented by exactly one row.
        for (spec in MetricCoverageRegistry.specs) {
            assertTrue(
                "expected a row for ${spec.metricType.key}",
                rows.any { it.metricType == spec.metricType }
            )
        }
    }

    @Test
    fun `all rows are MISSING when the DB is empty`() = runTest {
        val rows = MetricCoverageEngine.compute(
            readiness = noReadiness,
            nowMillis = now,
            lastTimestampFor = { null }
        )
        assertTrue(rows.all { it.status == MetricCoverageStatus.MISSING })
        assertTrue(rows.all { it.lastTimestamp == null })
    }

    @Test
    fun `fresh hr reading classifies LIVE and stale one classifies STALE`() = runTest {
        val rows = MetricCoverageEngine.compute(
            readiness = noReadiness,
            nowMillis = now,
            lastTimestampFor = { key ->
                when (key) {
                    MetricType.HEART_RATE.key -> now - 2 * H              // fresh
                    MetricType.SLEEP_DURATION.key -> now - 5 * D          // very stale
                    else -> null
                }
            }
        )
        val hr = rows.single { it.metricType == MetricType.HEART_RATE }
        val sleep = rows.single { it.metricType == MetricType.SLEEP_DURATION }
        assertEquals(MetricCoverageStatus.LIVE, hr.status)
        assertEquals(MetricCoverageStatus.STALE, sleep.status)
    }

    @Test
    fun `oura route flips to configured when its token is present`() = runTest {
        val ouraReady = noReadiness.copy(ouraConfigured = true)
        val rows = MetricCoverageEngine.compute(
            readiness = ouraReady,
            nowMillis = now,
            lastTimestampFor = { null }
        )
        val hr = rows.single { it.metricType == MetricType.HEART_RATE }
        val apiRoute = hr.routes.single { it.kind == CoverageRouteKind.API_ADAPTER }
        assertTrue(apiRoute.isConfigured)
        assertEquals("Oura", apiRoute.displayName)
    }

    @Test
    fun `withings is the API route surfaced for body composition`() = runTest {
        val rows = MetricCoverageEngine.compute(
            readiness = noReadiness,
            nowMillis = now,
            lastTimestampFor = { null }
        )
        val mass = rows.single { it.metricType == MetricType.BODY_MASS }
        val apiRoute = mass.routes.single { it.kind == CoverageRouteKind.API_ADAPTER }
        assertTrue("Withings" in apiRoute.displayName)
        assertEquals(false, apiRoute.isConfigured)
    }

    @Test
    fun `biomarker rows offer manual entry and FHIR import as configured`() = runTest {
        val rows = MetricCoverageEngine.compute(
            readiness = noReadiness,
            nowMillis = now,
            lastTimestampFor = { null }
        )
        val hba1c = rows.single { it.metricType == MetricType.HBA1C }
        val manual = hba1c.routes.single { it.kind == CoverageRouteKind.MANUAL_ENTRY }
        val fhir = hba1c.routes.single { it.kind == CoverageRouteKind.FHIR_IMPORT }
        assertTrue(manual.isConfigured)
        assertTrue(fhir.isConfigured)
        assertNotNull(manual.deepLink)
        assertNotNull(fhir.deepLink)
    }

    @Test
    fun `reproductive metrics are deliberately not in the registry`() {
        // BBT / cycle data live in the isolated ReproductiveDatabase behind
        // their own entry surface, so the general coverage view skips them.
        val present = MetricCoverageRegistry.byMetric.keys
        assertTrue(MetricType.BASAL_BODY_TEMPERATURE !in present)
        assertTrue(MetricType.CYCLE_PHASE !in present)
        assertTrue(MetricType.CYCLE_DAY !in present)
        assertTrue(MetricType.MENSTRUATION_ONSET !in present)
    }

    @Test
    fun `companion-provided metrics are deliberately not in the registry`() {
        // No install-companion flow yet — surfacing those metrics here would
        // just be noise without an actionable CTA.
        val present = MetricCoverageRegistry.byMetric.keys
        assertTrue(MetricType.TYPING_CADENCE !in present)
        assertTrue(MetricType.MOOD_DRIFT_SCORE !in present)
        assertTrue(MetricType.FALL_EVENT !in present)
        assertTrue(MetricType.TOBACCO_USE !in present)
    }

    @Test
    fun `every wearable spec lists health connect and a named api adapter`() {
        // Routes-shape sanity: a continuous-wear metric should always offer
        // both Health Connect and an API adapter so the engine can render
        // either CTA depending on what's already set up.
        val sample = MetricCoverageRegistry.byMetric.getValue(MetricType.HEART_RATE)
        assertTrue(CoverageRouteKind.HEALTH_CONNECT in sample.routeKinds)
        assertTrue(CoverageRouteKind.API_ADAPTER in sample.routeKinds)
    }

    @Test
    fun `manual entry routes have no API-adapter sibling for biomarkers`() {
        val sample = MetricCoverageRegistry.byMetric.getValue(MetricType.HSCRP)
        assertEquals(
            listOf(CoverageRouteKind.MANUAL_ENTRY, CoverageRouteKind.FHIR_IMPORT),
            sample.routeKinds
        )
    }

    @Test
    fun `sleep duration offers manual entry routed to the sleep entry screen`() = runTest {
        val rows = MetricCoverageEngine.compute(
            readiness = noReadiness,
            nowMillis = now,
            lastTimestampFor = { null }
        )
        val sleep = rows.single { it.metricType == MetricType.SLEEP_DURATION }
        val manual = sleep.routes.single { it.kind == CoverageRouteKind.MANUAL_ENTRY }
        assertTrue(manual.isConfigured)
        assertEquals("sleep_entry", manual.deepLink)
    }

    @Test
    fun `biomarker manual entry still routes to the biomarker entry screen`() = runTest {
        val rows = MetricCoverageEngine.compute(
            readiness = noReadiness,
            nowMillis = now,
            lastTimestampFor = { null }
        )
        val hba1c = rows.single { it.metricType == MetricType.HBA1C }
        val manual = hba1c.routes.single { it.kind == CoverageRouteKind.MANUAL_ENTRY }
        assertEquals("biomarker_entry", manual.deepLink)
    }

    @Test
    fun `last timestamp survives onto the produced row`() = runTest {
        val ts = now - 3 * H
        val rows = MetricCoverageEngine.compute(
            readiness = noReadiness,
            nowMillis = now,
            lastTimestampFor = { if (it == MetricType.HEART_RATE.key) ts else null }
        )
        val hr = rows.single { it.metricType == MetricType.HEART_RATE }
        assertEquals(ts, hr.lastTimestamp)
        // Other rows still null.
        assertNull(rows.single { it.metricType == MetricType.SLEEP_DURATION }.lastTimestamp)
    }
}
