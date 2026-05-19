package com.bios.app.engine

import com.bios.contracts.MetricType

/**
 * Status of the owner's coverage for a single [MetricType] — does Bios
 * have data, is it fresh, and what routes can fill the gap.
 *
 * Surfaced by [MetricCoverageEngine] to the Data Coverage screen so the
 * owner can spot missing or stale metrics at a glance and act on them.
 */
data class MetricCoverage(
    val metricType: MetricType,
    val status: MetricCoverageStatus,
    /** Epoch millis of the most recent reading, or null if none exists. */
    val lastTimestamp: Long?,
    /** Routes that could deliver this metric — adapters, manual entry, etc.
     *  Each route carries its own configured/unconfigured state so the UI
     *  can mark them as either CTAs (tap to connect / add) or
     *  acknowledgements (already set up). */
    val routes: List<CoverageRoute>
)

enum class MetricCoverageStatus {
    /** Fresh data within the metric's staleness budget. */
    LIVE,

    /** Has data, but the most recent reading is older than the budget. */
    STALE,

    /** No reading has ever been recorded for this metric. */
    MISSING
}

/**
 * One way the owner can register a metric. Routes are *what could deliver
 * this metric*, regardless of whether the owner has set the route up.
 * `isConfigured` distinguishes "ready to receive" from "needs setup".
 */
data class CoverageRoute(
    val kind: CoverageRouteKind,
    val displayName: String,
    val isConfigured: Boolean,
    /** Optional in-app navigation route to take on tap (Compose nav). Null
     *  for routes that don't lead anywhere actionable (e.g. an external
     *  device that's already paired and we're just informing the owner). */
    val deepLink: String? = null
)

enum class CoverageRouteKind {
    HEALTH_CONNECT,
    API_ADAPTER,    // Oura, Withings, Polar, Dexcom, etc.
    DIRECT_SENSOR,
    PHONE_SENSOR,
    PPG_CAPTURE,
    MANUAL_ENTRY,
    FHIR_IMPORT,
    BLE_PERIPHERAL, // Bluetooth-LE air-quality / future BLE sensors (#43).
}

/**
 * Snapshot of which data-source surfaces the app currently has set up.
 * Built by the ViewModel from `AppViewModel` / `IngestManager` flags and
 * passed into [MetricCoverageEngine] so the engine stays pure (no Android
 * deps, no permissions checks).
 */
data class AdapterReadiness(
    val healthConnectGranted: Boolean,
    val ouraConfigured: Boolean,
    val withingsConfigured: Boolean,
    val whoopConfigured: Boolean,
    val polarConfigured: Boolean,
    val dexcomConfigured: Boolean,
    val directSensorAvailable: Boolean,
    val phoneSensorAvailable: Boolean,
    /** The PPG-camera capture path is always available on a phone with a
     *  rear camera + flash — kept here so the engine doesn't have to
     *  assume the hardware exists. */
    val ppgCaptureAvailable: Boolean,
    /** True when at least one BLE air-quality peripheral has been paired
     *  (#43). Default false until the phase-2 pairing UI lands. */
    val bleAirQualityPaired: Boolean = false,
)
