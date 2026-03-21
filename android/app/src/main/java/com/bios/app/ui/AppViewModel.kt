package com.bios.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bios.app.alerts.AlertManager
import com.bios.app.alerts.FollowUpWorker
import com.bios.app.data.BiosDatabase
import com.bios.app.engine.AnomalyDetector
import com.bios.app.engine.BaselineEngine
import com.bios.app.ingest.HealthConnectAdapter
import com.bios.app.ingest.IngestManager
import com.bios.app.model.Anomaly
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import com.bios.app.model.PersonalBaseline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    val db = BiosDatabase.getInstance(application)
    val healthConnect = HealthConnectAdapter(application)
    val ingestManager = IngestManager(healthConnect, db)
    val baselineEngine = BaselineEngine(db)
    val anomalyDetector = AnomalyDetector(db)
    val alertManager = AlertManager(application, db)

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions

    private val _unacknowledgedAlerts = MutableStateFlow<List<Anomaly>>(emptyList())
    val unacknowledgedAlerts: StateFlow<List<Anomaly>> = _unacknowledgedAlerts

    private val _recentAlerts = MutableStateFlow<List<Anomaly>>(emptyList())
    val recentAlerts: StateFlow<List<Anomaly>> = _recentAlerts

    private val _baselines = MutableStateFlow<List<PersonalBaseline>>(emptyList())
    val baselines: StateFlow<List<PersonalBaseline>> = _baselines

    private val _timelineEntries = MutableStateFlow<List<Anomaly>>(emptyList())
    val timelineEntries: StateFlow<List<Anomaly>> = _timelineEntries

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Check permissions without initializing. Returns true if all granted. */
    suspend fun checkPermissions(): Boolean {
        if (!healthConnect.isAvailable) {
            _error.value = "Health Connect is not available on this device."
            return false
        }
        val granted = healthConnect.hasAllPermissions()
        _hasPermissions.value = granted
        return granted
    }

    /** Called after the permission launcher reports success. */
    fun onPermissionsGranted() {
        _hasPermissions.value = true
        initialize()
    }

    fun initialize() {
        viewModelScope.launch {
            try {
                if (!healthConnect.isAvailable) {
                    _error.value = "Health Connect is not available on this device."
                    _isInitialized.value = true
                    return@launch
                }

                ingestManager.setup()

                if (ingestManager.dataAgeDays.value >= BaselineEngine.MINIMUM_DATA_DAYS) {
                    baselineEngine.computeAllBaselines()
                    baselineEngine.computeDailyAggregates()
                    anomalyDetector.runDetection()
                }

                refreshAlerts()
                refreshBaselines()
                _isInitialized.value = true
            } catch (e: Exception) {
                _error.value = e.message
                _isInitialized.value = true
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                ingestManager.syncRecentData()

                if (ingestManager.dataAgeDays.value >= BaselineEngine.MINIMUM_DATA_DAYS) {
                    baselineEngine.computeAllBaselines()
                    baselineEngine.computeDailyAggregates()
                    anomalyDetector.runDetection()
                }

                refreshAlerts()
                refreshBaselines()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun acknowledgeAlert(id: String) {
        viewModelScope.launch {
            alertManager.acknowledge(id)
            refreshAlerts()
        }
    }

    fun saveAlertFeedback(
        anomalyId: String,
        feltSick: Boolean?,
        visitedDoctor: Boolean?,
        diagnosis: String?,
        symptoms: String?,
        notes: String?,
        outcomeAccurate: Boolean?
    ) {
        viewModelScope.launch {
            db.anomalyDao().saveFeedback(
                id = anomalyId,
                feltSick = feltSick,
                visitedDoctor = visitedDoctor,
                diagnosis = diagnosis,
                symptoms = symptoms,
                notes = notes,
                outcomeAccurate = outcomeAccurate
            )
            FollowUpWorker.cancel(getApplication(), anomalyId)
            refreshAlerts()
        }
    }

    suspend fun getLatestReading(metricType: MetricType): MetricReading? {
        return db.metricReadingDao().fetchLatest(metricType.key).firstOrNull()
    }

    suspend fun getBaseline(metricType: MetricType): PersonalBaseline? {
        return db.personalBaselineDao().fetch(metricType.key)
    }

    fun refreshTimeline() {
        viewModelScope.launch {
            _timelineEntries.value = db.anomalyDao().fetchAll()
        }
    }

    private suspend fun refreshAlerts() {
        _unacknowledgedAlerts.value = alertManager.fetchUnacknowledged()
        _recentAlerts.value = alertManager.fetchRecent()
        _timelineEntries.value = db.anomalyDao().fetchAll()
    }

    private suspend fun refreshBaselines() {
        _baselines.value = db.personalBaselineDao().fetchAll()
    }
}
