package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The vocabulary gate of docs/specs/instrument-only.md: no condition name
 * and no word of medical purpose in text Bios writes for the owner to read.
 *
 * Scanned: every string of `res/values*`, and every string literal of the
 * packages that hold owner-facing text, plus any file that posts a
 * notification. Legitimate uses are in [VocabularyGateAllowlist].
 */
class VocabularyGateTest {

    private data class Hit(val path: String, val line: Int, val term: String, val text: String) {
        override fun toString() = "$path:$line  '$term'  in \"${text.trim().take(MAX_SHOWN)}\""
    }

    private val main = File("src/main")

    private fun resourceHits(): List<Hit> {
        val dirs = File(main, "res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        assertTrue("string resources found under ${main.absolutePath}", dirs.isNotEmpty())
        return dirs.sortedBy { it.name }.flatMap { dir ->
            // "values-es-rMX" -> "es"; the default folder is English.
            val language = dir.name.removePrefix("values").removePrefix("-").substringBefore("-").ifEmpty { "en" }
            dir.listFiles { f -> f.name.endsWith(".xml") }.orEmpty().sortedBy { it.name }.flatMap { file ->
                strings(file).flatMap { text ->
                    AlertContentPolicy.medicalVocabularyIn(text, language)
                        .map { Hit(file.relativeTo(main).path, 0, it, text) }
                }
            }
        }
    }

    private fun strings(file: File): List<String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val doc = factory.newDocumentBuilder().parse(file)
        return listOf("string", "item").flatMap { tag ->
            val nodes = doc.getElementsByTagName(tag)
            (0 until nodes.length).map { nodes.item(it).textContent.orEmpty() }
        }
    }

    private fun scannedSources(): List<File> {
        val root = File(main, SOURCE_ROOT)
        val all = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("sources found under ${root.absolutePath}", all.size > MIN_SOURCES)
        return all.filter { file ->
            val relative = file.relativeTo(root).path
            relative != POLICY_FILE &&
                (SCANNED_PACKAGES.any { relative.startsWith("$it/") } || NOTIFICATION_MARKS.any { it in file.readText() })
        }.sortedBy { it.path }
    }

    private fun sourceHits(): List<Hit> = scannedSources().flatMap { file ->
        KotlinStringLiterals.of(file.readText())
            .filterNot { isKey(it.text) }
            .flatMap { literal ->
                AlertContentPolicy.medicalVocabularyIn(literal.text)
                    .map { Hit(file.relativeTo(main).path, literal.line, it, literal.text) }
            }
    }

    private fun allowed(hit: Hit, entry: VocabularyGateAllowlist.Entry) =
        entry.path == hit.path && entry.term == hit.term && hit.text.contains(entry.text)

    @Test
    fun `no medical vocabulary in the text Bios shows, outside the allowlist`() {
        val hits = resourceHits() + sourceHits()
        val unexcused = hits.filterNot { hit -> VocabularyGateAllowlist.entries.any { allowed(hit, it) } }
        assertTrue(
            "Medical vocabulary in owner-facing text (docs/specs/instrument-only.md). Reword it, or, for an " +
                "owner's own record or a quoted source, add an entry with its reason to VocabularyGateAllowlist:\n" +
                unexcused.joinToString("\n"),
            unexcused.isEmpty(),
        )
    }

    @Test
    fun `every allowlist entry still excuses something and gives a reason`() {
        val hits = resourceHits() + sourceHits()
        val stale = VocabularyGateAllowlist.entries.filter { entry -> hits.none { allowed(it, entry) } }
        assertTrue("Allowlist entries that match nothing:\n" + stale.joinToString("\n"), stale.isEmpty())
        for (entry in VocabularyGateAllowlist.entries) {
            assertTrue("reason for ${entry.path} '${entry.term}'", entry.reason.length > MIN_REASON)
            assertTrue("${entry.term} is a listed word", entry.term in AlertContentPolicy.MEDICAL_VOCABULARY)
        }
    }

