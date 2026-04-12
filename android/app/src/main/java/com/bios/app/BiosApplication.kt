package com.bios.app

import android.app.Application
import com.bios.app.alerts.DailyDigestWorker
import com.bios.app.data.BiosDatabase
import com.bios.app.ingest.GadgetbridgeReceiver
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

        // Schedule daily digest notification (8 AM, user can disable in settings)
        if (DailyDigestWorker.isEnabled(this)) {
            DailyDigestWorker.schedule(this)
        }

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
        p2pTransport.stop()
        healthApiServer?.stop()
        letheCompat.unregisterWipeReceivers(this)
        super.onTerminate()
    }
}
