package com.bios.app.ui.ppg

import android.app.Application
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.bios.app.data.BiosDatabase
import com.bios.app.engine.CaptureQuality
import com.bios.app.ingest.CameraPpgAdapter
import com.bios.app.ingest.CaptureResult
import com.bios.app.model.DataSource
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State machine for a camera-PPG HRV snapshot. Owns the [CameraPpgAdapter]
 * and persists accepted readings via the Room DAO. The screen observes
 * [uiState] + [quality] and calls [requestCapture] / [bindCamera] / [reset]
 * in response to user input and PreviewView lifecycle.
 *
 * Capture is two-phase so the UI can show the live lens view:
 *   1. [requestCapture] transitions to [PpgUiState.Capturing]; the screen
 *      then renders its `PreviewView`.
 *   2. Once the PreviewView's `Preview.SurfaceProvider` is available, the
 *      screen calls [bindCamera], which actually binds CameraX and runs
 *      the analyzer for the countdown.
 */
class PpgCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BiosDatabase.getInstance(application)
    private val adapter = CameraPpgAdapter(application)

    private val _uiState = MutableStateFlow<PpgUiState>(PpgUiState.Idle)
    val uiState: StateFlow<PpgUiState> = _uiState.asStateFlow()

    private val _quality = MutableStateFlow(CaptureQuality.WAITING)
    val quality: StateFlow<CaptureQuality> = _quality.asStateFlow()

    private var captureJob: Job? = null

    /** User tapped Start. Transition to [PpgUiState.Capturing] without
     *  binding the camera yet — the screen will call [bindCamera] once its
     *  PreviewView is composed and has a surface provider. */
    fun requestCapture(durationSec: Int = DEFAULT_DURATION_SEC) {
        if (_uiState.value is PpgUiState.Capturing) return
        _quality.value = CaptureQuality.WAITING
        _uiState.value = PpgUiState.Capturing(durationSec)
    }

    /** Called by the screen once its PreviewView has a [surfaceProvider].
     *  Idempotent — extra calls during the same Capturing state are no-ops. */
    fun bindCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        val state = _uiState.value as? PpgUiState.Capturing ?: return
        if (captureJob?.isActive == true) return

        captureJob = viewModelScope.launch {
            val sourceId = getOrCreateCameraSource()
            val result = adapter.capture(
                lifecycleOwner = lifecycleOwner,
                durationSec = state.totalSec,
                sourceId = sourceId,
                surfaceProvider = surfaceProvider,
                qualityFlow = _quality
            )

            if (result.accepted && result.readings.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    db.metricReadingDao().insertAll(result.readings)
                }
            }

            _uiState.value = PpgUiState.Complete(result)
            _quality.value = CaptureQuality.WAITING
        }
    }

    /** Return to the idle state so the user can start another capture. */
    fun reset() {
        if (_uiState.value !is PpgUiState.Capturing) {
            _uiState.value = PpgUiState.Idle
            _quality.value = CaptureQuality.WAITING
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
