package com.bios.app.ingest

import com.bios.app.model.EcgClassification
import com.bios.app.model.EcgStrip
import com.bios.app.model.LeadPlacement
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Parses Apple Health export XML for ECG records (#188, audit gap §2.8).
 *
 * Apple Health exports ECG data as `<ElectrocardiogramRecord>` elements
 * within an `<HealthData>` document. Each record carries metadata
 * attributes (start/end date, sampling frequency, classification, source
 * device) and a child `<VoltageMeasurement>` element with a long string
 * of sample values.
 *
 * Format reference: an iOS Health app export produces an `export.xml`
 * file at the archive root; ECG strips appear as siblings of `<Record>`
 * elements. The Apple format has shifted across iOS versions — this
 * parser is permissive about attribute names and matches case-
 * insensitively where the spec was unstable.
 *
 * Sample format: comma- or whitespace-separated floats in microvolts.
 * Converted to a packed `int16` little-endian blob for storage;
 * `voltageScale = 0.001` recovers millivolts at render time.
 *
 * **Out of scope for this importer:**
 *  - PDF parsing (KardiaMobile export). PDF text extraction is heavy
 *    and the owner-side workaround — re-exporting as Apple Health XML
 *    or manual entry — is the cleaner path. TODO follow-up.
 *  - Apple's own classification post-iOS-16 split (the "atrial
 *    fibrillation with high heart rate" sub-classification). Falls
 *    through to ATRIAL_FIBRILLATION here; the original string is
 *    preserved verbatim in [EcgStrip.note] if the caller wires it.
 */
object AppleHealthEcgImporter {

    /**
     * Streams an Apple Health export XML and returns every
     * ElectrocardiogramRecord found, as ready-to-insert [EcgStrip]
     * rows. Uses SAX so memory stays bounded — Health exports can
     * exceed 100 MB and we don't want to DOM-parse them.
     */
    fun parse(input: InputStream): List<EcgStrip> {
        val handler = EcgSaxHandler()
        val parser = SAXParserFactory.newInstance().apply {
            // Disable XXE — defence in depth even though we trust the
            // owner-supplied file. Health exports never reference
            // external entities, and disabling closes the door on
            // a hostile crafted XML.
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newSAXParser()
        parser.parse(input, handler)
        return handler.strips
    }

    /**
     * Maps Apple's `HKElectrocardiogramClassification` string to Bios's
     * coarse enum. Apple has added classifications across iOS versions
     * (high-HR AFib, low-HR AFib, sinus rhythm, inconclusive low/high
     * HR, inconclusive poor recording, unrecognized). We collapse the
     * AFib variants and inconclusive variants to two buckets; "other"
     * catches everything else so we never silently drop data.
     */
    internal fun mapClassification(raw: String?): EcgClassification? {
        val v = raw?.trim()?.lowercase() ?: return null
        return when {
            v.isEmpty() -> null
            v.contains("sinusrhythm") || v == "sinus rhythm" -> EcgClassification.SINUS_RHYTHM
            v.contains("atrialfibrillation") || v.contains("atrial fibrillation") || v.contains("afib") ->
                EcgClassification.ATRIAL_FIBRILLATION
            v.contains("inconclusive") || v.contains("unclassified") || v.contains("unrecognized") ->
                EcgClassification.INCONCLUSIVE
            else -> EcgClassification.OTHER
        }
    }

    /**
     * Parses "512.07 Hz" → 512. Apple writes the unit suffix; some
     * exports omit it. Falls back to 512 (Apple Watch default) when
     * unparseable rather than dropping the strip — the renderer can
     * compensate at display time.
     */
    internal fun parseSamplingRate(raw: String?): Int {
        if (raw.isNullOrBlank()) return 512
        val numeric = raw.trim()
            .substringBefore(' ')
            .toDoubleOrNull()
            ?: return 512
        return numeric.toInt().coerceAtLeast(1)
    }

    /**
     * Apple ISO-8601 timestamps look like "2023-09-12 14:30:00 -0700"
     * (space between date and time, space before a `±HHMM` offset
     * without colon). `ISO_OFFSET_DATE_TIME` requires `±HH:MM`, so we
     * insert the colon before parsing.
     */
    internal fun parseAppleTimestamp(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .replaceFirst(' ', 'T')
            .replace(" ", "")
        // Apple emits "-0700" (HHMM, no colon); ISO_OFFSET_DATE_TIME
        // wants "-07:00". Inject the colon at the offset boundary.
        // Handles both `Z` (already valid) and `±HHMM` shapes.
        val normalized = if (cleaned.length >= 5) {
            val tail = cleaned.takeLast(5)
            if (tail.matches(Regex("[+-]\\d{4}"))) {
                cleaned.dropLast(5) + tail.substring(0, 3) + ":" + tail.substring(3)
            } else {
                cleaned
            }
        } else {
            cleaned
        }
        return runCatching {
            OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant().toEpochMilli()
        }.getOrNull()
    }

    /**
     * Splits the voltage measurement text on commas, whitespace, or
     * semicolons (Apple has used all three across versions). Returns a
     * compact int16 little-endian byte buffer. Apple emits microvolts
     * as floats; multiply by 1000 to recover an integer µV count and
     * clamp into the int16 range. A 30 s strip with peaks <3 mV stays
     * well inside ±32 768 µV. Caller stores 0.001 as the voltageScale
     * on the strip so renderer math is `mv = sample * 0.001`.
     */
    internal fun encodeVoltageSamples(raw: String): ByteArray {
        if (raw.isBlank()) return ByteArray(0)
        val tokens = raw.split(',', ';', ' ', '\n', '\r', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val buf = ByteBuffer.allocate(tokens.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (tok in tokens) {
            val mv = tok.toDoubleOrNull() ?: continue
            val asShort = (mv * 1000.0).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buf.putShort(asShort.toShort())
        }
        // The buffer may have unused tail bytes when some tokens failed
        // to parse; trim to what we actually wrote.
        val written = buf.position()
        val out = ByteArray(written)
        buf.flip()
        buf.get(out, 0, written)
        return out
    }

    /**
     * SAX handler — accumulates fields while inside an
     * `<ElectrocardiogramRecord>`, emits an [EcgStrip] on the close tag.
     */
    private class EcgSaxHandler : DefaultHandler() {

        val strips = mutableListOf<EcgStrip>()

        private var inRecord = false
        private var inVoltage = false
        private val voltageBuffer = StringBuilder()
        private var startTime: Long? = null
        private var samplingRateHz: Int = 512
        private var classification: EcgClassification? = null
        private var sourceName: String = "Apple Watch"
        private var durationSeconds: Int = 30

        override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
            when (qName) {
                "ElectrocardiogramRecord", "Record" -> {
                    val type = attrs?.getValue("type") ?: ""
                    val isEcg = qName == "ElectrocardiogramRecord" ||
                        type.contains("ECG", ignoreCase = true) ||
                        type.contains("Electrocardiogram", ignoreCase = true)
                    if (!isEcg) return
                    inRecord = true
                    startTime = parseAppleTimestamp(attrs?.getValue("startDate"))
                    val endTime = parseAppleTimestamp(attrs?.getValue("endDate"))
                    if (startTime != null && endTime != null) {
                        durationSeconds = ((endTime - startTime!!) / 1000L).toInt()
                            .coerceAtLeast(1)
                    }
                    samplingRateHz = parseSamplingRate(attrs?.getValue("samplingFrequency"))
                    classification = mapClassification(attrs?.getValue("classification"))
                    sourceName = attrs?.getValue("sourceName")?.takeIf { it.isNotBlank() }
                        ?: "Apple Watch"
                }
                "VoltageMeasurement", "VoltageMeasurements" -> if (inRecord) {
                    inVoltage = true
                    voltageBuffer.clear()
                }
            }
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            if (inVoltage && ch != null) {
                voltageBuffer.appendRange(ch, start, start + length)
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (qName) {
                "VoltageMeasurement", "VoltageMeasurements" -> if (inRecord) inVoltage = false
                "ElectrocardiogramRecord", "Record" -> if (inRecord) {
                    val bytes = encodeVoltageSamples(voltageBuffer.toString())
                    val ts = startTime
                    if (ts != null && bytes.isNotEmpty()) {
                        strips += EcgStrip(
                            timestamp = ts,
                            durationSeconds = durationSeconds,
                            samplingRateHz = samplingRateHz,
                            leadPlacement = LeadPlacement.LEAD_I,
                            samples = bytes,
                            voltageScale = 0.001,  // µV → mV at render
                            voltageOffset = 0.0,
                            sampleEncoding = "int16_le",
                            classification = classification,
                            sourceVendor = sourceName,
                        )
                    }
                    inRecord = false
                    voltageBuffer.clear()
                    startTime = null
                    classification = null
                    sourceName = "Apple Watch"
                    durationSeconds = 30
                    samplingRateHz = 512
                }
            }
        }
    }
}
