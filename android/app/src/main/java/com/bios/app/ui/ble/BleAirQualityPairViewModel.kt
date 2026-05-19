package com.bios.app.ui.ble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bios.app.ingest.BleAirQualityAdapter
import com.bios.app.ingest.BlePairedDeviceStore
import com.bios.app.ingest.IngestManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the BLE air-quality pair screen (#43 phase 2). Drives the
 * scan flow off [BleAirQualityAdapter], tracks discovered devices keyed by
 * address, and bridges pair/unpair into [IngestManager] so the
 * `SourceType.BLE_PERIPHERAL` row exists before any reading lands.
 *
 * The connection state is forwarded from the adapter for the "Connected /
 * Waiting for connection…" hint on the paired card.
 */
class BleAirQualityPairViewModel(
    private val adapter: BleAirQualityAdapter,
    private val ingestManager: IngestManager,
) : ViewModel() {

    private val _discovered = MutableStateFlow<List<BleAirQualityAdapter.Discovered>>(emptyList())
    val discovered: StateFlow<List<BleAirQualityAdapter.Discovered>> = _discovered.asStateFlow()

    private val _paired = MutableStateFlow(adapter.pairedDevice())
    val paired: StateFlow<BlePairedDeviceStore.Paired?> = _paired.asStateFlow()

    val isConnected: StateFlow<Boolean> = adapter.isConnected

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    private val _bleEnabled = MutableStateFlow(adapter.isReady() || adapter.hasScanPermission())
    val bleEnabled: StateFlow<Boolean> = _bleEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    init {
        refreshPermissionState()
    }

    fun requiredPermissions(): Array<String> = adapter.requiredRuntimePermissions()

    fun refreshPermissionState() {
        _hasPermissions.value = adapter.hasScanPermission() && adapter.hasConnectPermission()
        // Treat the adapter as "off" only when permissions are present but
        // isReady() still returns false — that's the case where the
        // BluetoothAdapter is null or its `isEnabled` is false. Without perms
        // we can't tell, so default to "on" and let the perm CTA take
        // precedence in the UI.
        _bleEnabled.value = !_hasPermissions.value || adapter.isReady()
    }

    fun startScan() {
        if (_isScanning.value) return
        if (!adapter.hasScanPermission()) return
        _isScanning.value = true
        _discovered.value = emptyList()
        scanJob = viewModelScope.launch {
            adapter.scan().collect { d ->
                _discovered.value = upsert(_discovered.value, d)
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    fun pair(address: String, name: String) {
        stopScan()
        adapter.pair(address, name)
        _paired.value = adapter.pairedDevice()
        viewModelScope.launch { ingestManager.onBleAirQualityPaired() }
    }

    fun unpair() {
        adapter.unpair()
        _paired.value = null
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }

    private fun upsert(
        list: List<BleAirQualityAdapter.Discovered>,
        new: BleAirQualityAdapter.Discovered,
    ): List<BleAirQualityAdapter.Discovered> {
        val idx = list.indexOfFirst { it.address == new.address }
        return if (idx >= 0) list.toMutableList().apply { set(idx, new) }
        else list + new
    }
}
