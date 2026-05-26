package com.bios.app.ingest

import com.bios.app.data.dao.DataSourceDao
import com.bios.app.data.dao.SourceMetricToggleDao
import com.bios.app.model.MetricReading

// Owner-controlled drop: any reading whose (sourceType, metricType)
// pair is toggled off in `source_metric_toggles` is silently filtered
// out before the DAO write. Default behaviour is unchanged — when no
// toggles are stored, the original list is returned unmodified.
//
// Scope: scalar readings (the `quality + derived` batch from
// IngestManager). Exercise-session writes and the BLE air-quality
// streaming path bypass this filter today; those would need their own
// gate or a unified writer wrapper to fully honour the toggle.
internal object SourceMetricToggleFilter {

    suspend fun apply(
        readings: List<MetricReading>,
        toggleDao: SourceMetricToggleDao,
        sourceDao: DataSourceDao,
    ): List<MetricReading> {
        if (readings.isEmpty()) return readings
        val disabled = toggleDao.disabledPairs()
        if (disabled.isEmpty()) return readings
        val disabledSet = disabled.map { it.sourceTypeKey to it.metricTypeKey }.toHashSet()
        val sourceIdToType = sourceDao.getAll().associate { it.id to it.sourceType }
        return readings.filter { reading ->
            val type = sourceIdToType[reading.sourceId] ?: return@filter true
            (type to reading.metricType) !in disabledSet
        }
    }
}
