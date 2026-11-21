package com.bios.app

import android.app.Application
import com.bios.app.alerts.DailyDigestWorker
import com.bios.app.data.BiosDatabase
import com.bios.app.ingest.GadgetbridgeReceiver
import com.bios.app.ingest.PowerConnectionReceiver
import com.bios.app.ingest.SyncWorker
import com.bios.app.platform.HealthApiServer
import com.bios.app.platform.LetheCompat
import com.bios.app.platform.PlatformDetector
import com.bios.app.sync.p2p.IrohNode
import com.bios.app.sync.p2p.LocalNetworkTransport
import com.bios.app.sync.p2p.P2PTransport
import com.bios.app.sync.p2p.P2PSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BiosApplication : Application() {

    lateinit var letheCompat: LetheCompat
        private set

    lateinit var p2pTransport: P2PTransport
        private set

    private var healthApiServer: HealthApiServer? = null
    private var gadgetbridgeReceiver: GadgetbridgeReceiver? = null
    private var powerConnectionReceiver: PowerConnectionReceiver? = null
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Detect platform and initialize LETHE integration (no-op on stock Android)
        val platform = PlatformDetector.detect(this)
        letheCompat = LetheCompat.create(this)
        letheCompat.registerWipeReceivers(this)

        // Start local health API for LETHE agent (LETHE only)
        if (PlatformDetector.isLethe(this)) {
            val db = BiosDatabase.getInstance(this)
            healthApiServer = HealthApiServer(db, applicationScope, this)
            healthApiServer?.start()
        }

        // P2P transport (Iroh if available, else local network). It opens a
        // listener and announces itself on the local network, so it starts
        // only when the owner has turned device-to-device sync on.
        val irohNode = IrohNode(this)
        p2pTransport = if (irohNode.isAvailable) irohNode else LocalNetworkTransport(this)
        if (P2PSyncWorker.isEnabled(this)) {
            applicationScope.launch(Dispatchers.IO) {
                p2pTransport.start()
            }
        }
        // Schedule periodic P2P sync
        P2PSyncWorker.enqueuePeriodicSync(this)

        // Register Gadgetbridge real-time data receiver (push model).
        // RECEIVER_EXPORTED because broadcasts come from the Gadgetbridge app.
        gadgetbridgeReceiver = GadgetbridgeReceiver()
        registerReceiver(gadgetbridgeReceiver, GadgetbridgeReceiver.intentFilter(),
            RECEIVER_EXPORTED)

        // Schedule periodic ingestion from the connected sources
        SyncWorker.enqueuePeriodicSync(this)

        // Schedule periodic phone-sleep sampling + morning inference.
        // Idempotent via WorkManager's unique-work policy; only does
        // anything on devices with an accelerometer (the worker
        // self-skips otherwise).
        com.bios.app.ingest.PhoneSleepWorker.enqueuePeriodicWork(this)

        // Arm the wake-up motion tracker so SIGNIFICANT_MOTION /
        // STATIONARY_DETECT listeners are live before the periodic
        // worker fires (#243 Cut 2). The short-lived adapter the worker
        // builds per firing wouldn't otherwise give the sensors time to
        // observe a state transition; attaching here means the sensor-
        // hub is already tracking when the first sample is taken.
        com.bios.app.ingest.WakeUpMotionTracker.attach(this)

        // Register the charge-edge receiver for the new foreground
        // collection service (#241). ACTION_POWER_CONNECTED /
        // _DISCONNECTED are not on the API 26+ implicit-broadcast
        // allowlist, so the receiver must be registered at runtime —
        // a manifest declaration would never fire. RECEIVER_NOT_EXPORTED
        // is correct: only the OS sends these broadcasts.
        powerConnectionReceiver = PowerConnectionReceiver().also {
            registerReceiver(
                it,
                android.content.IntentFilter().apply {
                    addAction(android.content.Intent.ACTION_POWER_CONNECTED)
                    addAction(android.content.Intent.ACTION_POWER_DISCONNECTED)
                },
                RECEIVER_NOT_EXPORTED,
            )
        }

        // Schedule daily digest notification (8 AM, user can disable in settings)
        if (DailyDigestWorker.isEnabled(this)) {
            DailyDigestWorker.schedule(this)
        }

        // Schedule the nightly sleep-regularity derivation worker (#135
        // deliverable 2 — closes the gap between SleepRegularityCalculator
        // existing and rows actually landing on the bus). Pull-side
        // metric only — no alert. The worker is a no-op when fewer than
        // the minimum nights are available, so it's safe to enqueue
        // unconditionally.
        com.bios.app.engine.SleepRegularityWorker.schedule(this)

        // Periodic work of removed features is cancelled on existing installs.
        val workManager = androidx.work.WorkManager.getInstance(this)
        REMOVED_PERIODIC_WORK.forEach { workManager.cancelUniqueWork(it) }
    }

    override fun onTerminate() {
        gadgetbridgeReceiver?.let { unregisterReceiver(it) }
        powerConnectionReceiver?.let { unregisterReceiver(it) }
        p2pTransport.stop()
        healthApiServer?.stop()
        letheCompat.unregisterWipeReceivers(this)
        super.onTerminate()
    }

    private companion object {
        val REMOVED_PERIODIC_WORK = listOf(
            "bios_paediatric_band",
            "bios_perioperative_state_transition",
            "bios_chronic_migraine_screening",
            "bios_moh_screening",
            "bios_community_contribution",
        )
    }
}
