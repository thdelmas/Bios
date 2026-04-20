package com.bios.app.ui.ppg

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.bios.app.data.BiosDatabase
import com.bios.app.ingest.CameraPpgAdapter
import com.bios.app.ingest.CaptureResult
import com.bios.app.model.DataSource
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State machine for a camera-PPG HRV snapshot. Owns the [CameraPpgAdapter]
 * and persists accepted readings via the Room DAO. The screen observes
 * [uiState] and calls [startCapture] / [reset] in response to user input.
 */
class PpgCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BiosDatabase.getInstance(application)
    private val adapter = CameraPpgAdapter(application)

    private val _uiState = MutableStateFlow<PpgUiState>(PpgUiState.Idle)
    val uiState: StateFlow<PpgUiState> = _uiState.asStateFlow()

    /**
     * Launch a capture for [durationSec] seconds. The screen must hold CAMERA
     * permission and pass its [LifecycleOwner] so CameraX can bind.
     */
    fun startCapture(lifecycleOwner: LifecycleOwner, durationSec: Int = DEFAULT_DURATION_SEC) {
        if (_uiState.value is PpgUiState.Capturing) return

        viewModelScope.launch {
            _uiState.value = PpgUiState.Capturing(durationSec)

            val sourceId = getOrCreateCameraSource()
            val result = adapter.capture(lifecycleOwner, durationSec, sourceId)

            if (result.accepted && result.readings.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    db.metricReadingDao().insertAll(result.readings)
                }
            }

            _uiState.value = PpgUiState.Complete(result)
        }
    }

    /** Return to the idle state so the user can start another capture. */
    fun reset() {
        if (_uiState.value !is PpgUiState.Capturing) {
            _uiState.value = PpgUiState.Idle
        }
    }

    private suspend fun getOrCreateCameraSource(): String = withContext(Dispatchers.IO) {
        val existing = db.dataSourceDao().findByType(SourceType.CAMERA_PPG.key)
        if (existing != null) return@withContext existing.id

        val source = DataSource(
            sourceType = SourceType.CAMERA_PPG.key,
            deviceName = "Phone Camera",
            deviceModel = android.os.Build.MODEL,
            sensorType = SensorType.PPG_CAMERA.name
        )
        db.dataSourceDao().insert(source)
        source.id
    }

    companion object {
        const val DEFAULT_DURATION_SEC = 60
    }
}

/** UI states the capture screen renders. */
sealed interface PpgUiState {
    /** Ready to start a new capture. */
    data object Idle : PpgUiState

    /** Capture in progress; [totalSec] lets the UI render a countdown. */
    data class Capturing(val totalSec: Int) : PpgUiState

    /** Capture done (accepted or rejected). */
    data class Complete(val result: CaptureResult) : PpgUiState
}
