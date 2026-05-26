package com.bios.app.ingest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bios.app.data.BiosDatabase
import com.bios.app.engine.BaselinedActivityThreshold
import com.bios.app.model.PhoneSleepSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Charge-triggered foreground service that collects phone-sleep samples
 * continuously while the device is plugged in and inside quiet-hours
 * (#241 Cut 1).
 *
 * Replaces — eventually — the WorkManager periodic worker
 * [PhoneSleepWorker], which on Doze-aggressive OEMs (Samsung, Xiaomi,
 * OnePlus, Oppo) fires every 30–90 min instead of the requested 15 min
 * and never accumulates enough samples for the inference's 4 h
 * minimum-window floor. The worker stays in place this cut as a safety
 * net; the receiver-triggered service is the new primary path.
 *
 * ## Lifecycle
 *
 * - **Start.** [PowerConnectionReceiver] fires on
 *   `ACTION_POWER_CONNECTED`, checks [PhoneSleepQuietHours], and calls
 *   `ContextCompat.startForegroundService(intent)` when both conditions
 *   pass.
 * - **Foreground notification.** Posted in `onCreate` with copy that
 *   honestly explains what is happening ("Bios is collecting sleep
 *   signals while charging"). On API 34+ the service type is
 *   `FOREGROUND_SERVICE_TYPE_HEALTH`.
 * - **Wake lock.** A `PARTIAL_WAKE_LOCK` keeps the CPU available so the
 *   sampling cadence doesn't drift under Doze. Released on `onDestroy`.
 * - **Sampling loop.** [SAMPLE_INTERVAL_MS] cadence, every iteration
 *   calls [PhoneSleepAdapter.sample] and persists one row to
 *   `phone_sleep_samples`. Inference itself is left to the existing
 *   `PhoneSleepWorker` morning trigger (or the dashboard "Infer now"
 *   path); this service is collection-only.
 * - **Stop.** [PowerConnectionReceiver] fires on
 *   `ACTION_POWER_DISCONNECTED` and calls `stopService`.
 *   Self-protective: if the service is started outside quiet-hours
 *   (e.g. due to a stale broadcast), `onStartCommand` immediately
 *   stops itself.
 *
 * ## Out of scope for this cut
 *
 * - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` onboarding ask (UI work).
 * - Removing / downgrading `PhoneSleepWorker`. Both paths coexist; the
 *   worker's inserts and the service's inserts both land in
 *   `phone_sleep_samples` and the inference is order-agnostic.
 * - 1-minute bucket aggregation. This cut samples at the same cadence
 *   as the worker (one row per sample); the bucketing optimisation
 *   lands when we measure whether the row volume is a problem.
 */
class PhoneSleepCollectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var samplingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded(this)
        startForegroundWithType()
        acquireWakeLock()
        Log.i(TAG, "service onCreate — foreground notification posted")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Self-protective: a stale broadcast or a manual `am startservice`
        // outside quiet-hours must not turn into an all-day collection
        // session. The receiver already gates, but we don't trust that
        // alone — the service double-checks.
        if (!PhoneSleepQuietHours.isInQuietHoursNow()) {
            Log.i(TAG, "service started outside quiet-hours; stopping self")
            stopSelf()
            return START_NOT_STICKY
        }
        if (samplingJob == null) samplingJob = launchSamplingLoop()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        samplingJob?.cancel()
        samplingJob = null
        releaseWakeLock()
        scope.cancel()
        Log.i(TAG, "service onDestroy — wake lock released")
        super.onDestroy()
    }

    private fun launchSamplingLoop(): Job = scope.launch {
        val adapter = PhoneSleepAdapter(applicationContext)
        if (!adapter.isAvailable) {
            Log.i(TAG, "no accelerometer; sampling loop exiting")
            stopSelf()
            return@launch
        }
        val sampleDao = BiosDatabase.getInstance(applicationContext).phoneSleepSampleDao()
        while (isActive) {
            try {
                val sample = adapter.sample()
                sampleDao.insert(
                    PhoneSleepSample(
                        timestamp = sample.timestamp,
                        screenOff = sample.screenOff,
                        charging = sample.charging,
                        ambientLightLux = sample.ambientLightLux,
                        accelMagnitudeVar = sample.accelMagnitudeVar,
                        stepDelta = sample.stepDelta,
                        dndEnabled = sample.dndEnabled,
                        pairedBluetoothConnected = sample.pairedBluetoothConnected,
                        significantMotionFired = sample.significantMotionFired,
                        stationary = sample.stationary,
                    )
                )
                // #244 Cut 2: feed the variance sample into the per-owner
                // baseline. The service only runs during quiet hours (the
                // PhoneSleepQuietHours gate runs in onStartCommand), so
                // every sample here qualifies as a quiet-hour observation.
                sample.accelMagnitudeVar?.let {
                    BaselinedActivityThreshold.recordSample(
                        applicationContext, it, sample.timestamp,
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "sample iteration failed: ${t.message}", t)
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private fun startForegroundWithType() {
        val notification = buildNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+ requires a service-type declaration on every
            // startForeground call. HEALTH is the documented category
            // for sleep / vitals collection.
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
        // Cap the lock so a stuck service can't drain the battery
        // indefinitely; ACTION_POWER_DISCONNECTED normally releases it
        // well before this.
        lock.acquire(MAX_WAKE_LOCK_MS)
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val TAG = "PhoneSleepCollectionService"

        /** Notification channel id; created lazily on first service start. */
        const val CHANNEL_ID = "bios_phone_sleep_collection"

        /** Notification id. Stable so the OS keeps replacing it on restart. */
        private const val NOTIFICATION_ID = 0x515 // arbitrary, stable

        /** Wake-lock tag prefix per AOSP convention `<app>:<purpose>`. */
        private const val WAKE_LOCK_TAG = "Bios:PhoneSleepCollection"

        /**
         * Sampling cadence. One row every 60 s gives ~480 samples across
         * an 8 h window — well above the inference's MIN_SAMPLES floor
         * even when Doze drops a few — and matches the C4DMH/Sleep
         * reference implementation cadence.
         */
        const val SAMPLE_INTERVAL_MS: Long = 60_000L

        /**
         * Hard ceiling on the wake-lock hold so a stuck service can't
         * drain the battery overnight. Bigger than the longest
         * plausible quiet-hours session so the cap is only hit in a
         * pathology. */
        private const val MAX_WAKE_LOCK_MS: Long = 14L * 60L * 60L * 1000L

        /**
         * Idempotent channel creation. The first service start posts
         * the foreground notification; on API 26+ the channel must
         * exist or the notification is silently dropped.
         */
        fun createChannelIfNeeded(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
                ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Sleep signal collection",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description =
                        "Posted while Bios is collecting sleep signals from the phone " +
                            "while it is charging overnight."
                    setShowBadge(false)
                }
            )
        }

        internal fun buildNotification(context: Context): Notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Bios is collecting sleep signals")
                .setContentText("Active while the phone is charging during quiet hours.")
                .setSmallIcon(context.applicationInfo.icon)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
    }
}
