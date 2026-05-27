package com.bios.app.ingest

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import java.time.Instant

/**
 * Body-composition reads from Health Connect — weight, body-fat %, and
 * lean mass. Extracted to a sibling file because [HealthConnectAdapter]
 * is at its 500-line ceiling; the three reads are mechanical and benefit
 * from being grouped (the permission set, the parallel-fetch trio, and
 * the canonical-key mapping all live together).
 *
 * HC does not expose hydration / total-body-water or bone mass —
 * Withings impedance scales produce those, and Bios pulls them via the
 * Withings adapter directly (see [WithingsApiAdapter.parseGroupMeasures]).
 */
internal object HealthConnectBodyComposition {

    /** Permissions required to read body-composition records from HC. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
    )

    /**
     * Fetch every body-composition record HC carries in the window.
     * Returns canonical [MetricReading]s — weight as `BODY_MASS` (kg),
     * fat ratio as `BODY_FAT_PCT` (%), and lean mass as `LEAN_MASS` (kg).
     * HC's `BodyFatRecord` reports a fraction in `0.0–1.0`; this maps
     * to a percent at the boundary so the bus stays in the same scale
     * Withings emits.
     */
    suspend fun fetchAll(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        sourceId: String,
    ): List<MetricReading> = buildList {
        addAll(readWeight(client, start, end, sourceId))
        addAll(readBodyFat(client, start, end, sourceId))
        addAll(readLeanMass(client, start, end, sourceId))
    }

    private suspend fun readWeight(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(WeightRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.map { record ->
        MetricReading(
            id = HealthConnectStableIds.forRecord("body-mass", sourceId, record.metadata.id),
            metricType = MetricType.BODY_MASS.key,
            value = record.weight.inKilograms,
            timestamp = record.time.toEpochMilli(),
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
        )
    }

    private suspend fun readBodyFat(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(BodyFatRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.map { record ->
        MetricReading(
            id = HealthConnectStableIds.forRecord("body-fat", sourceId, record.metadata.id),
            metricType = MetricType.BODY_FAT_PCT.key,
            // HC reports body fat as a percentage (0–100), confusingly via
            // a `Percentage` type — `.value` is the 0–100 number, matching
            // Withings type 6 and the rest of the bus.
            value = record.percentage.value,
            timestamp = record.time.toEpochMilli(),
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
        )
    }

    private suspend fun readLeanMass(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(LeanBodyMassRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.map { record ->
        MetricReading(
            id = HealthConnectStableIds.forRecord("lean-mass", sourceId, record.metadata.id),
            metricType = MetricType.LEAN_MASS.key,
            value = record.mass.inKilograms,
            timestamp = record.time.toEpochMilli(),
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
        )
    }
}
