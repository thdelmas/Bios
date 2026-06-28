package com.bios.app.labocr

import com.bios.app.model.Specimen
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Pulls the panel-level header a lab prints once and applies to every analyte
 * — collection date, specimen type, lab name — out of the recognised lines
 * (docs LAB_OCR_INGESTION.md §4.1). The values pre-fill the review screen's
 * editable header; none is binding until the owner confirms.
 *
 * [PanelMetadata.collectionDate] is deliberately left null when no date is
 * found: OCR must never silently stamp a lab draw as "today". The review
 * screen forces the owner to set a date before confirm is enabled.
 */
object PanelMetadataExtractor {

    /** dd/mm/yyyy or dd-mm-yy etc. — the dominant ES/CA/EU report format. */
    private val DMY = Regex("\\b(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})\\b")

    /** ISO yyyy-mm-dd. */
    private val ISO = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")

    /** Labels that introduce the *collection* date (preferred over print/report date). */
    private val DATE_LABELS = listOf(
        "presa de la mostra", "data extraccio", "data d obtencio", "data de la mostra",
        "fecha de toma", "fecha de extraccion", "fecha de la muestra", "fecha de obtencion",
        "collected", "collection date", "specimen date", "drawn", "sample date",
    )

    private val LAB_KEYWORDS = listOf(
        "laboratori", "laboratorio", "laboratory", "hospital", "clinica", "clinic",
        "centre", "centro", "synlab", "cerba", "catlab", "labco", "echevarne", "reference lab",
    )

    fun extract(lines: List<OcrLine>): PanelMetadata = PanelMetadata(
        labName = findLabName(lines),
        collectionDate = findCollectionDate(lines),
        specimen = findSpecimen(lines),
    )

    private fun findCollectionDate(lines: List<OcrLine>): Long? {
        // 1) A date on a line that names the collection event wins.
        for (line in lines) {
            val norm = deAccent(line.text)
            if (DATE_LABELS.any { norm.contains(it) }) {
                parseDate(line.text)?.let { return it }
            }
        }
        // 2) Otherwise the first plausible date in the header band.
        return lines.take(HEADER_BAND).firstNotNullOfOrNull { parseDate(it.text) }
    }

    private fun parseDate(text: String): Long? {
        ISO.find(text)?.let { m ->
            return toEpochUtc(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        DMY.find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val year = normaliseYear(m.groupValues[3].toInt())
            return toEpochUtc(year, month, day)
        }
        return null
    }

    /** Two-digit years map into 2000–2099 — lab reports are contemporary. */
    private fun normaliseYear(raw: Int): Int = if (raw < 100) 2000 + raw else raw

    private fun toEpochUtc(year: Int, month: Int, day: Int): Long? = try {
        LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (_: DateTimeException) {
        null // 31/02, month 13, OCR garble — let the owner pick instead.
    }

    private fun findSpecimen(lines: List<OcrLine>): Specimen? {
        for (line in lines.take(HEADER_BAND)) {
            val norm = deAccent(line.text)
            when {
                "serum" in norm || "suero" in norm -> return Specimen.SERUM
                "plasma" in norm -> return Specimen.PLASMA
                "edta" in norm || "whole blood" in norm || "sang total" in norm ||
                    "sangre total" in norm -> return Specimen.WHOLE_BLOOD
            }
        }
        return null
    }

    private fun findLabName(lines: List<OcrLine>): String? {
        val candidate = lines.take(HEADER_BAND).firstOrNull { line ->
            val norm = deAccent(line.text)
            LAB_KEYWORDS.any { norm.contains(it) }
        } ?: return null
        return candidate.text.trim().take(MAX_LAB_NAME).ifBlank { null }
    }

    private fun deAccent(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()

    /** How many leading lines count as the report header for name/specimen. */
    private const val HEADER_BAND = 20

    /** Defensive cap so an OCR-merged header line can't become a giant lab name. */
    private const val MAX_LAB_NAME = 80
}
