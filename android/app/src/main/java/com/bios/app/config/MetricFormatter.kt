package com.bios.app.config

import com.bios.app.model.MetricType
import com.bios.app.model.MetricUnit

/**
 * Formats metric values for display using the active RegionConfig.
 * Internal storage is always SI/metric; this handles presentation.
 */
class MetricFormatter(private val config: RegionConfig) {

    /**
     * Formats a metric value for display with locale-appropriate units.
     * [metricValue] is always in the internal (metric) unit.
     */
    fun format(metricType: MetricType, metricValue: Double, decimals: Int = 1): String {
        val override = config.unitOverrides[metricType]
        if (override != null) {
            val converted = override.fromMetric(metricValue)
            return "${String.format("%.${decimals}f", converted)} ${override.symbol}"
        }
        return "${String.format("%.${decimals}f", metricValue)} ${metricType.unit.symbol}"
    }

    /**
     * Returns the display unit symbol for a metric type.
     */
    fun unitSymbol(metricType: MetricType): String {
        return config.unitOverrides[metricType]?.symbol ?: metricType.unit.symbol
    }

    /**
     * Converts a user-entered value (in display units) to internal metric units.
     */
    fun toMetric(metricType: MetricType, displayValue: Double): Double {
        val override = config.unitOverrides[metricType] ?: return displayValue
        return override.toMetric(displayValue)
    }

    /**
     * Formats a glucose value using the region's preferred unit (mg/dL or mmol/L).
     */
    fun formatGlucose(mgPerDl: Double, decimals: Int = 1): String {
        return if (config.clinicalThresholds.glucoseInMmol) {
            val mmol = mgPerDl / 18.0
            "${String.format("%.${decimals}f", mmol)} mmol/L"
        } else {
            "${String.format("%.${decimals}f", mgPerDl)} mg/dL"
        }
    }

    /**
     * Returns the regulatory disclaimer for health alerts.
     */
    fun alertDisclaimer(): String = config.regulatory.alertDisclaimer
}
