package com.bios.app.ingest

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import com.bios.app.model.SleepStage
import java.time.Instant
import java.util.UUID

/**
 * Bridges Health Connect data into Bios unified MetricReadings.
 */
class HealthConnectAdapter(private val context: Context) {

    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    // Permissions we request
    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(SkinTemperatureRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasAllPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return permissions.all { it in granted }
    }

    // MARK: - Fetch all readings

    suspend fun fetchReadings(
        startTime: Instant,
        endTime: Instant,
        sourceId: String
    ): List<MetricReading> {
        val readings = mutableListOf<MetricReading>()

        readings += fetchHeartRate(startTime, endTime, sourceId)
        readings += fetchHRV(startTime, endTime, sourceId)
        readings += fetchRestingHR(startTime, endTime, sourceId)
        readings += fetchSpO2(startTime, endTime, sourceId)
        readings += fetchRespiratoryRate(startTime, endTime, sourceId)
        readings += fetchSkinTemp(startTime, endTime, sourceId)
        readings += fetchSleep(startTime, endTime, sourceId)
        readings += fetchSteps(startTime, endTime, sourceId)
        readings += fetchActiveCalories(startTime, endTime, sourceId)

        return readings
    }

    // MARK: - Individual record types

    private suspend fun fetchHeartRate(
        start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val response = client.readRecords(
            ReadRecordsRequest(
                HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.flatMap { record ->
            record.samples.map { sample ->
                MetricReading(
                    metricType = MetricType.HEART_RATE.key,
                    value = sample.beatsPerMinute.toDouble(),
                    timestamp = sample.time.toEpochMilli(),
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }
        }
    }

    private suspend fun fetchHRV(
        start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val response = client.readRecords(
            ReadRecordsRequest(
                HeartRateVariabilityRmssdRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.map { record ->
            MetricReading(
                metricType = MetricType.HEART_RATE_VARIABILITY.key,
                value = record.heartRateVariabilityMillis,
                timestamp = record.time.toEpochMilli(),
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }
    }

    private suspend fun fetchRestingHR(
        start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val response = client.readRecords(
            ReadRecordsRequest(
                RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.map { record ->
            MetricReading(
                metricType = MetricType.RESTING_HEART_RATE.key,
                value = record.beatsPerMinute.toDouble(),
                timestamp = record.time.toEpochMilli(),
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }
    }

    private suspend fun fetchSpO2(
        start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val response = client.readRecords(
            ReadRecordsRequest(
                OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.map { record ->
            MetricReading(
                metricType = MetricType.BLOOD_OXYGEN.key,
                value = record.percentage.value,
                timestamp = record.time.toEpochMilli(),
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }
    }

    private suspend fun fetchRespiratoryRate(
        start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val response = client.readRecords(
            ReadRecordsRequest(
                RespiratoryRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.map { record ->
            MetricReading(
                metricType = MetricType.RESPIRATORY_RATE.key,
                value = record.rate,
                timestamp = record.time.toEpochMilli(),
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }
    }

    private suspend fun fetchSkinTemp(
        start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val response = client.readRecords(
            ReadRecordsRequest(
                SkinTemperatureRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.flatMap { record ->
            val baseline = record.baseline?.inCelsius ?: 0.0
            record.deltas.map { delta ->
                MetricReading(
                    metricType = MetricType.SKIN_TEMPERATURE_DEVIATION.key,
                    value = delta.delta.inCelsius,
                    timestamp = delta.time.toEpochMilli(),
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }
        }
    }

    private suspend fun fetchSleep(
        start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val response = client.readRecords(
            ReadRecordsRequest(
                SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.flatMap { session ->
            session.stages.mapNotNull { stage ->
                val biosStage = when (stage.stage) {
                    SleepSessionRecord.STAGE_TYPE_AWAKE,
                    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStage.AWAKE
                    SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.LIGHT
                    SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
                    SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
                    else -> null
                } ?: return@mapNotNull null

                val durationSec = java.time.Duration.between(
                    stage.startTime, stage.endTime
                ).seconds.toInt()

                MetricReading(
                    metricType = MetricType.SLEEP_STAGE.key,
                    value = biosStage.value.toDouble(),
                    timestamp = stage.startTime.toEpochMilli(),
                    durationSec = durationSec,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }
        }
    }

    private suspend fun fetchSteps(
        start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val response = client.readRecords(
            ReadRecordsRequest(
                StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.map { record ->
            val durationSec = java.time.Duration.between(
                record.startTime, record.endTime
            ).seconds.toInt()

            MetricReading(
                metricType = MetricType.STEPS.key,
                value = record.count.toDouble(),
                timestamp = record.startTime.toEpochMilli(),
                durationSec = durationSec,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }
    }

    private suspend fun fetchActiveCalories(
        start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val response = client.readRecords(
            ReadRecordsRequest(
                ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.map { record ->
            MetricReading(
                metricType = MetricType.ACTIVE_CALORIES.key,
                value = record.energy.inKilocalories,
                timestamp = record.startTime.toEpochMilli(),
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }
    }
}
