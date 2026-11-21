package com.bios.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bios.app.data.BiosDatabase
import com.bios.app.data.InstrumentOnlyMigrations
import com.bios.app.engine.AnomalyDetector
import com.bios.app.model.Anomaly
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema 38 → 39 on a real SQLite file, opened by Room afterwards (which
 * also runs the later steps) so the migrated schema is validated against
 * the entities. The limit under test
 * (docs/specs/instrument-only.md): a stored alert loses its interpretation
 * text and nothing else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class InstrumentOnlyMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "instrument-only-migration.db"
    private var db: BiosDatabase? = null

    private fun open(): BiosDatabase =
        Room.databaseBuilder(context, BiosDatabase::class.java, name)
            .addMigrations(InstrumentOnlyMigrations.MIGRATION_38_39, InstrumentOnlyMigrations.MIGRATION_39_40)
            .allowMainThreadQueries()
            .build()
            .also { db = it }

    private val pattern = Anomaly(
        id = "pattern", detectedAt = 1_000L, metricTypes = "[\"resting_heart_rate\",\"heart_rate_variability\"]",
        deviationScores = "{\"resting_heart_rate\":2.4,\"heart_rate_variability\":-2.1}", combinedScore = 2.3,
        patternId = "infection_onset", severity = 2, title = "Possible infection onset",
        explanation = "Elevated resting heart rate with reduced HRV can precede illness.",
        suggestedAction = "Consider resting and monitoring symptoms.",
        acknowledged = true, acknowledgedAt = 2_000L, feedbackAt = 3_000L, feltSick = true, visitedDoctor = true,
        diagnosis = "Cold", symptoms = "Sore throat", notes = "Stayed home", outcomeAccurate = true,
    )
    private val holistic = Anomaly(
        id = "holistic", detectedAt = 5_000L, metricTypes = "[\"resting_heart_rate\"]",
        deviationScores = "{\"resting_heart_rate\":3.7}", combinedScore = 0.75,
        patternId = AnomalyDetector.ML_PATTERN_ID, severity = 1, title = "Unusual health pattern detected",
        explanation = "Resting heart rate 66 bpm over the last 24 h, above your usual 53–60 bpm.",
        suggestedAction = "Review your recent health trends and note any symptoms.",
        notes = "Late dinner", signalSnapshot = "[{\"metric\":\"resting_heart_rate\",\"value\":66.0}]",
    )
    private val unnamed = Anomaly(
        id = "unnamed", detectedAt = 6_000L, metricTypes = "[]", deviationScores = "{}", combinedScore = 1.0,
        patternId = null, severity = 4, title = "Old title", explanation = "Old text", suggestedAction = "Old step",
    )

    /** A version-38 file: today's schema plus the review table, holding alerts written by the earlier version. */
    @Before
    fun writeVersion38(): Unit = runBlocking {
        context.deleteDatabase(name)
        val current = open()
        listOf(pattern, holistic, unnamed).forEach { current.anomalyDao().insert(it) }
        current.close()

        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.execSQL(REVIEW_TABLE)
            raw.execSQL(
                "INSERT INTO professional_reviews (id, anomalyId, requestedAt, status, sharedExplanation, sharedBaselines) " +
                    "VALUES ('r', 'pattern', 1, 0, 1, 1)"
            )
            raw.version = 38
        }
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(name)
    }

    private fun migrated(): Map<String, Anomaly> = runBlocking { open().anomalyDao().fetchAll().associateBy { it.id } }

    @Test
    fun `the migration is the one from 38 to 39 and uses the detector's identifier`() {
        assertEquals(38, InstrumentOnlyMigrations.MIGRATION_38_39.startVersion)
        assertEquals(39, InstrumentOnlyMigrations.MIGRATION_38_39.endVersion)
        assertEquals(AnomalyDetector.ML_PATTERN_ID, InstrumentOnlyMigrations.DEVIATION_ALERT_ID)
        assertEquals(AnomalyDetector.TITLE, InstrumentOnlyMigrations.DEVIATION_TITLE)
    }

    @Test
    fun `a pattern alert loses its interpretation text and nothing else`() {
        val after = migrated().getValue("pattern")
        assertEquals(
            pattern.copy(
                title = "Earlier alert",
                explanation = "Recorded by an earlier version. Its interpretation text was removed; " +
                    "the measured values are kept.",
                suggestedAction = null,
            ),
            after,
        )
        assertEquals("infection_onset", after.patternId)
        assertEquals("Cold", after.diagnosis)
    }

    @Test
    fun `a deviation alert keeps its measured lines and takes the current title`() {
        assertEquals(
            holistic.copy(title = "Outside your usual range", suggestedAction = null),
            migrated().getValue("holistic"),
        )
    }

    @Test
    fun `an alert with no identifier is treated as an earlier alert and keeps its tier`() {
        val after = migrated().getValue("unnamed")
        assertEquals("Earlier alert", after.title)
        assertNull(after.suggestedAction)
        assertEquals(4, after.severity)
    }

    @Test
    fun `no stored alert keeps the old wording`() {
        val text = migrated().values.joinToString(" ") {
            "${it.title} ${it.explanation} ${it.suggestedAction.orEmpty()}"
        }
        for (word in listOf("infection", "illness", "symptom", "health pattern", "Consider", "Old")) {
            assertFalse("'$word' in: $text", text.contains(word))
        }
    }

    @Test
    fun `the review table is dropped and the file reports the current version`() {
        migrated()
        db?.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertEquals(40, raw.version)
            raw.rawQuery("SELECT name FROM sqlite_master WHERE name = 'professional_reviews'", null).use { c ->
                assertEquals(0, c.count)
            }
        }
    }

    private companion object {
        /** As created by the 4 → 5 migration. */
        const val REVIEW_TABLE = """
            CREATE TABLE IF NOT EXISTS professional_reviews (
                id TEXT NOT NULL PRIMARY KEY, anomalyId TEXT NOT NULL, requestedAt INTEGER NOT NULL,
                status INTEGER NOT NULL, shareMethod TEXT, sharedMetrics TEXT, sharedWindowDays INTEGER,
                sharedExplanation INTEGER NOT NULL, sharedBaselines INTEGER NOT NULL, respondedAt INTEGER,
                professionalNotes TEXT, clinicallyRelevant INTEGER, recommendation TEXT, ownerFoundHelpful INTEGER,
                FOREIGN KEY (anomalyId) REFERENCES anomalies(id) ON DELETE CASCADE
            )"""
    }
}
