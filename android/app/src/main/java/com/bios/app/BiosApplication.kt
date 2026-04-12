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
import com.bios.app.sync.p2p.IrohNode
import com.bios.app.sync.p2p.P2PDiscovery
import com.bios.app.sync.p2p.P2PSyncWorker
import com.bios.app.sync.p2p.WillowSyncAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BiosApplication : Application() {

    lateinit var letheCompat: LetheCompat
        private set

    lateinit var irohNode: IrohNode
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

        // Initialize Iroh P2P node (non-blocking — starts in background)
        irohNode = IrohNode(this)
        if (irohNode.isAvailable) {
            applicationScope.launch(Dispatchers.IO) {
                irohNode.start()
            }
            // Schedule periodic P2P sync via Iroh/Willow
            P2PSyncWorker.enqueuePeriodicSync(this)
        }

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

        // Schedule community contributions (will no-op if Private tier)
        ContributionWorker.enqueueNextContribution(this)
    }

    override fun onTerminate() {
        gadgetbridgeReceiver?.let { unregisterReceiver(it) }
        irohNode.stop()
        healthApiServer?.stop()
        letheCompat.unregisterWipeReceivers(this)
        super.onTerminate()
    }
}
