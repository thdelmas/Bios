package com.bios.app.labocr

import android.graphics.Bitmap

/**
 * On-device OCR seam. The whole extraction pipeline depends only on this
 * interface, so the concrete engine (Tesseract today, ML Kit or another
 * tomorrow) is swappable without touching alias resolution, the line
 * extractor, or the review UI — the hedge the design called for
 * (docs LAB_OCR_INGESTION.md §2).
 *
 * Implementations MUST be fully on-device and free of Google Play Services so
 * Bios keeps working on a degoogled build (principle 6). No byte of a lab
 * report leaves the device.
 */
interface LabOcrEngine {

    /**
     * True when the engine can actually run — e.g. its language data is
     * installed. When false the scanner reports a clear, actionable file
     * error instead of silently returning zero readings.
     */
    fun isAvailable(): Boolean

    /**
     * Recognise text lines from one rasterised page, in reading order.
     * [page] is the 0-based source page index, carried onto each [OcrLine].
     * Returns an empty list on a blank/garbled page; throws only on a hard
     * engine failure (the scanner converts that into a file error).
     */
    suspend fun recognise(bitmap: Bitmap, page: Int): List<OcrLine>

    /** Release native resources. Safe to call more than once. */
    fun close()
}
