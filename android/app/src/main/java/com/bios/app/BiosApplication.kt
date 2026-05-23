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
import com.bios.app.privacy.ContributionWorker
import com.bios.app.push.PushRegistrationManager
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
            healthApiServer = HealthApiServer(db, applicationScope)
            healthApiServer?.start()
        }

        // Initialize P2P transport (Iroh if available, else local network)
        val irohNode = IrohNode(this)
        p2pTransport = if (irohNode.isAvailable) irohNode else LocalNetworkTransport(this)
        applicationScope.launch(Dispatchers.IO) {
            p2pTransport.start()
        }
        // Schedule periodic P2P sync
        P2PSyncWorker.enqueuePeriodicSync(this)

        // Register Gadgetbridge real-time data receiver (push model).
        // RECEIVER_EXPORTED because broadcasts come from the Gadgetbridge app.
        gadgetbridgeReceiver = GadgetbridgeReceiver()
        registerReceiver(gadgetbridgeReceiver, GadgetbridgeReceiver.intentFilter(),
            RECEIVER_EXPORTED)

        // Schedule periodic health data sync (HTTP/IPFS)
        SyncWorker.enqueuePeriodicSync(this)

        // Schedule periodic phone-sleep sampling + morning inference.
        // Idempotent via WorkManager's unique-work policy; only does
        // anything on devices with an accelerometer (the worker
        // self-skips otherwise).
        com.bios.app.ingest.PhoneSleepWorker.enqueuePeriodicWork(this)

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

        // Schedule daily paediatric-band recompute (#198). Idempotent and
        // a no-op when no birth date is on file — safe to enqueue on every
        // launch.
        com.bios.app.physiology.PaediatricBandWorker.schedule(this)

        // Re-register UnifiedPush if previously enabled (idempotent — just
        // confirms the registration with the distributor). Does NOT auto-enable;
        // only fires if the owner previously opted in via Settings.
        if (PushRegistrationManager.isEnabled(this)) {
            PushRegistrationManager.register(this)
        }

        // Schedule community contributions (will no-op if Private tier)
        ContributionWorker.enqueueNextContribution(this)
    }

    override fun onTerminate() {
        gadgetbridgeReceiver?.let { unregisterReceiver(it) }
        powerConnectionReceiver?.let { unregisterReceiver(it) }
        p2pTransport.stop()
        healthApiServer?.stop()
        letheCompat.unregisterWipeReceivers(this)
        super.onTerminate()
    }
}