    @Test
    fun `the scan covers the owner-facing packages and the notification code`() {
        val scanned = scannedSources().map { it.relativeTo(File(main, SOURCE_ROOT)).path }
        for (expected in listOf(
            "ui/home/HomeScreen.kt", "alerts/AlertManager.kt", "export/PdfReportExporter.kt",
            "platform/HealthApiServer.kt", "engine/AnomalyDetector.kt", "model/Enums.kt",
            "provider/CompanionAccessNotifier.kt", "ingest/PhoneSleepCollectionService.kt",
        )) {
            assertTrue("$expected is scanned", expected in scanned)
        }
    }

    @Test
    fun `condition names and medical-purpose words are caught at the start of a word`() {
        val caught = mapOf(
            "Possible infection onset" to "infection",
            "Signs consistent with atrial fibrillation" to "fibrillation",
            "Not for emergencies or diagnosis." to "emergency",
            "Share with your doctor" to "your doctor",
            "Screening is due" to "screening",
            "You may be at  risk" to "at risk",
            "Clinical reading" to "clinical",
            "Seek medical attention" to "seek medical",
        )
        for ((text, term) in caught) {
            val found = AlertContentPolicy.medicalVocabularyIn(text)
            assertTrue("'$term' in \"$text\", found $found", term in found || (term == "emergency" && "diagnos" in found))
        }
        assertEquals(listOf("diagnos"), AlertContentPolicy.medicalVocabularyIn("Not for diagnosis."))
        for (clean in listOf(
            "Resting heart rate 66 bpm over the last 24 h, above your usual 53–60 bpm.",
            "Keystroke cadence", "Romania", "Outside your usual range", "Bios is not a medical device.",
        )) {
            assertTrue(clean, AlertContentPolicy.medicalVocabularyIn(clean).isEmpty())
        }
    }

    @Test
    fun `the overlay languages with a reviewed list are checked in their own words`() {
        assertEquals(listOf("enfermedad"), AlertContentPolicy.medicalVocabularyIn("Signos de enfermedad", "es"))
        assertEquals(listOf("doença"), AlertContentPolicy.medicalVocabularyIn("Sinais de doença", "pt"))
        assertEquals(listOf("illness"), AlertContentPolicy.medicalVocabularyIn("Signs of illness", "es"))
        assertTrue(AlertContentPolicy.medicalVocabularyIn("Frecuencia cardíaca en reposo 66 lpm.", "es").isEmpty())
    }

    @Test
    fun `literals are read whole, without comments, templates or keys`() {
        val source = """
            // "seek care" in a comment
            /* "illness" in a block comment */
            val a = "Seek " +
                "care now"
            val b = "Value ${'$'}{format("illness")} and ${'$'}name"
            val c = '"'
            val route = "clinical_entry"
            val d = ${"\"\"\""}raw "quoted" text${"\"\"\""}
        """.trimIndent()
        val texts = KotlinStringLiterals.of(source).map { it.text }
        assertEquals(listOf("Seek care now", "Value  …  and  … ", "clinical_entry", "raw \"quoted\" text"), texts)
        assertEquals(3, KotlinStringLiterals.of(source).first().line)
        assertTrue(isKey("clinical_entry"))
        assertTrue(isKey("treatmentCourseId"))
        assertTrue(isKey("TreatmentCourse/ … "))
        assertTrue(!isKey("Clinical reading") && !isKey("Symptom"))
    }

    /** Routes, preference keys, column names, file names: no whitespace, and lowercase-led or punctuated like a key. */
    private fun isKey(literal: String): Boolean {
        val text = literal.replace(" … ", "")
        return text.none { it.isWhitespace() } &&
            (text.firstOrNull()?.isUpperCase() != true || text.any { it in KEY_PUNCTUATION })
    }

    private companion object {
        const val SOURCE_ROOT = "java/com/bios/app"
        const val POLICY_FILE = "alerts/AlertContentPolicy.kt"
        val SCANNED_PACKAGES = listOf("ui", "alerts", "export", "platform", "engine", "model", "i18n", "config")
        val NOTIFICATION_MARKS = listOf("NotificationCompat", "NotificationChannel")
        const val KEY_PUNCTUATION = "_/:"
        const val MIN_SOURCES = 100
        const val MIN_REASON = 20
        const val MAX_SHOWN = 120
    }
}
