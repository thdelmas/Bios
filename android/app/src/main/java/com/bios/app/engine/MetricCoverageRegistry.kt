package com.bios.app.engine

import com.bios.contracts.MetricType

/**
 * Static description of how Bios expects each [MetricType] to be filled.
 *
 * Each spec carries:
 *  - a *staleness budget* — how recent a reading must be for the coverage
 *    engine to call it LIVE — anchored in clinical or wearable conventions
 *    rather than UX preference; and
 *  - the set of *route kinds* that could deliver this metric, which the
 *    runtime engine matches against [AdapterReadiness] to produce
 *    actionable [CoverageRoute] entries.
 *
 * Out of scope for this registry:
 *  - Companion-provided metrics (TYPING_CADENCE, MOOD_DRIFT_SCORE,
 *    FALL_EVENT, TOBACCO_USE, etc.) — those need an "install companion"
 *    flow which is its own design problem.
 *  - Reproductive metrics (BASAL_BODY_TEMPERATURE, CYCLE_PHASE, CYCLE_DAY,
 *    MENSTRUATION_ONSET) — those live in the isolated `ReproductiveDatabase`
 *    behind their own entry surface and aren't part of the general health
 *    coverage view.
 */
data class MetricCoverageSpec(
    val metricType: MetricType,
    val stalenessBudgetMs: Long,
    val routeKinds: List<CoverageRouteKind>
)

object MetricCoverageRegistry {

    private const val H = 60L * 60_000L                  // 1 hour
    private const val D = 24L * H                        // 1 day
    private const val W = 7L * D                         // 1 week
    private const val MONTH = 30L * D                    // 30 days
    private const val SIX_MONTHS = 6L * MONTH

