package com.bios.app

import com.bios.app.data.dao.DataSourceDao
import com.bios.app.data.dao.SourceMetricToggleDao
import com.bios.app.ingest.SourceMetricToggleFilter
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.SourceMetricToggle
import com.bios.app.model.SourceType
import com.bios.contracts.MetricType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the per-(source, metric) ingest gate.
 *
 * The filter is intentionally a fast no-op when no toggle rows exist —
 * this is the common path for owners who never visit the new settings
 * surface, and it must not introduce a per-batch DAO round-trip in the
 * common case. The "no toggles" test pins that contract.
 */
class SourceMetricToggleFilterTest {

    @Test
    fun `empty readings short-circuits`() = runBlocking {
        val out = SourceMetricToggleFilter.apply(
            readings = emptyList(),
            toggleDao = FakeToggleDao(),
            sourceDao = FakeSourceDao(emptyList()),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `no toggles returns input unchanged`() = runBlocking {
        val source = dataSource("s1", SourceType.OURA_API.key)
        val readings = listOf(
            reading(sourceId = source.id, metricType = MetricType.HEART_RATE.key),
            reading(sourceId = source.id, metricType = MetricType.SLEEP_DURATION.key),
        )
        val out = SourceMetricToggleFilter.apply(
            readings = readings,
            toggleDao = FakeToggleDao(),
            sourceDao = FakeSourceDao(listOf(source)),
        )
        assertEquals(readings, out)
    }

    @Test
    fun `disabled pair is dropped`() = runBlocking {
        val oura = dataSource("oura", SourceType.OURA_API.key)
        val whoop = dataSource("whoop", SourceType.WHOOP_API.key)
        val readings = listOf(
            reading(sourceId = oura.id, metricType = MetricType.HEART_RATE.key),
            reading(sourceId = oura.id, metricType = MetricType.SLEEP_DURATION.key),
            reading(sourceId = whoop.id, metricType = MetricType.HEART_RATE.key),
        )
        val out = SourceMetricToggleFilter.apply(
            readings = readings,
            toggleDao = FakeToggleDao(
                disabled = listOf(
                    SourceMetricToggle(
                        sourceTypeKey = SourceType.OURA_API.key,
                        metricTypeKey = MetricType.HEART_RATE.key,
                        enabled = false,
                    ),
                )
            ),
            sourceDao = FakeSourceDao(listOf(oura, whoop)),
        )
        // Oura HR dropped; Oura sleep + WHOOP HR retained
        assertEquals(2, out.size)
        assertTrue(out.none { it.sourceId == "oura" && it.metricType == MetricType.HEART_RATE.key })
        assertTrue(out.any { it.sourceId == "oura" && it.metricType == MetricType.SLEEP_DURATION.key })
        assertTrue(out.any { it.sourceId == "whoop" && it.metricType == MetricType.HEART_RATE.key })
    }

    @Test
    fun `reading from unknown sourceId is preserved`() = runBlocking {
        // Defensive: a reading whose sourceId has no matching data_sources
        // row (FK drift, race against setup) must not be silently dropped
        // — the gate is opt-out, not opt-in.
        val readings = listOf(
            reading(sourceId = "phantom", metricType = MetricType.HEART_RATE.key),
        )
        val out = SourceMetricToggleFilter.apply(
            readings = readings,
            toggleDao = FakeToggleDao(
                disabled = listOf(
                    SourceMetricToggle(
                        sourceTypeKey = SourceType.OURA_API.key,
                        metricTypeKey = MetricType.HEART_RATE.key,
                        enabled = false,
                    ),
                )
            ),
            sourceDao = FakeSourceDao(emptyList()),
        )
        assertEquals(1, out.size)
    }

    private fun reading(
        sourceId: String,
        metricType: String,
        timestamp: Long = 1000L,
        value: Double = 70.0,
    ) = MetricReading(
        metricType = metricType,
        value = value,
        timestamp = timestamp,
        sourceId = sourceId,
        confidence = ConfidenceTier.MEDIUM.level,
    )

    private fun dataSource(id: String, sourceType: String) = DataSource(
        id = id,
        sourceType = sourceType,
        sensorType = "OPTICAL_HR",
    )

    private class FakeToggleDao(
        private val disabled: List<SourceMetricToggle> = emptyList(),
    ) : SourceMetricToggleDao {
        override suspend fun upsert(toggle: SourceMetricToggle) = Unit
        override suspend fun disabledPairs(): List<SourceMetricToggle> = disabled
        override fun allFlow(): Flow<List<SourceMetricToggle>> = flowOf(disabled)
    }

    private class FakeSourceDao(
        private val sources: List<DataSource>,
    ) : DataSourceDao {
        override suspend fun insert(source: DataSource) = Unit
        override suspend fun findByType(sourceType: String): DataSource? =
            sources.firstOrNull { it.sourceType == sourceType }
        override suspend fun findByTypeAndDeviceName(
            sourceType: String,
            deviceName: String,
        ): DataSource? = sources.firstOrNull {
            it.sourceType == sourceType && it.deviceName == deviceName
        }
        override suspend fun getAll(): List<DataSource> = sources
        override suspend fun deleteAll() = Unit
    }
}
