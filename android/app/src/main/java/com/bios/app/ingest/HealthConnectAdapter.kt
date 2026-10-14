package com.bios.app.ingest

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalFeatureAvailabilityApi
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

/**
 * Bridges Health Connect data into Bios unified MetricReadings.
 */
@OptIn(ExperimentalFeatureAvailabilityApi::class)
class HealthConnectAdapter(private val context: Context) {

    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    /**
     * Owner-readable name of a Health Connect writer: the installed app's
     * label when this app may see it (package visibility hides most apps
     * on Android 11+), else a known health app, else the phone's own name
     * for a platform writer (an origin that is not a package), else the
     * package's last segment.
     */
    fun appLabel(packageName: String): String {
        if (packageName.isBlank()) return "Unknown app"
        runCatching {
            val pm = context.packageManager
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }
        KNOWN_WRITERS.entries.firstOrNull { packageName.startsWith(it.key) }?.let { return it.value }
        if (!packageName.contains('.')) {
            return android.provider.Settings.Global.getString(context.contentResolver, "device_name")
                ?.takeIf { it.isNotBlank() } ?: "This phone"
        }
        return packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    /**
     * Permissions we request: the core set plus whatever optional features
     * this device's Health Connect supports. Skin temperature and background
     * read are unknown to older HC builds (Android 14 platform HC among them):
     * requesting them there can never be granted, and onboarding demanded the
     * full set, so the owner was stuck on the first screen forever.
     */
    val permissions: Set<String> by lazy {
        permissionsFor(skinTempSupported = skinTempSupported, backgroundReadSupported = backgroundReadSupported)
    }

    /** False on HC builds without skin temperature; its read would fail the whole batch. */
    val skinTempSupported: Boolean by lazy { featureAvailable(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) }

    private val backgroundReadSupported: Boolean by lazy {
        featureAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
    }

    private fun featureAvailable(feature: Int): Boolean = runCatching {
        client.features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasAllPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return permissions.all { it in granted }
    }

    // MARK: - Fetch all readings

    /**
     * [originSource] maps a writing app's package name to the Bios source
     * that owns its count records (docs/specs/source-preference.md); the
     * default keeps everything under [sourceId].
     */
    suspend fun fetchReadings(
        startTime: Instant,
        endTime: Instant,
        sourceId: String,
        originSource: suspend (String) -> String = { sourceId },
    ): List<MetricReading> = coroutineScope {
        val jobs = listOf(
            async { HealthConnectReads.heartRate(client, startTime, endTime, sourceId) },
            async { HealthConnectReads.hrv(client, startTime, endTime, sourceId) },
            async { HealthConnectReads.restingHr(client, startTime, endTime, sourceId) },
            async { HealthConnectReads.spo2(client, startTime, endTime, sourceId) },
            async { HealthConnectReads.respiratoryRate(client, startTime, endTime, sourceId) },
            async {
                if (skinTempSupported) HealthConnectReads.skinTemp(client, startTime, endTime, sourceId) else emptyList()
            },
            async { HealthConnectReads.sleep(client, startTime, endTime, sourceId) },
            async { HealthConnectReads.steps(client, startTime, endTime, originSource) },
            async { HealthConnectReads.activeCalories(client, startTime, endTime, originSource) },
            async { HealthConnectReads.vo2Max(client, startTime, endTime, sourceId) },
            async { HealthConnectBodyComposition.fetchAll(client, startTime, endTime, sourceId) },
        )
        jobs.awaitAll().flatten()
    }

    // Per-record reads (1:1 record ⇒ MetricReading, multi-output for HR
    // samples / skin-temp deltas / sleep stages) live in
    // [HealthConnectReads] so this file stays under the 500-line ceiling
    // and each emitted MetricReading carries a stable id derived from the
    // HC record id — see #312.

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
     * Enriches each session with `avg_hr_bpm` when HeartRateRecord samples
     * overlap the session window. Fetches HR samples once for the whole
     * outer window and intersects per-session — sessions are sparse but
     * HR samples can be dense, so a single IPC beats N+1 queries.
     *
     * RPE isn't in Health Connect's record shape, so it stays null here.
     */
    suspend fun fetchExerciseSessions(
        start: Instant, end: Instant, sourceId: String
    ): List<ExerciseSession> {
        val sessionResponse = client.readRecords(
            ReadRecordsRequest(
                ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        if (sessionResponse.records.isEmpty()) return emptyList()

        // One HR fetch for the whole window; per-session filtering is local.
        val hrSamples = fetchHeartRateSamples(start, end)

        return sessionResponse.records.map { record ->
            val startMs = record.startTime.toEpochMilli()
            val endMs = record.endTime.toEpochMilli()
            val durationSec = java.time.Duration.between(
                record.startTime, record.endTime
            ).seconds.toInt()

            val reading = MetricReading(
                id = HealthConnectStableIds.forRecord("exercise-session", sourceId, record.metadata.id),
                metricType = MetricType.EXERCISE_SESSION.key,
                value = 1.0,
                timestamp = startMs,
                durationSec = durationSec,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )

            val payload = mutableListOf(
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

            meanHrInWindow(hrSamples, startMs, endMs)?.let { avgHr ->
                payload += EventPayloadField(
                    readingId = reading.id,
                    fieldKey = ExerciseSessionFields.AVG_HR_BPM,
                    doubleValue = avgHr,
                )
            }

            ExerciseSession(reading = reading, payload = payload)
        }
    }

    /**
     * Flattens HeartRateRecord -> (timestamp ms, bpm) samples for the window.
     * Used as the input to per-session mean-HR computation. Kept private —
     * the canonical streaming HR ingestion is [fetchHeartRate], which writes
     * MetricReading rows; this is a denormalized read for session enrichment.
     */
    private suspend fun fetchHeartRateSamples(
        start: Instant, end: Instant
    ): List<HrSample> {
        val response = client.readRecords(
            ReadRecordsRequest(
                HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.flatMap { record ->
            record.samples.map { sample ->
                HrSample(
                    timestampMs = sample.time.toEpochMilli(),
                    bpm = sample.beatsPerMinute.toDouble(),
                )
            }
        }
    }

    /** Internal time-stamped HR sample for session enrichment. */
    internal data class HrSample(val timestampMs: Long, val bpm: Double)

    companion object {
        /** Health apps whose label the package manager will not give us (package visibility). */
        private val KNOWN_WRITERS = mapOf(
            "com.yf.smart.coros" to "COROS",
            "com.fitbit" to "Fitbit",
            "com.google.android.apps.fitness" to "Google Fit",
            "com.ouraring" to "Oura",
            "com.whoop" to "WHOOP",
            "com.garmin" to "Garmin",
            "fi.polar" to "Polar",
            "com.withings" to "Withings",
            "com.sec.android.app.shealth" to "Samsung Health",
            "com.huami" to "Zepp",
            "com.xiaomi.wearable" to "Mi Fitness",
            "com.huawei.health" to "Huawei Health",
            "nodomain.freeyourgadget.gadgetbridge" to "Gadgetbridge",
        )

        /** Pure so the feature gating is unit-testable without a device. */
        fun permissionsFor(skinTempSupported: Boolean, backgroundReadSupported: Boolean): Set<String> = buildSet {
            add(HealthPermission.getReadPermission(HeartRateRecord::class))
            add(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class))
            add(HealthPermission.getReadPermission(RestingHeartRateRecord::class))
            add(HealthPermission.getReadPermission(OxygenSaturationRecord::class))
            add(HealthPermission.getReadPermission(RespiratoryRateRecord::class))
            if (skinTempSupported) add(HealthPermission.getReadPermission(SkinTemperatureRecord::class))
            add(HealthPermission.getReadPermission(SleepSessionRecord::class))
            add(HealthPermission.getReadPermission(StepsRecord::class))
            add(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
            add(HealthPermission.getReadPermission(ExerciseSessionRecord::class))
            add(HealthPermission.getReadPermission(Vo2MaxRecord::class))
            // SyncWorker runs in the background; without this the HC service returns
            // only Bios' own written records (none) instead of Google Fit / Fitbit data.
            if (backgroundReadSupported) add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
            addAll(HealthConnectBodyComposition.permissions) // body comp lives in a sibling
        }

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

        /**
         * Mean BPM across HR samples that fall in `[startMs, endMs]`,
         * inclusive at both ends. Returns null when no samples land in
         * the window — caller should omit the `avg_hr_bpm` payload field
         * rather than write a misleading zero.
         */
        internal fun meanHrInWindow(
            samples: List<HrSample>, startMs: Long, endMs: Long
        ): Double? {
            val inWindow = samples.filter { it.timestampMs in startMs..endMs }
            if (inWindow.isEmpty()) return null
            return inWindow.sumOf { it.bpm } / inWindow.size
        }
    }
}
