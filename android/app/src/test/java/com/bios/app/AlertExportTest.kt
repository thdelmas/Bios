package com.bios.app

import com.bios.app.alerts.AlertMeasurements
import com.bios.app.alerts.MeasuredSignal
import com.bios.app.alerts.SignalSnapshot
import com.bios.app.engine.AnomalyDetector
import com.bios.app.export.AlertExport
import com.bios.app.model.Anomaly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An alert leaves in a file, and over the local API, as measurements
 * (docs/specs/instrument-only.md): no condition name, no text of an earlier
 * version, no journal answers.
 */
class AlertExportTest {

    private val snapshot = SignalSnapshot.encode(
        listOf(
            MeasuredSignal("resting_heart_rate", 66.0, 53.0, 60.0, zScore = 3.7, readingCount = 4),
            MeasuredSignal("heart_rate_variability", 31.0, 38.0, 55.0, zScore = -2.1, readingCount = 2),
            MeasuredSignal("steps", 8000.0, 5000.0, 11000.0, zScore = 0.2, readingCount = 9),
        )
    )

    /** A pattern alert as an earlier version stored it, journal included. */
    private val earlier = Anomaly(
        id = "e", detectedAt = 1_000L, metricTypes = "[\"resting_heart_rate\",\"heart_rate_variability\"]",
        deviationScores = "{\"resting_heart_rate\":3.7,\"heart_rate_variability\":-2.1}", combinedScore = 2.3,
        patternId = "infection_onset", severity = 2, title = "Possible infection onset",
        explanation = "Elevated resting heart rate with reduced HRV can precede illness.",
        suggestedAction = "Consider resting and monitoring symptoms.",
        acknowledged = true, acknowledgedAt = 2_000L, feedbackAt = 3_000L, feltSick = true, visitedDoctor = true,
        diagnosis = "Cold", symptoms = "Sore throat", notes = "Stayed home", outcomeAccurate = true,
        signalSnapshot = snapshot,
    )

    private val withoutSnapshot = Anomaly(
        id = "n", detectedAt = 5_000L, metricTypes = "[\"resting_heart_rate\"]",
        deviationScores = "{\"resting_heart_rate\":3.7}", combinedScore = 0.75,
        patternId = AnomalyDetector.ML_PATTERN_ID, severity = 1, title = AnomalyDetector.TITLE,
        explanation = "Resting heart rate 66 bpm over the last 24 h, above your usual 53–60 bpm.",
    )

    @Test
    fun `the JSON form has exactly the measurement fields`() {
        val json = AlertExport.json(earlier)
        assertEquals(
            setOf("id", "detectedAt", "tier", "metrics", "measured", "acknowledged", "acknowledgedAt", "note"),
            json.keys().asSequence().toSet(),
        )
        assertEquals(2, json.getInt("tier"))
        assertEquals("resting_heart_rate", json.getJSONArray("metrics").getString(0))
        val first = json.getJSONArray("measured").getJSONObject(0)
        assertEquals("resting_heart_rate", first.getString("metric"))
        assertEquals(66.0, first.getDouble("value"), 0.0)
        assertEquals("bpm", first.getString("unit"))
        assertEquals(53.0, first.getDouble("usualLow"), 0.0)
        assertEquals(60.0, first.getDouble("usualHigh"), 0.0)
        assertEquals(2, json.getJSONArray("measured").length())
    }

    @Test
    fun `neither form carries a condition name, old text or a journal answer`() {
        val out = AlertExport.json(earlier).toString() + AlertExport.csvRow(earlier) { it }
        for (word in listOf("infection", "illness", "Consider", "Cold", "feltSick", "visitedDoctor", "diagnosis", "true,true")) {
            assertFalse("'$word' in: $out", out.contains(word))
        }
    }

    @Test
    fun `the note is what the owner wrote, as written`() {
        assertEquals("Sore throat\nStayed home", AlertExport.json(earlier).getString("note"))
        assertTrue(AlertExport.json(withoutSnapshot).isNull("note"))
    }

    @Test
    fun `the CSV row matches its header`() {
        assertEquals(AlertExport.CSV_HEADER.split(",").size, AlertExport.csvRow(withoutSnapshot) { it }.split(",").size)
        val row = AlertExport.csvRow(earlier) { "\"$it\"" }
        assertTrue(row, row.contains("resting_heart_rate=66.0 (53.0 to 60.0)"))
    }

    @Test
    fun `without a snapshot the metrics are listed and nothing is invented`() {
        val json = AlertExport.json(withoutSnapshot)
        assertEquals(1, json.getJSONArray("metrics").length())
        assertEquals(0, json.getJSONArray("measured").length())
    }

    @Test
    fun `the lines state each metric in units, largest deviation first`() {
        val lines = AlertMeasurements.lines(earlier)
        assertEquals(2, lines.size)
        assertEquals("Resting heart rate 66 bpm over the last 24 h, above your usual 53–60 bpm.", lines[0])
        assertTrue(lines[1], lines[1].startsWith("Heart rate variability (HRV) 31 ms over the last 24 h, below"))
    }

    @Test
    fun `a deviation alert without a snapshot gives its stored measured lines, an earlier alert gives none`() {
        assertEquals(listOf(withoutSnapshot.explanation), AlertMeasurements.lines(withoutSnapshot))
        assertTrue(AlertMeasurements.lines(earlier.copy(signalSnapshot = null)).isEmpty())
    }
}
