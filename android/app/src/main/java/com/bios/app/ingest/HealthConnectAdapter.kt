package com.bios.app.ingest

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.EventPayloadField
import com.bios.app.model.ExerciseModality
import com.bios.app.model.ExerciseSession
import com.bios.app.model.ExerciseSessionFields
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import com.bios.app.model.SleepStage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        // SyncWorker runs in the background; without this the HC service returns
        // only Bios' own written records (none) instead of Google Fit / Fitbit data.
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
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
    ): List<MetricReading> = coroutineScope {
        val jobs = listOf(
            async { fetchHeartRate(startTime, endTime, sourceId) },
            async { fetchHRV(startTime, endTime, sourceId) },
            async { fetchRestingHR(startTime, endTime, sourceId) },
            async { fetchSpO2(startTime, endTime, sourceId) },
            async { fetchRespiratoryRate(startTime, endTime, sourceId) },
            async { fetchSkinTemp(startTime, endTime, sourceId) },
            async { fetchSleep(startTime, endTime, sourceId) },
            async { fetchSteps(startTime, endTime, sourceId) },
            async { fetchActiveCalories(startTime, endTime, sourceId) }
        )
        jobs.awaitAll().flatten()
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
            val baselineC = record.baseline?.inCelsius
            record.deltas.flatMap { delta ->
                val tMs = delta.time.toEpochMilli()
                val deviation = MetricReading(
                    metricType = MetricType.SKIN_TEMPERATURE_DEVIATION.key,
                    value = delta.delta.inCelsius,
                    timestamp = tMs,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
                // Reconstruct raw absolute temp when the record supplies a baseline.
                // Needed for febrile-range thresholds and menstrual phase detection,
                // which require absolute °C rather than personal-baseline deltas.
                if (baselineC != null) {
                    listOf(
                        deviation,
                        MetricReading(
                            metricType = MetricType.SKIN_TEMPERATURE.key,
                            value = baselineC + delta.delta.inCelsius,
                            timestamp = tMs,
                            sourceId = sourceId,
                            confidence = ConfidenceTier.MEDIUM.level
                        )
                    )
                } else {
                    listOf(deviation)
                }
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
            val stageReadings = session.stages.mapNotNull { stage ->
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

            // Asleep time = session total minus AWAKE stages (matches Oura/Whoop semantics).
            // If the session has no stage breakdown, fall back to the full session length.
            val awakeSec = stageReadings
                .filter { it.value.toInt() == SleepStage.AWAKE.value }
                .sumOf { it.durationSec ?: 0 }
            val sessionSec = java.time.Duration.between(
                session.startTime, session.endTime
            ).seconds.toInt()
            val asleepSec = (sessionSec - awakeSec).coerceAtLeast(0)

            val durationReading = MetricReading(
                metricType = MetricType.SLEEP_DURATION.key,
                value = asleepSec.toDouble(),
                timestamp = session.endTime.toEpochMilli(),
                durationSec = sessionSec,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )

            stageReadings + durationReading
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

    // MARK: - Composite event: exercise sessions

    /**
     * Reads `ExerciseSessionRecord`s in the window and returns each one as
     * a composite [ExerciseSession] — a parent EXERCISE_SESSION MetricReading
     * plus its EventPayloadField rows. Returned separately from
     * [fetchReadings] because the result type is structurally different
     * (parent + payload, not a flat reading list) and the dedupe/quality
     * pipeline doesn't yet know how to keep payload rows in sync with a
     * deduped parent.
     *
     * `avg_hr_bpm` is left null today — enriching from HeartRateRecord in
     * the same window is a follow-up. RPE isn't in Health Connect's record
     * shape, so it stays null here.
     */
    suspend fun fetchExerciseSessions(
        start: Instant, end: Instant, sourceId: String
    ): List<ExerciseSession> {
        val response = client.readRecords(
            ReadRecordsRequest(
                ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.map { record ->
            val startMs = record.startTime.toEpochMilli()
            val endMs = record.endTime.toEpochMilli()
            val durationSec = java.time.Duration.between(
                record.startTime, record.endTime
            ).seconds.toInt()

            val reading = MetricReading(
                id = UUID.randomUUID().toString(),
                metricType = MetricType.EXERCISE_SESSION.key,
                value = 1.0,
                timestamp = startMs,
                durationSec = durationSec,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )

            val payload = listOf(
                EventPayloadField(
                    readingId = reading.id,
                    fieldKey = ExerciseSessionFields.MODALITY,
                    stringValue = mapExerciseTypeToModality(record.exerciseType),
                ),
                EventPayloadField(
                    readingId = reading.id,
                    fieldKey = ExerciseSessionFields.START_UTC,
                    longValue = startMs,
                ),
                EventPayloadField(
                    readingId = reading.id,
                    fieldKey = ExerciseSessionFields.END_UTC,
                    longValue = endMs,
                ),
            )

            ExerciseSession(reading = reading, payload = payload)
        }
    }

    companion object {
        /**
         * Maps a Health Connect `ExerciseSessionRecord.exerciseType` int to
         * Bios's coarse modality bucket. Unknown / unmapped types fall to
         * OTHER — that's the right default for the pattern engine, which
         * cares about aerobic-vs-anaerobic-vs-mixed load buckets rather
         * than HC's full sport taxonomy.
         */
        internal fun mapExerciseTypeToModality(exerciseType: Int): String =
            when (exerciseType) {
                ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
                ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
                ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
                ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
                ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
                ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
                ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
                ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
                ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
                ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> ExerciseModality.CARDIO

                ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
                ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> ExerciseModality.STRENGTH

                ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> ExerciseModality.INTERVAL

                ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
                ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
                ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> ExerciseModality.MOBILITY

                else -> ExerciseModality.OTHER
            }
    }
}
