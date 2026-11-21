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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema 40 → 41 on a real SQLite file that Room opens afterwards. Stored
 * alerts lose the name of the removed pattern that raised them and nothing
 * else; the feedback table leaves the schema and stays in the file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AlertIdentifierMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "alert-identifier-migration.db"
    private var db: BiosDatabase? = null

    private fun open(): BiosDatabase =
        Room.databaseBuilder(context, BiosDatabase::class.java, name)
            .addMigrations(InstrumentOnlyMigrations.MIGRATION_40_41)
            .allowMainThreadQueries()
            .build()
            .also { db = it }

    private val named = Anomaly(
        id = "named", detectedAt = 1_000L, metricTypes = "[\"resting_heart_rate\"]",
        deviationScores = "{\"resting_heart_rate\":2.4}", combinedScore = 2.3,
        patternId = "infection_onset", severity = 2, title = "Earlier alert", explanation = "Kept text",
        acknowledged = true, acknowledgedAt = 2_000L, feedbackAt = 3_000L, feltSick = true, visitedDoctor = true,
        diagnosis = "Cold", symptoms = "Sore throat", notes = "Stayed home", outcomeAccurate = true,
        signalSnapshot = "[{\"metric\":\"resting_heart_rate\",\"value\":66.0}]",
    )
    private val deviation = Anomaly(
        id = "deviation", detectedAt = 5_000L, metricTypes = "[\"resting_heart_rate\"]",
        deviationScores = "{\"resting_heart_rate\":3.7}", combinedScore = 0.75,
        patternId = AnomalyDetector.ML_PATTERN_ID, severity = 1, title = "Outside your usual range",
        explanation = "Resting heart rate 66 bpm over the last 24 h, above your usual 53–60 bpm.",
        notes = "Late dinner",
    )
    private val unnamed = Anomaly(
        id = "unnamed", detectedAt = 6_000L, metricTypes = "[]", deviationScores = "{}", combinedScore = 1.0,
        patternId = null, severity = 4, title = "Earlier alert", explanation = "Kept text",
    )

    /** A version-40 file: today's schema plus the feedback table with one answer. */
    @Before
    fun writeVersion40(): Unit = runBlocking {
        context.deleteDatabase(name)
        val current = open()
        listOf(named, deviation, unnamed).forEach { current.anomalyDao().insert(it) }
        current.close()

        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.execSQL(FEEDBACK_TABLE)
            raw.execSQL(
                "INSERT INTO user_feedback (id, surface, surfaceItemId, rating, comment, createdAt) " +
                    "VALUES ('f', 'alert', 'infection_onset', 1, 'useful that week', 7)"
            )
            raw.version = 40
        }
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(name)
    }

    private fun migrated(): Map<String, Anomaly> = runBlocking { open().anomalyDao().fetchAll().associateBy { it.id } }

    @Test
    fun `the step is 40 to 41`() {
        assertEquals(40, InstrumentOnlyMigrations.MIGRATION_40_41.startVersion)
        assertEquals(41, InstrumentOnlyMigrations.MIGRATION_40_41.endVersion)
    }

    @Test
    fun `a pattern alert loses the pattern name and keeps every other column`() {
        assertEquals(named.copy(patternId = "earlier_alert"), migrated().getValue("named"))
    }

    @Test
    fun `an alert with no identifier takes the same identifier`() {
        assertEquals(unnamed.copy(patternId = "earlier_alert"), migrated().getValue("unnamed"))
    }

    @Test
    fun `a deviation alert is untouched`() {
        assertEquals(deviation, migrated().getValue("deviation"))
    }

    @Test
    fun `the feedback table stays in the file with its row and Room opens at 41`() {
        migrated()
        val helper = db!!.openHelper.readableDatabase
        assertEquals(41, helper.version)
        helper.query("SELECT comment FROM ${InstrumentOnlyMigrations.KEPT_FEEDBACK_TABLE}").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("useful that week", c.getString(0))
        }
    }

    private companion object {
        /** As created by the migration that introduced the table. */
        const val FEEDBACK_TABLE = """
            CREATE TABLE IF NOT EXISTS user_feedback (
                id TEXT NOT NULL PRIMARY KEY, surface TEXT NOT NULL, surfaceItemId TEXT, rating INTEGER NOT NULL,
                comment TEXT, createdAt INTEGER NOT NULL
            )"""
    }
}
