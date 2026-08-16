package com.bios.app.ingest

import com.bios.app.data.dao.DataSourceDao
import com.bios.app.model.DataSource
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType

/**
 * Idempotent DataSource row registration, keyed by source type. Split out of
 * IngestManager for the 500-line ceiling (same move as HealthConnectReads).
 */
internal suspend fun DataSourceDao.getOrCreate(
    type: SourceType,
    deviceName: String,
    sensorType: SensorType
): String {
    val existing = findByType(type.key)
    if (existing != null) return existing.id

    val source = DataSource(
        sourceType = type.key,
        deviceName = deviceName,
        sensorType = sensorType.name
    )
    insert(source)
    return source.id
}
