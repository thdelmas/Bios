package com.bios.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bios.app.alerts.AlertManager
import com.bios.app.alerts.FollowUpWorker
import com.bios.app.data.BiosDatabase
import com.bios.app.engine.AnomalyDetector
import com.bios.app.engine.BaselineEngine
import com.bios.app.engine.TFLiteAnomalyModel
import com.bios.app.ingest.DirectSensorAdapter
import com.bios.app.ingest.GadgetbridgeAdapter
import com.bios.app.ingest.HealthConnectAdapter
import com.bios.app.ingest.IngestManager
import com.bios.app.ingest.OuraApiAdapter
import com.bios.app.ingest.OuraTokenStore
import com.bios.app.ingest.PhoneSensorAdapter
import com.bios.app.model.ActionItem
import com.bios.app.model.Anomaly
import com.bios.app.model.HealthEvent
import com.bios.app.model.HealthEventStatus
import com.bios.app.model.HealthEventType
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import com.bios.app.model.PersonalBaseline
import com.bios.app.ui.diagnostics.DiagnosticResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AppViewModel(application: Application) : AndroidViewModel(application) {

    val db = BiosDatabase.getInstance(application)
    val healthConnect = HealthConnectAdapter(application)
    val ouraTokenStore = OuraTokenStore(application)
    val ouraAdapter = OuraApiAdapter(ouraTokenStore)
    val phoneSensorAdapter = PhoneSensorAdapter(application)
    val gadgetbridgeAdapter = GadgetbridgeAdapter(application)
    val directSensorAdapter = DirectSensorAdapter(application)
    val ingestManager = IngestManager(
        healthConnect, db, ouraAdapter, phoneSensorAdapter,
        gadgetbridgeAdapter = gadgetbridgeAdapter,
        directSensorAdapter = directSensorAdapter
    )
    val baselineEngine = BaselineEngine(db)
    val mlModel = TFLiteAnomalyModel.load(application)
    val anomalyDetector = AnomalyDetector(db, mlModel)
    val alertManager = AlertManager(application, db)

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    private val _initStatus = MutableStateFlow("")
    val initStatus: StateFlow<String> = _initStatus

    private val _initProgress = MutableStateFlow(0f)
    val initProgress: StateFlow<Float> = _initProgress

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

    private val _healthEvents = MutableStateFlow<List<HealthEvent>>(emptyList())
    val healthEvents: StateFlow<List<HealthEvent>> = _healthEvents

    private val _pendingActionItems = MutableStateFlow<List<ActionItem>>(emptyList())
    val pendingActionItems: StateFlow<List<ActionItem>> = _pendingActionItems

    private val _diagnosticResults = MutableStateFlow<List<DiagnosticResult>>(emptyList())
    val diagnosticResults: StateFlow<List<DiagnosticResult>> = _diagnosticResults

    private val _trackedMetricTypes = MutableStateFlow<Set<MetricType>>(emptySet())
    val trackedMetricTypes: StateFlow<Set<MetricType>> = _trackedMetricTypes

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() {
        _error.value = null
    }

    /** Check permissions without initializing. Returns true if all granted or if alternate sources exist. */
    suspend fun checkPermissions(): Boolean {
        if (!healthConnect.isAvailable) {
            // Health Connect not available — check if we have alternative data sources
            val hasAlternatives = gadgetbridgeAdapter.isAvailable ||
                directSensorAdapter.hasAnySensor ||
                ouraTokenStore.hasToken()
            if (hasAlternatives) {
                _hasPermissions.value = true
                return true
            }
            _error.value = "No health data sources available. Install Health Connect or Gadgetbridge."
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
                _initProgress.value = 0f
                _initStatus.value = "Checking data sources..."

                if (!healthConnect.isAvailable &&
                    !gadgetbridgeAdapter.isAvailable &&
                    !directSensorAdapter.hasAnySensor &&
                    !ouraTokenStore.hasToken()
                ) {
                    _error.value = "No health data sources available."
                    return@launch
                }

                _initStatus.value = "Syncing health data..."
                _initProgress.value = 0.1f
                val syncJob = viewModelScope.launch {
                    launch {
                        ingestManager.syncProgress.collect { syncPct ->
                            _initProgress.value = 0.1f + syncPct * 0.3f
                        }
                    }
                    launch {
                        ingestManager.syncStatus.collect { status ->
                            if (status.isNotEmpty()) _initStatus.value = status
                        }
                    }
                }
                try {
                    withTimeout(SYNC_TIMEOUT_MS) {
                        ingestManager.setup()
                    }
                } catch (_: TimeoutCancellationException) {
                    _error.value = "Data sync timed out. The app will work with available data."
                } finally {
                    syncJob.cancel()
                }
                _initProgress.value = 0.4f

                if (ingestManager.dataAgeDays.value >= BaselineEngine.MINIMUM_DATA_DAYS) {
                    _initStatus.value = "Computing baselines..."
                    _initProgress.value = 0.5f
                    baselineEngine.computeAllBaselines()
                    baselineEngine.computeDailyAggregates()

                    _initStatus.value = "Detecting patterns..."
                    _initProgress.value = 0.7f
                    anomalyDetector.runDetection()
                }

                _initStatus.value = "Preparing dashboard..."
                _initProgress.value = 0.85f
                refreshAlerts()
                refreshBaselines()
                refreshHealthEvents()
                refreshActionItems()
                _initProgress.value = 1f
            } catch (e: Throwable) {
                _error.value = e.message ?: "Initialization failed"
            } finally {
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

    // MARK: - Health Events

    fun createHealthEvent(
        type: HealthEventType,
        title: String,
        description: String?,
        anomalyId: String? = null,
        parentEventId: String? = null,
        initialActionItems: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            val event = HealthEvent(
                type = type.name,
                title = title,
                description = description,
                anomalyId = anomalyId,
                parentEventId = parentEventId
            )
            db.healthEventDao().insert(event)
            for (desc in initialActionItems) {
                db.actionItemDao().insert(
                    ActionItem(healthEventId = event.id, description = desc)
                )
            }
            refreshHealthEvents()
            if (initialActionItems.isNotEmpty()) refreshActionItems()
        }
    }

    fun updateHealthEventStatus(eventId: String, status: HealthEventStatus) {
        viewModelScope.launch {
            db.healthEventDao().updateStatus(eventId, status.name)
            refreshHealthEvents()
        }
    }

    fun createActionItem(eventId: String, description: String, dueAt: Long? = null) {
        viewModelScope.launch {
            db.actionItemDao().insert(
                ActionItem(healthEventId = eventId, description = description, dueAt = dueAt)
            )
            refreshActionItems()
        }
    }

    fun toggleActionItem(itemId: String, completed: Boolean) {
        viewModelScope.launch {
            db.actionItemDao().setCompleted(
                itemId, completed,
                if (completed) System.currentTimeMillis() else null
            )
            refreshActionItems()
        }
    }

    fun deleteActionItem(itemId: String) {
        viewModelScope.launch {
            db.actionItemDao().delete(itemId)
            refreshActionItems()
        }
    }

    suspend fun getActionItemsForEvent(eventId: String): List<ActionItem> {
        return db.actionItemDao().fetchByEventId(eventId)
    }

    suspend fun getChildEvents(parentId: String): List<HealthEvent> {
        return db.healthEventDao().fetchByParentId(parentId)
    }

    fun refreshTimeline() {
        viewModelScope.launch {
            _timelineEntries.value = db.anomalyDao().fetchAll()
            refreshHealthEvents()
        }
    }

    private suspend fun refreshHealthEvents() {
        _healthEvents.value = db.healthEventDao().fetchAll()
    }

    private suspend fun refreshActionItems() {
        _pendingActionItems.value = db.actionItemDao().fetchPending()
    }

    private suspend fun refreshAlerts() {
        _unacknowledgedAlerts.value = alertManager.fetchUnacknowledged()
        _recentAlerts.value = alertManager.fetchRecent()
        _timelineEntries.value = db.anomalyDao().fetchAll()
    }

    private suspend fun refreshBaselines() {
        val allBaselines = db.personalBaselineDao().fetchAll()
        _baselines.value = allBaselines
        _trackedMetricTypes.value = allBaselines
            .mapNotNull { MetricType.fromKey(it.metricType) }
            .toSet()
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            try {
                _diagnosticResults.value = anomalyDetector.scoreAllPatterns()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    companion object {
        private const val SYNC_TIMEOUT_MS = 120_000L // 2 minutes
    }
}
