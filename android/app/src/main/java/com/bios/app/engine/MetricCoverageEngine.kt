package com.bios.app.engine

import com.bios.contracts.MetricType

/**
 * Pure runtime classifier for [MetricCoverage]. Decoupled from Room / the
 * Application context so it can be exercised by unit tests with a fake
 * [lastTimestampFor] function.
 *
 * The engine takes:
 *  - a [readiness] snapshot of which adapter surfaces are currently
 *    configured (Health Connect granted, Oura token present, etc.),
 *  - a [lastTimestampFor] callback that returns the most recent reading
 *    timestamp for a given metric key (null if none), and
 *  - the current wall-clock time (so tests can freeze it).
 *
 * It walks [MetricCoverageRegistry.specs], compares last-reading times
 * against each spec's staleness budget, and assembles a list of
 * actionable [CoverageRoute] entries from the spec's route kinds + the
 * readiness flags.
 */
object MetricCoverageEngine {

    suspend fun compute(
        readiness: AdapterReadiness,
        nowMillis: Long,
        lastTimestampFor: suspend (String) -> Long?
    ): List<MetricCoverage> {
        return MetricCoverageRegistry.specs.map { spec ->
            val lastTs = lastTimestampFor(spec.metricType.key)
            val status = classifyStatus(lastTs, nowMillis, spec.stalenessBudgetMs)
            val routes = spec.routeKinds.map { kind ->
                routeFor(kind, spec.metricType, readiness)
            }
            MetricCoverage(
                metricType = spec.metricType,
                status = status,
                lastTimestamp = lastTs,
                routes = routes
            )
        }
    }

    internal fun classifyStatus(
        lastTs: Long?,
        nowMillis: Long,
        stalenessBudgetMs: Long
    ): MetricCoverageStatus = when {
        lastTs == null -> MetricCoverageStatus.MISSING
        (nowMillis - lastTs) <= stalenessBudgetMs -> MetricCoverageStatus.LIVE
        else -> MetricCoverageStatus.STALE
    }

    internal fun routeFor(
        kind: CoverageRouteKind,
        metric: MetricType,
        readiness: AdapterReadiness
    ): CoverageRoute = when (kind) {
        CoverageRouteKind.HEALTH_CONNECT -> CoverageRoute(
            kind = kind,
            displayName = "Health Connect",
            isConfigured = readiness.healthConnectGranted,
            deepLink = null
        )
        CoverageRouteKind.API_ADAPTER -> apiAdapterRoute(metric, readiness)
        CoverageRouteKind.DIRECT_SENSOR -> CoverageRoute(
            kind = kind,
            displayName = "On-device sensor",
            isConfigured = readiness.directSensorAvailable,
            deepLink = null
        )
        CoverageRouteKind.PHONE_SENSOR -> CoverageRoute(
            kind = kind,
            displayName = "Phone sensor",
            isConfigured = readiness.phoneSensorAvailable,
            deepLink = null
        )
        CoverageRouteKind.PPG_CAPTURE -> CoverageRoute(
            kind = kind,
            displayName = "Fingertip-PPG snapshot",
            isConfigured = readiness.ppgCaptureAvailable,
            deepLink = "ppg_capture"
        )
        CoverageRouteKind.MANUAL_ENTRY -> CoverageRoute(
            kind = kind,
            displayName = "Manual entry",
            isConfigured = true,  // always available
            deepLink = manualEntryDeepLink(metric)
        )
        CoverageRouteKind.FHIR_IMPORT -> CoverageRoute(
            kind = kind,
            displayName = "FHIR file import",
            isConfigured = true,
            deepLink = "biomarker_entry"
        )
    }

    /** Route a MANUAL_ENTRY chip to the right entry screen. Sleep has its
     *  own form; everything else lands on the biomarker entry surface (which
     *  is also the FHIR import host). */
    private fun manualEntryDeepLink(metric: MetricType): String = when (metric) {
        MetricType.SLEEP_DURATION -> "sleep_entry"
        else -> "biomarker_entry"
    }

    /** Pick the API adapter most likely to provide a given metric. The
     *  registry's route list only says "an API adapter can help"; this
     *  function names which one based on what the adapters actually emit. */
    private fun apiAdapterRoute(metric: MetricType, readiness: AdapterReadiness): CoverageRoute {
        // (adapter display name, isConfigured)
        val candidates: List<Pair<String, Boolean>> = when (metric) {
            MetricType.BODY_MASS,
            MetricType.BODY_FAT_PCT,
            MetricType.BLOOD_PRESSURE_SYSTOLIC,
            MetricType.BLOOD_PRESSURE_DIASTOLIC ->
                listOf("Withings" to readiness.withingsConfigured)

            MetricType.BLOOD_GLUCOSE ->
                listOf("Dexcom" to readiness.dexcomConfigured)

            MetricType.RECOVERY_SCORE ->
                listOf("Oura" to readiness.ouraConfigured)

            else -> listOf(
                "Oura" to readiness.ouraConfigured,
                "Polar" to readiness.polarConfigured,
            )
        }
        // Prefer to show a configured adapter; otherwise show all candidate
        // names so the UI can suggest any of them.
        val configured = candidates.firstOrNull { it.second }
        return if (configured != null) {
            CoverageRoute(
                kind = CoverageRouteKind.API_ADAPTER,
                displayName = configured.first,
                isConfigured = true,
                deepLink = null
            )
        } else {
            CoverageRoute(
                kind = CoverageRouteKind.API_ADAPTER,
                displayName = candidates.joinToString(" or ") { it.first },
                isConfigured = false,
                deepLink = "settings"
            )
        }
    }
}
