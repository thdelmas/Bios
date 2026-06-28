package com.bios.app.ui

import android.content.Context
import android.net.Uri
import com.bios.app.labocr.ExtractedReading
import com.bios.app.labocr.LabScanSummary
import com.bios.app.labocr.LabScanner
import com.bios.app.labocr.PanelMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lab-report OCR slice of [AppViewModel] (Phase 10). Holds the pending scan
 * the review screen renders, runs the scan off the main thread, and writes the
 * rows the owner confirms through the same manual-entry path as everything
 * else ([AppViewModelBiomarkers.addManual]). Kept separate so the main
 * view-model stays under the 500-line cap.
 *
 * Nothing is persisted by scanning — only [confirm] writes, and only the rows
 * handed to it. See docs/LAB_OCR_INGESTION.md §§3,6,7.
 */
class AppViewModelLabScan(
    private val biomarkers: AppViewModelBiomarkers,
    private val scope: CoroutineScope,
    private val errorSink: MutableStateFlow<String?>,
) {
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _pending = MutableStateFlow<LabScanSummary?>(null)
    /** The most recent scan awaiting owner review, or null. */
    val pending: StateFlow<LabScanSummary?> = _pending.asStateFlow()

    /** Scan [uri] (PDF or image) and stash the summary for the review screen. */
    fun scan(context: Context, uri: Uri, isPdf: Boolean) {
        if (_scanning.value) return
        _scanning.value = true
        scope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    LabScanner.default(context.applicationContext).scan(context.applicationContext, uri, isPdf)
                }
                _pending.value = summary
            } catch (e: Exception) {
                errorSink.value = "Lab scan failed: ${e.message ?: "unknown error"}"
            } finally {
                _scanning.value = false
            }
        }
    }

    /**
     * Write the owner-confirmed [rows] (edited values included) against the
     * confirmed [panel]. Each row lands through the SELF_REPORTED manual-entry
     * path; the panel's collection date and provenance ride on every row.
     * Clears the pending scan when done.
     */
    fun confirm(rows: List<ExtractedReading>, panel: PanelMetadata) {
        val timestamp = panel.collectionDate
        if (timestamp == null) {
            errorSink.value = "Set the collection date before saving the scan."
            return
        }
        val context = panel.toContext()
        for (row in rows) {
            biomarkers.addManual(row.metricType, row.value, timestamp, context)
        }
        _pending.value = null
    }

    /** Discard the pending scan without writing anything. */
    fun discard() {
        _pending.value = null
    }
}
