package com.bios.app.ui

import com.bios.app.alerts.AlertManager
import com.bios.app.data.BiosDatabase
import com.bios.app.engine.AnomalyDetector
import com.bios.app.engine.BaselineEngine
import com.bios.app.engine.DetectionLatencyTracker
import com.bios.app.engine.TFLiteAnomalyModel
import com.bios.app.ingest.ApiTokenStore
import com.bios.app.ingest.BleAirQualityAdapter
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

/**
 * The engines, adapters, and stores that [AppViewModel] orchestrates.
 *
 * Construction used to live in AppViewModel's field initializers, which made
 * the ViewModel impossible to unit-test: it instantiated a real database, ML
 * model, and every ingest adapter straight off the Application. Modelling the
 * graph as an interface lets production build it once ([ProductionAppDependencies])
 * while a test constructs an AppViewModel with fakes (e.g. a relaxed mock of
 * this interface). See the maintainability audit, Finding 2.
 */
interface AppDependencies {
    val db: BiosDatabase
    val healthConnect: HealthConnectAdapter
    val ouraTokenStore: OuraTokenStore
    val ouraAdapter: OuraApiAdapter
    val apiTokenStore: ApiTokenStore
    val withingsAdapter: WithingsApiAdapter
    val whoopAdapter: WhoopApiAdapter
    val garminAdapter: GarminApiAdapter
    val polarAdapter: PolarApiAdapter
    val phoneSensorAdapter: PhoneSensorAdapter
    val gadgetbridgeAdapter: GadgetbridgeAdapter
    val directSensorAdapter: DirectSensorAdapter
    val bleAirQualityAdapter: BleAirQualityAdapter
    val latencyTracker: DetectionLatencyTracker
    val ingestManager: IngestManager
    val baselineEngine: BaselineEngine
    val mlModel: TFLiteAnomalyModel?
    val anomalyDetector: AnomalyDetector
    val alertManager: AlertManager
}
