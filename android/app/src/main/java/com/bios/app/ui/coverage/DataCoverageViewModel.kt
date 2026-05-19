package com.bios.app.ui.coverage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bios.app.engine.AdapterReadiness
import com.bios.app.engine.MetricCoverage
import com.bios.app.engine.MetricCoverageEngine
import com.bios.app.ingest.DexcomApiAdapter
import com.bios.app.ingest.PolarApiAdapter
import com.bios.app.ingest.WithingsApiAdapter
import com.bios.app.ui.AppViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Surface-level ViewModel for the Data Coverage screen. Snapshots adapter
 * readiness from [AppViewModel] and asks [MetricCoverageEngine] for the
 * per-metric status + routes; re-snapshots on demand via [refresh].
 *
 * Plain `ViewModel` (not AndroidViewModel) — the data sources it needs
 * live on the shared [AppViewModel] instance the caller passes in.
 */
class DataCoverageViewModel(
    private val app: AppViewModel
) : ViewModel() {

    private val _coverage = MutableStateFlow<List<MetricCoverage>>(emptyList())
    val coverage: StateFlow<List<MetricCoverage>> = _coverage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val readiness = AdapterReadiness(
                healthConnectGranted = app.hasPermissions.value,
                ouraConfigured = app.ouraTokenStore.hasToken(),
                withingsConfigured = app.apiTokenStore.hasToken(WithingsApiAdapter.PROVIDER_KEY),
                polarConfigured = app.apiTokenStore.hasToken(PolarApiAdapter.PROVIDER_KEY),
                dexcomConfigured = app.apiTokenStore.hasToken(DexcomApiAdapter.PROVIDER_KEY),
                directSensorAvailable = app.directSensorAdapter.hasAnySensor,
                phoneSensorAvailable = app.phoneSensorAdapter.hasAccelerometer,
                ppgCaptureAvailable = true,  // any phone with a rear camera + flash
                bleAirQualityPaired = app.bleAirQualityAdapter.isPaired,
            )
            val dao = app.db.metricReadingDao()
            _coverage.value = MetricCoverageEngine.compute(
                readiness = readiness,
                nowMillis = System.currentTimeMillis(),
                lastTimestampFor = { dao.lastTimestampFor(it) }
            )
            _isLoading.value = false
        }
    }
}
