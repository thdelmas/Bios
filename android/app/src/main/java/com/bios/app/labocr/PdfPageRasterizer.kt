package com.bios.app.labocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri

/**
 * Rasterises a PDF lab report to one [Bitmap] per page using the framework
 * [PdfRenderer] (API 21+, zero dependency, no Google) so the OCR engine can
 * read it (docs LAB_OCR_INGESTION.md §2). Pages render at a fixed DPI tuned
 * for printed-table legibility; oversized reports are capped and the caller
 * is told how many pages were dropped (never a silent truncation).
 */
object PdfPageRasterizer {

    /** Target render resolution — high enough for small printed analyte rows. */
    private const val TARGET_DPI = 200
    private const val POINTS_PER_INCH = 72f

    /** Hard page cap for a single report; anything beyond is reported, not read. */
    const val MAX_PAGES = 12

    data class Result(val pages: List<Bitmap>, val droppedPages: Int)

    /**
     * Render up to [MAX_PAGES] pages of the PDF at [uri]. Throws on an
     * unreadable file (the scanner converts that into a file error).
     */
    fun rasterise(context: Context, uri: Uri): Result {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Could not open PDF")
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val total = renderer.pageCount
                val take = minOf(total, MAX_PAGES)
                val bitmaps = ArrayList<Bitmap>(take)
                for (i in 0 until take) {
                    renderer.openPage(i).use { page ->
                        val scale = TARGET_DPI / POINTS_PER_INCH
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        // PDF pages render with a transparent background; OCR
                        // wants black-on-white, so paint white underneath first.
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps += bitmap
                    }
                }
                return Result(bitmaps, droppedPages = total - take)
            }
        }
    }
}
