package com.bios.app.ingest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bios.app.data.BiosDatabase
import com.bios.app.engine.SeizureDetectionPrefs
import com.bios.app.engine.SeizureDetector
import com.bios.app.engine.SeizureEventFactory
import com.bios.app.model.DataSource
import com.bios.app.model.ReadingKind
import com.bios.app.model.SourceType
import com.bios.contracts.MetricType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that streams the phone accelerometer through
 * [SeizureDetector] looking for convulsive-pattern candidates while the
 * owner is opted in (#269 Cut 3b).
 *
 * Off by default. The owner enables / disables via
 * `Settings → Seizure detection` ([SeizureDetectionPrefs]). On enable
 * the service is started; on disable it is stopped.
 *
 * ## Two gates already established
 *
 * - **Privacy gate** ([SeizureDetectionPrefs], #269 Cut 3a): the service
 *   refuses to start when the opt-in is off; if the toggle flips off
 *   while running it self-stops on the next sampling cycle.
 * - **Safety gate** (#287, [com.bios.app.alerts.SignalRule.excludePayloadFieldValue]
 *   on the seizure patterns): wearable-inferred SEIZURE_EVENT rows
 *   never feed the URGENT / ADVISORY paths regardless of this service's
 *   output. Detector candidates land in the timeline only.
 *
 * ## Lifecycle
 *
 * - **Foreground notification.** Posted in `onCreate`; honest copy
 *   ("Bios is watching for convulsive-pattern movement"). API 34+ uses
 *   `FOREGROUND_SERVICE_TYPE_HEALTH`.
 * - **Wake lock.** A `PARTIAL_WAKE_LOCK` keeps the CPU available so the
 *   sampling cadence doesn't drift under Doze. Released on `onDestroy`.
 * - **Accelerometer listener.** Registered at `SENSOR_DELAY_GAME`
 *   (~50 Hz) — comfortably above the 16 Hz Nyquist floor for the 2–8 Hz
 *   clonic-phase band [SeizureDetector] looks for.
 * - **Analysis loop.** Every [ANALYSIS_INTERVAL_MS] (= 5 s) the loop
 *   snapshots the buffer, fetches recent HR readings, and invokes
 *   [SeizureDetector.analyse]. Detections are persisted via
 *   [SeizureEventFactory] (LOW confidence, `detection_source =
 *   "wearable_inferred"`).
 *
 * ## What's intentionally out of scope this cut
 *
 * - Timeline UI differentiation between `wearable_inferred` and
 *   `owner_logged` rows — separate Cut 3c.
 * - Per-detection battery-budget telemetry — separate follow-up.
 * - Charge-only operation. Seizures can happen any time; we accept the
 *   continuous wake-lock cost when the owner opts in.
 */
class SeizureDetectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var analysisJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var buffer: SeizureDetectionBuffer? = null

    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            buffer?.append(
                event.values[0], event.values[1], event.values[2],
                System.currentTimeMillis()
            )
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded(this)
        startForegroundWithType()
        acquireWakeLock()
        Log.i(TAG, "service onCreate — foreground notification posted")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SeizureDetectionPrefs.isEnabled(this)) {
            Log.i(TAG, "started while opt-in is off; stopping self")
            stopSelf()
            return START_NOT_STICKY
        }
        if (analysisJob == null) {
            val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sm == null || sensor == null) {
                Log.i(TAG, "no accelerometer; stopping self")
                stopSelf()
                return START_NOT_STICKY
            }
            sensorManager = sm
            accelerometer = sensor
            buffer = SeizureDetectionBuffer(SAMPLE_RATE_HZ, BUFFER_CAPACITY_SEC)
            sm.registerListener(accelListener, sensor, SAMPLE_PERIOD_US)
            analysisJob = launchAnalysisLoop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager?.unregisterListener(accelListener)
        analysisJob?.cancel()
        analysisJob = null
        releaseWakeLock()
        scope.cancel()
        sensorManager = null
        accelerometer = null
        buffer = null
        Log.i(TAG, "service onDestroy — wake lock released")
        super.onDestroy()
    }

    private fun launchAnalysisLoop(): Job = scope.launch {
        val db = BiosDatabase.getInstance(applicationContext)
        val readingDao = db.metricReadingDao()
        val sourceDao = db.dataSourceDao()
        val payloadDao = db.eventPayloadFieldDao()
        while (isActive) {
            delay(ANALYSIS_INTERVAL_MS)
            if (!SeizureDetectionPrefs.isEnabled(applicationContext)) {
                Log.i(TAG, "opt-in flipped off during run; stopping self")
                stopSelf()
                return@launch
            }
            try {
                val snapshot = buffer?.snapshot().orEmpty()
                val bufferStart = buffer?.startTimestampMs ?: continue
                if (snapshot.size < SAMPLE_RATE_HZ.toInt() * SeizureDetector.MIN_SUSTAINED_SEC) {
                    continue
                }
                val analysisEndMs = bufferStart +
                    ((snapshot.size / SAMPLE_RATE_HZ) * 1_000).toLong()
                val hrSeries = fetchHrSeries(readingDao, bufferStart, analysisEndMs)
                val detections = SeizureDetector.analyse(
                    accelNormMagnitudes = snapshot,
                    startTimestampMs = bufferStart,
                    sampleRateHz = SAMPLE_RATE_HZ,
                    hrSeries = hrSeries,
                )
                if (detections.isEmpty()) continue
                val sourceId = ensureSource(sourceDao)
                for (d in detections) {
                    val event = SeizureEventFactory.fromWearableDetection(d, sourceId)
                    readingDao.insert(event.reading)
                    payloadDao.insertAll(event.payload)
                    Log.i(
                        TAG,
                        "wearable_inferred seizure event written id=${event.reading.id} " +
                            "durationSec=${event.reading.durationSec}"
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "analysis iteration failed: ${t.message}", t)
            }
        }
    }

    private suspend fun fetchHrSeries(
        readingDao: com.bios.app.data.dao.MetricReadingDao,
        bufferStartMs: Long,
        analysisEndMs: Long,
    ): List<SeizureDetector.HrSample> {
        // Include the pre-event baseline window the corroborator needs.
        val baselineStart = bufferStartMs - SeizureDetector.PRE_EVENT_BASELINE_SEC * 1_000L
        return readingDao
            .fetch(MetricType.HEART_RATE.key, baselineStart, analysisEndMs)
            .map { SeizureDetector.HrSample(timestampMs = it.timestamp, bpm = it.value) }
    }

    private suspend fun ensureSource(dao: com.bios.app.data.dao.DataSourceDao): String {
        // Reuse the PHONE_SENSOR_DERIVED type but distinguish on deviceName
        // so provenance reads cleanly alongside the sleep-inference source.
        val existing = dao.findByTypeAndDeviceName(
            SourceType.PHONE_SENSOR_DERIVED.key,
            SOURCE_DEVICE_NAME,
        )
        if (existing != null) return existing.id
        val source = DataSource(
            sourceType = SourceType.PHONE_SENSOR_DERIVED.key,
            sensorType = "phone_seizure",
            deviceName = SOURCE_DEVICE_NAME,
            readingKind = ReadingKind.DERIVED.name,
        )
        dao.insert(source)
        return source.id
    }

    private fun startForegroundWithType() {
        val notification = buildNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
        }
        lock.acquire(MAX_WAKE_LOCK_MS)
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val TAG = "SeizureDetectionService"

        const val CHANNEL_ID = "bios_seizure_detection"
        private const val NOTIFICATION_ID = 0x5E12 // arbitrary, stable

        private const val WAKE_LOCK_TAG = "Bios:SeizureDetection"

        /** ~50 Hz. SENSOR_DELAY_GAME — comfortably above the 16 Hz
         *  Nyquist floor for [SeizureDetector]'s 2–8 Hz band, with
         *  headroom for OS-side sample-rate jitter under Doze. */
        internal const val SAMPLE_RATE_HZ: Double = 50.0
        private const val SAMPLE_PERIOD_US: Int = 20_000

        /** Rolling buffer holds 60 s — comfortably longer than
         *  [SeizureDetector.MIN_SUSTAINED_SEC] (10 s) plus headroom for
         *  the corroborator's pre-event baseline read window. */
        internal const val BUFFER_CAPACITY_SEC: Int = 60

        /** Analysis cadence. 5 s gives each detection a fair chance to
         *  fire within the buffer window without burning CPU per
         *  sample. */
        internal const val ANALYSIS_INTERVAL_MS: Long = 5_000L

        /** Wake-lock ceiling. Re-acquired on each app launch; the cap
         *  protects against a runaway service. 24 h covers a full day
         *  of opt-in operation; longer than that needs a re-start. */
        private const val MAX_WAKE_LOCK_MS: Long = 24L * 60L * 60L * 1000L

        /** Stable deviceName for the DataSource row that owns
         *  wearable-inferred SEIZURE_EVENT rows. */
        const val SOURCE_DEVICE_NAME = "Wearable seizure detector"

        /** Start the service from app init / the Settings toggle. No-op
         *  when the opt-in is off. */
        fun startIfOptedIn(context: Context) {
            if (!SeizureDetectionPrefs.isEnabled(context)) return
            val intent = Intent(context, SeizureDetectionService::class.java)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        /** Stop the service. Safe to call when already stopped. */
        fun stop(context: Context) {
            val intent = Intent(context, SeizureDetectionService::class.java)
            context.applicationContext.stopService(intent)
        }

        fun createChannelIfNeeded(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
                ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Seizure detection",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description =
                        "Posted while Bios is watching for convulsive-pattern movement. " +
                            "Owner opt-in only."
                    setShowBadge(false)
                }
            )
        }

        internal fun buildNotification(context: Context): Notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Bios is watching for convulsive-pattern movement")
                .setContentText("Detector candidates land in the timeline for your review.")
                .setSmallIcon(context.applicationInfo.icon)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
    }
}
