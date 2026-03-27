package com.bios.app.ui.diagnostics

import com.bios.app.alerts.ConditionPattern
import com.bios.app.alerts.DeviationDirection
import com.bios.app.model.MetricType

data class SignalStatus(
    val metricType: MetricType,
    val direction: DeviationDirection,
    val thresholdSigma: Double,
    val weight: Double,
    val currentZScore: Double?,
    val isActive: Boolean,
    val hasBaseline: Boolean
)

data class DiagnosticResult(
    val pattern: ConditionPattern,
    val probability: Double,
    val signals: List<SignalStatus>,
    val activeSignalCount: Int,
    val hasEnoughData: Boolean
)
