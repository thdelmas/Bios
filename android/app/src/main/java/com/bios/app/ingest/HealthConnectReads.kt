package com.bios.app.ingest

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.SleepStage
import com.bios.contracts.MetricType
import java.time.Instant

/**
 * Per-record-type reads from Health Connect. Extracted from
 * [HealthConnectAdapter] so the adapter stays under the project's 500-line
 * ceiling and each read carries a stable
 * [HealthConnectStableIds]-derived `MetricReading.id` (#312 follow-up).
 *
 * Every reading's primary key is anchored on the HC record's `metadata.id`,
 * so `OnConflictStrategy.REPLACE` updates the row in place when HC re-emits
 * the same record — even after HC silently shifts the record's timestamp.
 *
 * Kept as a flat `internal object` rather than a class so callers don't
 * have to thread a constructor through; the [client] is supplied per call
 * by [HealthConnectAdapter], which owns it.
 */
internal object HealthConnectReads {

    suspend fun heartRate(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.flatMap { record ->
        record.samples.map { sample ->
            val sampleMs = sample.time.toEpochMilli()
            MetricReading(
                id = HealthConnectStableIds.forSubRecord("hr-sample", sourceId, record.metadata.id, sampleMs),
                metricType = MetricType.HEART_RATE.key,
                value = sample.beatsPerMinute.toDouble(),
                timestamp = sampleMs,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level,
            )
        }
    }

    suspend fun hrv(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.map { record ->
        MetricReading(
            id = HealthConnectStableIds.forRecord("hrv", sourceId, record.metadata.id),
            metricType = MetricType.HEART_RATE_VARIABILITY.key,
            value = record.heartRateVariabilityMillis,
            timestamp = record.time.toEpochMilli(),
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level,
        )
    }

    suspend fun restingHr(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(RestingHeartRateRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.map { record ->
        MetricReading(
            id = HealthConnectStableIds.forRecord("resting-hr", sourceId, record.metadata.id),
            metricType = MetricType.RESTING_HEART_RATE.key,
            value = record.beatsPerMinute.toDouble(),
            timestamp = record.time.toEpochMilli(),
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level,
        )
    }

    suspend fun spo2(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(OxygenSaturationRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.map { record ->
        MetricReading(
            id = HealthConnectStableIds.forRecord("spo2", sourceId, record.metadata.id),
            metricType = MetricType.BLOOD_OXYGEN.key,
            value = record.percentage.value,
            timestamp = record.time.toEpochMilli(),
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level,
        )
    }

    suspend fun respiratoryRate(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(RespiratoryRateRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.map { record ->
        MetricReading(
            id = HealthConnectStableIds.forRecord("respiratory-rate", sourceId, record.metadata.id),
            metricType = MetricType.RESPIRATORY_RATE.key,
            value = record.rate,
            timestamp = record.time.toEpochMilli(),
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level,
        )
    }

    suspend fun skinTemp(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(SkinTemperatureRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.flatMap { record ->
        val baselineC = record.baseline?.inCelsius
        val recordId = record.metadata.id
        record.deltas.flatMap { delta ->
            val tMs = delta.time.toEpochMilli()
            val deviation = MetricReading(
                id = HealthConnectStableIds.forSubRecord("skin-temp-deviation", sourceId, recordId, tMs),
                metricType = MetricType.SKIN_TEMPERATURE_DEVIATION.key,
                value = delta.delta.inCelsius,
                timestamp = tMs,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level,
            )
            // Reconstruct raw absolute temp when the record supplies a baseline.
            // Needed for febrile-range thresholds and menstrual phase detection,
            // which require absolute °C rather than personal-baseline deltas.
            if (baselineC != null) {
                listOf(
                    deviation,
                    MetricReading(
                        id = HealthConnectStableIds.forSubRecord("skin-temp", sourceId, recordId, tMs),
                        metricType = MetricType.SKIN_TEMPERATURE.key,
                        value = baselineC + delta.delta.inCelsius,
                        timestamp = tMs,
                        sourceId = sourceId,
                        confidence = ConfidenceTier.MEDIUM.level,
                    ),
                )
            } else {
                listOf(deviation)
            }
        }
    }

    suspend fun sleep(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.flatMap { session ->
        val sessionId = session.metadata.id
        val stageReadings = session.stages.mapNotNull { stage ->
            val biosStage = when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_AWAKE,
                SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStage.AWAKE
                SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.LIGHT
                SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
                SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
                else -> null
            } ?: return@mapNotNull null

            val durationSec = java.time.Duration.between(stage.startTime, stage.endTime).seconds.toInt()
            val stageStartMs = stage.startTime.toEpochMilli()
            MetricReading(
                id = HealthConnectStableIds.forSubRecord("sleep-stage", sourceId, sessionId, stageStartMs),
                metricType = MetricType.SLEEP_STAGE.key,
                value = biosStage.value.toDouble(),
                timestamp = stageStartMs,
                durationSec = durationSec,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level,
            )
        }

        // Asleep time = session total minus AWAKE stages (matches Oura/Whoop semantics).
        // If the session has no stage breakdown, fall back to the full session length.
        val awakeSec = stageReadings
            .filter { it.value.toInt() == SleepStage.AWAKE.value }
            .sumOf { it.durationSec ?: 0 }
        val sessionSec = java.time.Duration.between(session.startTime, session.endTime).seconds.toInt()
        val asleepSec = (sessionSec - awakeSec).coerceAtLeast(0)

        val durationReading = MetricReading(
            id = HealthConnectStableIds.forRecord("sleep-duration", sourceId, sessionId),
            metricType = MetricType.SLEEP_DURATION.key,
            value = asleepSec.toDouble(),
            timestamp = session.endTime.toEpochMilli(),
            durationSec = sessionSec,
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level,
        )

        stageReadings + durationReading
    }

    suspend fun steps(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(StepsRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.map { record ->
        val durationSec = java.time.Duration.between(record.startTime, record.endTime).seconds.toInt()
        MetricReading(
            id = HealthConnectStableIds.forRecord("steps", sourceId, record.metadata.id),
            metricType = MetricType.STEPS.key,
            value = record.count.toDouble(),
            timestamp = record.startTime.toEpochMilli(),
            durationSec = durationSec,
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level,
        )
    }

    suspend fun activeCalories(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.map { record ->
        MetricReading(
            id = HealthConnectStableIds.forRecord("active-calories", sourceId, record.metadata.id),
            metricType = MetricType.ACTIVE_CALORIES.key,
            value = record.energy.inKilocalories,
            timestamp = record.startTime.toEpochMilli(),
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level,
        )
    }

    // VO2 max is vendor-derived (Garmin / Fitbit / Apple fitness models); not a clinical CPET.
    suspend fun vo2Max(
        client: HealthConnectClient, start: Instant, end: Instant, sourceId: String,
    ): List<MetricReading> = client.readRecords(
        ReadRecordsRequest(Vo2MaxRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records.map { record ->
        MetricReading(
            id = HealthConnectStableIds.forRecord("vo2-max", sourceId, record.metadata.id),
            metricType = MetricType.VO2_MAX.key,
            value = record.vo2MillilitersPerMinuteKilogram,
            timestamp = record.time.toEpochMilli(),
            sourceId = sourceId,
            confidence = ConfidenceTier.VENDOR_DERIVED.level,
        )
    }
}
