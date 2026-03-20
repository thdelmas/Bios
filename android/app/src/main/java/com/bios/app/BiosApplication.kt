package com.bios.app

import android.app.Application
import com.bios.app.ingest.SyncWorker
import com.bios.app.privacy.ContributionWorker

class BiosApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Schedule periodic health data sync
        SyncWorker.enqueuePeriodicSync(this)

        // Schedule community contributions (will no-op if Private tier)
        ContributionWorker.enqueueNextContribution(this)
    }
}
