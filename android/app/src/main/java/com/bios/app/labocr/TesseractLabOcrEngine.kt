package com.bios.app.labocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [LabOcrEngine] backed by Tesseract via tess-two (`com.rmtheis:tess-two`,
 * Apache-2.0). Fully FOSS and free of Google Play Services — the degoogled-
 * purist choice for Phase 10 (docs LAB_OCR_INGESTION.md §2).
 *
 * **Trained data is operator-provisioned, never committed.** Tesseract needs a
 * `<lang>.traineddata` model per language. Those are multi-MB binaries that do
 * not belong in a public source repo, so they are not bundled here. At runtime
 * [provisionFromAssets] copies any `.traineddata` files shipped in app
 * assets into private storage; if none are present, [isAvailable] is false and
 * the scanner reports "OCR language data not installed" rather than failing
 * silently. See `app/src/main/assets/tessdata/README` for how to add them.
 */
class TesseractLabOcrEngine private constructor(
    private val dataParentDir: File,
    private val language: String,
) : LabOcrEngine {

    private var api: TessBaseAPI? = null

    override fun isAvailable(): Boolean = language.isNotEmpty()

    override suspend fun recognise(bitmap: Bitmap, page: Int): List<OcrLine> =
        withContext(Dispatchers.Default) {
            if (!isAvailable()) return@withContext emptyList()
            val engine = obtainApi() ?: return@withContext emptyList()
            val text = try {
                engine.setImage(bitmap)
                engine.getUTF8Text().orEmpty()
            } catch (e: Exception) {
                Log.w(TAG, "Tesseract recognise failed on page $page", e)
                ""
            } finally {
                engine.clear()
            }
            // Line-level split keeps reading order without depending on the
            // result-iterator bbox API; `top` is just the running line index.
            text.split('\n')
                .mapIndexedNotNull { index, line ->
                    line.trim().ifBlank { null }?.let { OcrLine(it, page, index) }
                }
        }

    private fun obtainApi(): TessBaseAPI? {
        api?.let { return it }
        return try {
            TessBaseAPI().also {
                // init wants the directory that *contains* the `tessdata/` folder.
                if (!it.init(dataParentDir.absolutePath, language)) {
                    Log.w(TAG, "TessBaseAPI.init failed for '$language'")
                    it.end()
                    return null
                }
                api = it
            }
        } catch (e: Exception) {
            Log.w(TAG, "TessBaseAPI init threw", e)
            null
        }
    }

    override fun close() {
        try {
            api?.end()
        } catch (_: Exception) {
            // already ended / never inited
        }
        api = null
    }

    companion object {
        private const val TAG = "TesseractLabOcr"

        /** Languages we ship aliases for; provisioned if their data is present. */
        private val SUPPORTED = listOf("cat", "spa", "eng")

        /**
         * Build an engine for [context], copying any bundled trained data out
         * of assets into private storage and selecting the `+`-joined language
         * string from whatever resolved. Returns an engine whose [isAvailable]
         * is false when no trained data could be provisioned.
         */
        fun create(context: Context): TesseractLabOcrEngine {
            val parent = File(context.filesDir, OCR_DIR)
            val installed = provisionFromAssets(context, File(parent, "tessdata"))
            // tess-two language string, e.g. "cat+spa+eng".
            val language = SUPPORTED.filter { it in installed }.joinToString("+")
            return TesseractLabOcrEngine(parent, language)
        }

        /** filesDir subdirectory holding the `tessdata/` Tesseract expects. */
        const val OCR_DIR = "labocr"

        /**
         * Copy `tessdata/<lang>.traineddata` from assets into [tessdataDir],
         * returning the set of language codes now available there. Idempotent —
         * skips files already copied. Absent assets simply yield an empty set.
         */
        private fun provisionFromAssets(context: Context, tessdataDir: File): Set<String> {
            tessdataDir.mkdirs()
            return listTessdataAssets(context)
                .filter { it.endsWith(".traineddata") }
                .filter { ensureCopied(context, tessdataDir, it) }
                .map { it.removeSuffix(".traineddata") }
                .toSet()
        }

        private fun listTessdataAssets(context: Context): List<String> = try {
            context.assets.list("tessdata").orEmpty().toList()
        } catch (e: IOException) {
            Log.w(TAG, "Could not list tessdata assets", e)
            emptyList()
        }

        /** Copy one asset into [tessdataDir] if absent; true when it's present afterwards. */
        private fun ensureCopied(context: Context, tessdataDir: File, name: String): Boolean {
            val dest = File(tessdataDir, name)
            if (dest.exists() && dest.length() > 0L) return true
            return try {
                context.assets.open("tessdata/$name").use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                true
            } catch (e: IOException) {
                Log.w(TAG, "Failed to provision $name", e)
                false
            }
        }
    }
}