    /**
     * The set of metrics surfaced by the Data Coverage feature. Adding a
     * new wearable / biomarker is a matter of adding an entry here — the
     * UI and engine pick it up automatically.
     */
    val specs: List<MetricCoverageSpec> = listOf(
        // -- Cardiovascular (continuous wearable) --
        wearable(MetricType.HEART_RATE),
        wearable(MetricType.HEART_RATE_VARIABILITY, alsoFromPpgCapture = true),
        wearable(MetricType.RESTING_HEART_RATE),
        wearable(MetricType.BLOOD_OXYGEN),

        // HRV-derived metrics — only produced by the same paths that yield
        // HEART_RATE_VARIABILITY (PPG-camera capture + direct sensor).
        derived(MetricType.PARASYMPATHETIC_TONE),
        derived(MetricType.STRESS_SCORE),
        derived(MetricType.LF_HF_RATIO),
        derived(MetricType.HRV_LF_POWER),
        derived(MetricType.HRV_HF_POWER),
        // VO2 max updates roughly weekly (vendor refresh cadence is tied to
        // the next qualifying outdoor run / fitness test). MONTH freshness
        // is the right "stale" bar — daily-freshness would flag every
        // sedentary week as a coverage gap.
        MetricCoverageSpec(
            MetricType.VO2_MAX, MONTH,
            listOf(CoverageRouteKind.HEALTH_CONNECT, CoverageRouteKind.API_ADAPTER)
        ),

        // -- Blood pressure (cuff measurement, slower-moving) --
        MetricCoverageSpec(
            MetricType.BLOOD_PRESSURE_SYSTOLIC, W,
            listOf(CoverageRouteKind.HEALTH_CONNECT, CoverageRouteKind.API_ADAPTER)
        ),
        MetricCoverageSpec(
            MetricType.BLOOD_PRESSURE_DIASTOLIC, W,
            listOf(CoverageRouteKind.HEALTH_CONNECT, CoverageRouteKind.API_ADAPTER)
        ),

        // -- Respiratory --
        wearable(MetricType.RESPIRATORY_RATE),

        // -- Temperature --
        wearable(MetricType.SKIN_TEMPERATURE),
        wearable(MetricType.SKIN_TEMPERATURE_DEVIATION),

        // -- Sleep (after waking) --
        // SLEEP_DURATION is the only sleep metric that accepts self-reported
        // entry — the stage-derived metrics (latency, efficiency, fragmentation)
        // need per-minute stage data that a human can't reasonably produce.
        MetricCoverageSpec(
            MetricType.SLEEP_DURATION, 36 * H,
            listOf(
                CoverageRouteKind.HEALTH_CONNECT,
                CoverageRouteKind.API_ADAPTER,
                CoverageRouteKind.MANUAL_ENTRY
            )
        ),
        MetricCoverageSpec(
            MetricType.SLEEP_LATENCY, 36 * H,
            listOf(CoverageRouteKind.HEALTH_CONNECT, CoverageRouteKind.API_ADAPTER)
        ),
        MetricCoverageSpec(
            MetricType.SLEEP_EFFICIENCY, 36 * H,
            listOf(CoverageRouteKind.HEALTH_CONNECT, CoverageRouteKind.API_ADAPTER)
        ),
        MetricCoverageSpec(
            MetricType.SLEEP_FRAGMENTATION_INDEX, 36 * H,
            listOf(CoverageRouteKind.HEALTH_CONNECT, CoverageRouteKind.API_ADAPTER)
        ),
        MetricCoverageSpec(
            MetricType.WAKE_AFTER_SLEEP_ONSET, 36 * H,
            listOf(CoverageRouteKind.HEALTH_CONNECT, CoverageRouteKind.API_ADAPTER)
        ),
        MetricCoverageSpec(
            MetricType.SLEEP_SCORE, 36 * H,
            listOf(CoverageRouteKind.HEALTH_CONNECT, CoverageRouteKind.API_ADAPTER)
        ),

        // -- Activity --
        MetricCoverageSpec(
            MetricType.STEPS, D,
            listOf(
                CoverageRouteKind.HEALTH_CONNECT,
                CoverageRouteKind.API_ADAPTER,
                CoverageRouteKind.PHONE_SENSOR
            )
        ),
        MetricCoverageSpec(
            MetricType.ACTIVE_MINUTES, D,
            listOf(CoverageRouteKind.HEALTH_CONNECT, CoverageRouteKind.API_ADAPTER)
        ),
        MetricCoverageSpec(
            MetricType.ACTIVE_CALORIES, D,
            listOf(CoverageRouteKind.HEALTH_CONNECT, CoverageRouteKind.API_ADAPTER)
        ),

        // -- Metabolic --
        MetricCoverageSpec(
            MetricType.BLOOD_GLUCOSE, 4 * H,
            listOf(CoverageRouteKind.HEALTH_CONNECT, CoverageRouteKind.API_ADAPTER)
        ),
        MetricCoverageSpec(
            MetricType.BODY_MASS, MONTH,
            listOf(CoverageRouteKind.HEALTH_CONNECT, CoverageRouteKind.API_ADAPTER)
        ),
        MetricCoverageSpec(
            MetricType.BODY_FAT_PCT, MONTH,
            listOf(CoverageRouteKind.API_ADAPTER)
        ),

        // -- Recovery --
        MetricCoverageSpec(
            MetricType.RECOVERY_SCORE, D,
            listOf(CoverageRouteKind.API_ADAPTER)
        ),

        // -- Environment --
        MetricCoverageSpec(
            MetricType.AMBIENT_LIGHT, H,
            listOf(CoverageRouteKind.PHONE_SENSOR)
        ),

        // -- Biomarkers (clinical re-test cadence ≈ 6 months) --
        biomarker(MetricType.HBA1C),
        biomarker(MetricType.HSCRP),
        biomarker(MetricType.TOTAL_CHOLESTEROL),
        biomarker(MetricType.LDL_CHOLESTEROL),
        biomarker(MetricType.HDL_CHOLESTEROL),
        biomarker(MetricType.TRIGLYCERIDES),
        biomarker(MetricType.APO_B),
        biomarker(MetricType.VITAMIN_D_25OH),
        biomarker(MetricType.TSH),
        biomarker(MetricType.FREE_T4),
        biomarker(MetricType.FREE_T3),
        biomarker(MetricType.HEMOGLOBIN),
        biomarker(MetricType.HEMATOCRIT),
        biomarker(MetricType.WBC),
        biomarker(MetricType.RBC),
        biomarker(MetricType.PLATELETS),
    )

    val byMetric: Map<MetricType, MetricCoverageSpec> = specs.associateBy { it.metricType }

    private fun wearable(
        metric: MetricType,
        alsoFromPpgCapture: Boolean = false
    ): MetricCoverageSpec {
        val routes = buildList {
            add(CoverageRouteKind.HEALTH_CONNECT)
            add(CoverageRouteKind.API_ADAPTER)
            add(CoverageRouteKind.DIRECT_SENSOR)
            if (alsoFromPpgCapture) add(CoverageRouteKind.PPG_CAPTURE)
        }
        return MetricCoverageSpec(metric, D, routes)
    }

    /** HRV-derived autonomic scores: same paths as HEART_RATE_VARIABILITY. */
    private fun derived(metric: MetricType) = MetricCoverageSpec(
        metric, D,
        listOf(CoverageRouteKind.PPG_CAPTURE, CoverageRouteKind.DIRECT_SENSOR)
    )

    private fun biomarker(metric: MetricType) = MetricCoverageSpec(
        metric, SIX_MONTHS,
        listOf(CoverageRouteKind.MANUAL_ENTRY, CoverageRouteKind.FHIR_IMPORT)
    )
}
