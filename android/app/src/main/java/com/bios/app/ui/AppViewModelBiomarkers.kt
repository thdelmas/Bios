package com.bios.app.ui

import com.bios.app.data.BiomarkerContext
import com.bios.app.data.BiomarkerEntryRepo
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manual biomarker entry slice of [AppViewModel]. Kept separate so the
 * main view-model file stays under the 500-line cap while keeping a flat
 * call surface (`viewModel.biomarkers.add(...)`) for the entry screen.
 *
 * The screen still talks to [AppViewModel] for the underlying
 * [BiomarkerEntryRepo] when it needs to format / annotate readings — the
 * repo is held here so there's a single source of truth.
 */
class AppViewModelBiomarkers(
    val repo: BiomarkerEntryRepo,
    private val scope: CoroutineScope,
    private val errorSink: MutableStateFlow<String?>,
) {
    private val _recent = MutableStateFlow<List<MetricReading>>(emptyList())
    val recent: StateFlow<List<MetricReading>> = _recent.asStateFlow()

    fun refreshRecent(limit: Int = 20) {
        scope.launch { _recent.value = repo.fetchRecent(limit) }
    }

    fun addManual(
        metricType: MetricType,
        value: Double,
        timestamp: Long,
        context: BiomarkerContext = BiomarkerContext(),
    ) {
        if (metricType.domain != MetricDomain.BIOMARKER) {
            errorSink.value = "Manual entry is only supported for biomarker metrics."
            return
        }
        scope.launch {
            repo.add(metricType, value, timestamp, context)
            _recent.value = repo.fetchRecent(20)
        }
    }
}
