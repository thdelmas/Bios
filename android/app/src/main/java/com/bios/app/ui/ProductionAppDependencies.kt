package com.bios.app.ui

import android.app.Application
import com.bios.app.alerts.AlertManager
import com.bios.app.config.EnvironmentalContextProvider
import com.bios.app.data.BiosDatabase
import com.bios.app.data.ReproductiveDatabase
import com.bios.app.engine.AnomalyDetector
import com.bios.app.engine.BaselineEngine
import com.bios.app.engine.DetectionLatencyTracker
import com.bios.app.engine.TFLiteAnomalyModel
import com.bios.app.ingest.ApiTokenStore
import com.bios.app.ingest.BleAirQualityAdapter
import com.bios.app.ingest.BlePairedDeviceStore
import com.bios.app.ingest.DirectSensorAdapter
import com.bios.app.ingest.GadgetbridgeAdapter
import com.bios.app.ingest.GarminApiAdapter
import com.bios.app.ingest.HealthConnectAdapter
import com.bios.app.ingest.IngestManager
import com.bios.app.ingest.OuraApiAdapter
import com.bios.app.ingest.OuraTokenStore
import com.bios.app.ingest.PhoneSensorAdapter
import com.bios.app.ingest.PolarApiAdapter
import com.bios.app.ingest.WhoopApiAdapter
import com.bios.app.ingest.WithingsApiAdapter
import com.bios.app.physiology.OwnerConditionStore
import com.bios.app.physiology.PhysiologyStateStore

/**
 * The production [AppDependencies] graph, built once from the [application].
 *
 * This is the construction block that used to sit in AppViewModel's field
 * initializers, moved verbatim so the wiring is unchanged — only its home
 * changed. [AppViewModelFactory] builds one of these for the real app.
 */
class ProductionAppDependencies(application: Application) : AppDependencies {
    override val db = BiosDatabase.getInstance(application)
    override val healthConnect = HealthConnectAdapter(application)
    override val ouraTokenStore = OuraTokenStore(application)
    override val ouraAdapter = OuraApiAdapter(ouraTokenStore)
    override val apiTokenStore = ApiTokenStore(application)
    override val withingsAdapter = WithingsApiAdapter(apiTokenStore)
    override val whoopAdapter = WhoopApiAdapter(apiTokenStore)
    override val garminAdapter = GarminApiAdapter(apiTokenStore)
    override val polarAdapter = PolarApiAdapter(apiTokenStore)
    override val phoneSensorAdapter = PhoneSensorAdapter(application)
    override val gadgetbridgeAdapter = GadgetbridgeAdapter(application)
    override val directSensorAdapter = DirectSensorAdapter(application)
    override val bleAirQualityAdapter = BleAirQualityAdapter(
        application, db.metricReadingDao(), BlePairedDeviceStore(application)
    )
    override val latencyTracker = DetectionLatencyTracker()
    override val ingestManager = IngestManager(
        healthConnect, db, ouraAdapter, phoneSensorAdapter,
        gadgetbridgeAdapter, directSensorAdapter, withingsAdapter, whoopAdapter,
        garminAdapter, polarAdapter, bleAirQualityAdapter, latencyTracker
    )

    private val reproductiveReadingDao = ReproductiveDatabase.readingDaoOrNull(application)
    override val baselineEngine = BaselineEngine(db, latencyTracker, reproductiveReadingDao)
    override val mlModel = TFLiteAnomalyModel.load(application)

    private val physiologyStateStore = PhysiologyStateStore(application)
    override val anomalyDetector = AnomalyDetector(
        db, mlModel, latencyTracker, reproductiveReadingDao,
        physiologyState = physiologyStateStore.current(),
        ownerConditions = OwnerConditionStore(application).current(),
        drugClass = physiologyStateStore.drugClass(),
        environmentalContext = EnvironmentalContextProvider(application).current(),
    )
    override val alertManager = AlertManager(application, db, latencyTracker)
}
