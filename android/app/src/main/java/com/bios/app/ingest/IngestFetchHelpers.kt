package com.bios.app.ingest

import com.bios.app.model.MetricReading

// Fetch helpers moved out of IngestManager under the 500-line ceiling. They
// carry no manager state: each takes the adapter and the registered sourceId.

private const val SENSOR_SAMPLE_DURATION_MS = 10_000L // 10 seconds

/** Generic adapter-fetch helper. Returns empty when the source row
 *  hasn't been registered or the adapter is null; swallows fetch
 *  failures so a transient 5xx from one adapter doesn't kill the sync.
 *  Adapters that can say *why* they failed report it to
 *  [SourceHealthStore] themselves before returning empty. */
internal suspend inline fun <T> fetchApiReadings(
    sourceId: String?,
    adapter: T?,
    crossinline fetch: suspend (T, String) -> List<MetricReading>,
): List<MetricReading> {
    if (sourceId == null || adapter == null) return emptyList()
    return runCatching { fetch(adapter, sourceId) }.getOrDefault(emptyList())
}

internal suspend fun fetchDirectSensorReadings(
    adapter: DirectSensorAdapter?,
    sourceId: String?,
): List<MetricReading> {
    if (sourceId == null || adapter == null) return emptyList()
    return try {
        val readings = mutableListOf<MetricReading>()
        readings += adapter.sampleHeartRate(SENSOR_SAMPLE_DURATION_MS, sourceId)
        readings += adapter.sampleHrv(SENSOR_SAMPLE_DURATION_MS, sourceId)
        val steps = adapter.readSteps(sourceId)
        if (steps != null) readings += steps
        readings
    } catch (_: Exception) {
        emptyList()
    }
}

internal suspend fun fetchPhoneSensorReadings(
    adapter: PhoneSensorAdapter?,
    sourceId: String?,
): List<MetricReading> {
    if (sourceId == null || adapter == null) return emptyList()
    return try {
        val readings = mutableListOf<MetricReading>()
        readings += adapter.sampleAccelerometer(SENSOR_SAMPLE_DURATION_MS, sourceId)
        val stepReading = adapter.readStepCounter(sourceId)
        if (stepReading != null) readings += stepReading
        val lightReading = adapter.sampleAmbientLight(sourceId)
        if (lightReading != null) readings += lightReading
        readings
    } catch (_: Exception) {
        emptyList()
    }
}
