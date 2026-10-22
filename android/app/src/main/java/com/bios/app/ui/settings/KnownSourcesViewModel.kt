package com.bios.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bios.app.ingest.OwnerAction
import com.bios.app.ingest.SourceCadence
import com.bios.app.model.SourceType
import com.bios.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One source Bios has stored data from: its display name, the metrics it
 * has delivered, the timestamp of its newest stored reading, and a
 * freshness verdict. Read-only; see docs/specs/known-sources.md.
 */
data class KnownSource(
    val displayName: String,
    val typeLabel: String,
    val metrics: List<String>,
    val lastReadingAt: Long,
    val readingCount: Int,
    val state: Freshness,
    val ownerAction: OwnerAction,
) {
    enum class Freshness { CURRENT, STALE, ATTENTION, UNKNOWN }
}

/**
 * Builds the known-sources list from what already exists: the
 * `data_sources` registry, the readings' per-source freshness and metric
 * types, and [com.bios.app.ingest.SourceHealthStore]. No new query, no
 * write. A reading's `sourceId` is the `DataSource.id`, so the registry
 * joins to the readings on that id; a `sourceId` seen in readings but not
 * in the registry still gets a row (criterion 1).
 */
class KnownSourcesViewModel(
    private val app: AppViewModel,
) : ViewModel() {

    private val _sources = MutableStateFlow<List<KnownSource>>(emptyList())
    val sources: StateFlow<List<KnownSource>> = _sources.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _sources.value = withContext(Dispatchers.IO) { build() }
            _isLoading.value = false
        }
    }

    private suspend fun build(): List<KnownSource> {
        val now = System.currentTimeMillis()
        val dao = app.db.metricReadingDao()
        val registry = app.db.dataSourceDao().getAll().associateBy { it.id }
        val freshness = dao.sourceFreshness().associateBy { it.sourceId }
        val metricsBySource = dao.sourceMetricTypes()
            .groupBy({ it.sourceId }, { prettyMetric(it.metricType) })

        // Every sourceId that appears in readings, plus any registered
        // source that has not delivered yet (so a fresh connection shows).
        val ids = (freshness.keys + registry.keys).toSet()

        return ids.map { id ->
            val reg = registry[id]
            val fresh = freshness[id]
            val type = reg?.sourceType?.let { key -> SourceType.entries.firstOrNull { it.key == key } }
            val health = type?.let { app.sourceHealthStore.get(it) }
            val last = fresh?.lastTimestamp ?: 0L
            val state = freshnessOf(type, last, now, health?.hasOpenError == true)
            KnownSource(
                displayName = displayName(reg?.deviceName, reg?.deviceModel, type, id),
                typeLabel = type?.let { typeLabel(it) } ?: "Unknown source",
                metrics = (metricsBySource[id] ?: emptyList()).sorted(),
                lastReadingAt = last,
                readingCount = fresh?.readingCount ?: 0,
                state = state,
                ownerAction = health?.ownerAction ?: OwnerAction.NONE,
            )
        }.sortedByDescending { it.lastReadingAt }
    }

    private fun freshnessOf(
        type: SourceType?,
        lastReadingAt: Long,
        now: Long,
        hasOpenError: Boolean,
    ): KnownSource.Freshness {
        if (hasOpenError) return KnownSource.Freshness.ATTENTION
        if (lastReadingAt <= 0L) return KnownSource.Freshness.UNKNOWN
        val interval = type?.let { SourceCadence.intervalMillis(it) }
            ?: return KnownSource.Freshness.UNKNOWN
        val window = interval * SourceCadence.STALE_INTERVALS
        return if (now - lastReadingAt <= window) KnownSource.Freshness.CURRENT
        else KnownSource.Freshness.STALE
    }

    private fun displayName(
        deviceName: String?,
        deviceModel: String?,
        type: SourceType?,
        id: String,
    ): String = when {
        !deviceName.isNullOrBlank() && !deviceModel.isNullOrBlank() && deviceModel != deviceName ->
            "$deviceName ($deviceModel)"
        !deviceName.isNullOrBlank() -> deviceName
        type != null -> typeLabel(type)
        else -> id
    }

    companion object {
        fun typeLabel(type: SourceType): String = when (type) {
            SourceType.HEALTH_CONNECT -> "Health Connect"
            SourceType.GADGETBRIDGE -> "Gadgetbridge"
            SourceType.DIRECT_SENSOR -> "Phone sensors (direct)"
            SourceType.PHONE_SENSOR -> "Phone sensors"
            SourceType.PHONE_SENSOR_DERIVED -> "Phone (derived)"
            SourceType.BIOS_INFERRED -> "Bios (inferred)"
            SourceType.OURA_API -> "Oura"
            SourceType.WHOOP_API -> "WHOOP"
            SourceType.GARMIN_API -> "Garmin"
            SourceType.WITHINGS_API -> "Withings"
            SourceType.DEXCOM_API -> "Dexcom"
            SourceType.POLAR_API -> "Polar"
            SourceType.COROS_API -> "COROS Training Hub"
            SourceType.CAMERA_PPG -> "Camera (PPG)"
            SourceType.BLE_PERIPHERAL -> "Bluetooth sensor"
            SourceType.SELF_REPORTED -> "Self-reported"
        }

        /** Metric key to a readable label: underscores to spaces, capitalised. */
        fun prettyMetric(key: String): String =
            key.replace('_', ' ').split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
