package com.bios.app.labocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates one lab-report scan: source URI → page bitmaps → on-device OCR
 * → panel metadata + per-line extraction → a [LabScanSummary] for the review
 * screen. Pure funnel, no persistence — nothing is written here. The owner
 * confirms rows on the review screen; only then do they reach
 * [com.bios.app.data.BiomarkerEntryRepo] (docs LAB_OCR_INGESTION.md §3).
 *
 * The source [Uri] is threaded straight through onto every reading as
 * provenance ([PanelMetadata.sourceUri]); image picks keep their persistable
 * content URI (released by `DataDestroyer`), camera captures live under
 * `cacheDir/labocr` (wiped by `DataDestroyer`).
 */
class LabScanner(private val engine: LabOcrEngine) {

    /**
     * @param uri the report (PDF or image)
     * @param isPdf whether [uri] points at a PDF (from its MIME type at the call site)
     */
    suspend fun scan(context: Context, uri: Uri, isPdf: Boolean): LabScanSummary =
        withContext(Dispatchers.IO) {
            if (!engine.isAvailable()) {
                return@withContext LabScanSummary.fileError(
                    "OCR language data isn't installed on this build — add it or enter values by hand."
                )
            }

            val pages = try {
                loadPages(context, uri, isPdf)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load report pages", e)
                return@withContext LabScanSummary.fileError("Could not open the selected report.")
            }
            if (pages.bitmaps.isEmpty()) {
                return@withContext LabScanSummary.fileError("No readable pages in the report.")
            }

            val lines = mutableListOf<OcrLine>()
            try {
                pages.bitmaps.forEachIndexed { index, bitmap ->
                    lines += engine.recognise(bitmap, index)
                }
            } catch (e: Exception) {
                Log.w(TAG, "OCR failed", e)
                return@withContext LabScanSummary.fileError("Couldn't read text from the report.")
            } finally {
                pages.bitmaps.forEach { it.recycle() }
            }

            if (lines.isEmpty()) {
                return@withContext LabScanSummary.fileError("No text found in the report.")
            }

            val panel = PanelMetadataExtractor.extract(lines).copy(sourceUri = uri.toString())
            val readings = mutableListOf<ExtractedReading>()
            val skipped = mutableListOf<SkippedLine>()
            for (line in lines) {
                when (val outcome = LabLineExtractor.extract(line.text)) {
                    is LabLineExtractor.LineOutcome.Reading -> readings += outcome.reading
                    is LabLineExtractor.LineOutcome.Skipped -> skipped += outcome.skipped
                    LabLineExtractor.LineOutcome.Ignored -> Unit // structural line
                }
            }
            if (pages.droppedPages > 0) {
                skipped += SkippedLine(
                    "(${pages.droppedPages} page(s) beyond the ${PdfPageRasterizer.MAX_PAGES}-page limit)",
                    "Not scanned — re-scan those pages separately",
                )
            }
            LabScanSummary(readings = dedupe(readings), skipped = skipped, panel = panel)
        }

    private data class Pages(val bitmaps: List<Bitmap>, val droppedPages: Int)

    private fun loadPages(context: Context, uri: Uri, isPdf: Boolean): Pages {
        if (isPdf) {
            val result = PdfPageRasterizer.rasterise(context, uri)
            return Pages(result.pages, result.droppedPages)
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: error("Could not decode image")
        return Pages(listOf(bitmap), droppedPages = 0)
    }

    /**
     * A multi-column report can list the same analyte twice (e.g. a value and
     * its delta). Keep the first, highest-confidence reading per metric so the
     * owner doesn't see duplicate rows. Order is otherwise preserved.
     */
    private fun dedupe(readings: List<ExtractedReading>): List<ExtractedReading> {
        val byMetric = LinkedHashMap<String, ExtractedReading>()
        for (r in readings) {
            val key = r.metricType.key
            val existing = byMetric[key]
            if (existing == null || (existing.confidence == Confidence.LOW && r.confidence == Confidence.HIGH)) {
                byMetric[key] = r
            }
        }
        return byMetric.values.toList()
    }

    companion object {
        private const val TAG = "LabScanner"

        /** Build the default (Tesseract) scanner for [context]. */
        fun default(context: Context): LabScanner =
            LabScanner(TesseractLabOcrEngine.create(context))
    }
}
