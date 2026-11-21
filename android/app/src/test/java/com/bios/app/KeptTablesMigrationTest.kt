package com.bios.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.bios.app.data.BiosDatabase
import com.bios.app.data.InstrumentOnlyMigrations
import com.bios.app.data.ReproductiveDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema 39 → 40 of the main database and 5 → 6 of the reproductive one, on
 * real SQLite files that Room opens afterwards. The entities behind removed
 * screens left the schema; the limit under test is that what the owner
 * entered there is still physically in the file, row for row, and that Room
 * opens a file carrying tables it no longer maps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class KeptTablesMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mainName = "kept-tables-main.db"
    private val reproductiveName = "kept-tables-reproductive.db"
    private val opened = mutableListOf<RoomDatabase>()

    private fun openMain(): BiosDatabase =
        Room.databaseBuilder(context, BiosDatabase::class.java, mainName)
            .addMigrations(InstrumentOnlyMigrations.MIGRATION_39_40, InstrumentOnlyMigrations.MIGRATION_40_41)
            .allowMainThreadQueries()
            .build()
            .also { opened += it }

    private fun openReproductive(): ReproductiveDatabase =
        Room.databaseBuilder(context, ReproductiveDatabase::class.java, reproductiveName)
            .addMigrations(InstrumentOnlyMigrations.REPRODUCTIVE_MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
            .also { opened += it }

    /** Adds the old tables with one row each to a file Room just created, and stamps the earlier version on it. */
    private fun writeEarlierVersion(name: String, version: Int, statements: List<String>) {
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            statements.forEach { raw.execSQL(it) }
            raw.version = version
        }
    }

    @Before
    fun writeEarlierFiles(): Unit = runBlocking {
        context.deleteDatabase(mainName)
        context.deleteDatabase(reproductiveName)

        openMain().apply { anomalyDao().fetchAll() }.close()
        writeEarlierVersion(mainName, 39, MAIN_TABLES + MAIN_ROWS)

        openReproductive().apply { readingDao().countAll() }.close()
        writeEarlierVersion(reproductiveName, 5, REPRODUCTIVE_TABLES + REPRODUCTIVE_ROWS)
    }

    @After
    fun tearDown() {
        opened.forEach { if (it.isOpen) it.close() }
        context.deleteDatabase(mainName)
        context.deleteDatabase(reproductiveName)
    }

    private fun RoomDatabase.rowCount(table: String): Int =
        openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    private fun RoomDatabase.text(sql: String): String =
        openHelper.readableDatabase.query(sql).use { c ->
            c.moveToFirst()
            c.getString(0)
        }

    @Test
    fun `the steps are 39 to 40 and 5 to 6`() {
        assertEquals(39, InstrumentOnlyMigrations.MIGRATION_39_40.startVersion)
        assertEquals(40, InstrumentOnlyMigrations.MIGRATION_39_40.endVersion)
        assertEquals(5, InstrumentOnlyMigrations.REPRODUCTIVE_MIGRATION_5_6.startVersion)
        assertEquals(6, InstrumentOnlyMigrations.REPRODUCTIVE_MIGRATION_5_6.endVersion)
    }

    @Test
    fun `the fixture writes every kept table`() {
        for (table in InstrumentOnlyMigrations.KEPT_TABLES) {
            assertTrue(table, MAIN_TABLES.any { it.contains("EXISTS $table ") })
        }
        for (table in InstrumentOnlyMigrations.KEPT_REPRODUCTIVE_TABLES) {
            assertTrue(table, REPRODUCTIVE_TABLES.any { it.contains("EXISTS $table ") })
        }
    }

    @Test
    fun `Room opens the main file at the current version and the mapped tables work`(): Unit = runBlocking {
        val db = openMain()
        assertTrue(db.anomalyDao().fetchAll().isEmpty())
        assertTrue(db.headacheLogDao().fetchAll().isEmpty())
        assertEquals(41, db.openHelper.readableDatabase.version)
    }

    @Test
    fun `every table of a removed screen is still in the main file with its row`(): Unit = runBlocking {
        val db = openMain()
        db.anomalyDao().fetchAll()
        for (table in InstrumentOnlyMigrations.KEPT_TABLES) {
            assertEquals(table, 1, db.rowCount(table))
        }
    }

    @Test
    fun `what the owner entered reads back unchanged`(): Unit = runBlocking {
        val db = openMain()
        db.anomalyDao().fetchAll()
        assertEquals(
            "Ada|+34 600 000 000|sister",
            db.text("SELECT name || '|' || phoneNumber || '|' || note FROM emergency_contacts"),
        )
        assertEquals("kitchen drawer", db.text("SELECT documentLocation FROM goals_of_care"))
        assertEquals("father, at 52", db.text("SELECT note FROM risk_profile"))
        assertEquals("after the walk", db.text("SELECT notes FROM fast_stroke_events"))
        assertEquals("7", db.text("SELECT painScore FROM esas_reports"))
        assertEquals("knee", db.text("SELECT procedureTag FROM perioperative_baselines"))
        assertEquals("at the clinic", db.text("SELECT note FROM screening_entries"))
        assertEquals("Ada", db.text("SELECT proxyContactName FROM clinical_directive"))
    }

    @Test
    fun `Room opens the reproductive file at 6 and keeps both tables with their rows`(): Unit = runBlocking {
        val db = openReproductive()
        assertEquals(0, db.readingDao().countAll())
        assertTrue(db.contraceptionEntryDao().fetchAll().isEmpty())
        assertEquals(6, db.openHelper.readableDatabase.version)
        for (table in InstrumentOnlyMigrations.KEPT_REPRODUCTIVE_TABLES) {
            assertEquals(table, 1, db.rowCount(table))
        }
        assertEquals("since March", db.text("SELECT notes FROM menopause_stage_entries"))
        assertEquals("2 mg", db.text("SELECT dose FROM gender_affirming_care_entries"))
    }

    private companion object {
        /** As created by the migrations that introduced each table. */
        val MAIN_TABLES = listOf(
            """CREATE TABLE IF NOT EXISTS screening_entries (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, screeningKey TEXT NOT NULL,
                performedDate INTEGER NOT NULL, note TEXT)""",
            """CREATE TABLE IF NOT EXISTS risk_profile (
                id INTEGER NOT NULL PRIMARY KEY, firstDegreeCadEarly INTEGER NOT NULL,
                firstDegreeBreastOvarianCancer INTEGER NOT NULL, firstDegreeColorectalCancer INTEGER NOT NULL,
                firstDegreeDiabetes INTEGER NOT NULL, firstDegreeOsteoporosisHipFracture INTEGER NOT NULL,
                firstDegreeMelanoma INTEGER NOT NULL, personalTobaccoYears INTEGER, personalTobaccoPackYears INTEGER,
                personalTobaccoQuitDate INTEGER, personalCancerHistory TEXT, personalCardiacEventHistory TEXT,
                note TEXT, updatedAt INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS goals_of_care (
                id INTEGER NOT NULL PRIMARY KEY, cprPreference TEXT NOT NULL, hospitalizationPreference TEXT NOT NULL,
                interventionLevel TEXT NOT NULL, lastReviewedAt INTEGER NOT NULL, documentLocation TEXT, notes TEXT)""",
            """CREATE TABLE IF NOT EXISTS clinical_directive (
                id INTEGER NOT NULL PRIMARY KEY, hasAdvanceDirective INTEGER NOT NULL, hasPolst INTEGER NOT NULL,
                hasHealthcareProxy INTEGER NOT NULL, proxyContactName TEXT, proxyContactPhone TEXT,
                updatedAt INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS fast_stroke_events (
                id TEXT NOT NULL PRIMARY KEY, timestamp INTEGER NOT NULL, facialDrooping INTEGER NOT NULL,
                armWeakness INTEGER NOT NULL, speechDifficulty INTEGER NOT NULL, notes TEXT)""",
            """CREATE TABLE IF NOT EXISTS esas_reports (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, painScore INTEGER NOT NULL,
                tirednessScore INTEGER NOT NULL, drowsinessScore INTEGER NOT NULL, nauseaScore INTEGER NOT NULL,
                appetiteScore INTEGER NOT NULL, shortnessOfBreathScore INTEGER NOT NULL,
                depressionScore INTEGER NOT NULL, anxietyScore INTEGER NOT NULL, wellbeingScore INTEGER NOT NULL,
                otherSymptomLabel TEXT, otherSymptomScore INTEGER, note TEXT)""",
            """CREATE TABLE IF NOT EXISTS emergency_contacts (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, relationship TEXT NOT NULL,
                phoneNumber TEXT NOT NULL, minTierLevel INTEGER NOT NULL, acknowledgementTimeoutMinutes INTEGER,
                createdAt INTEGER NOT NULL, note TEXT)""",
            """CREATE TABLE IF NOT EXISTS perioperative_baselines (
                id INTEGER NOT NULL PRIMARY KEY, procedureDate INTEGER NOT NULL, procedureTag TEXT,
                snapshotAt INTEGER NOT NULL, restingHeartRateMean REAL, restingHeartRateSd REAL)""",
        )

        val MAIN_ROWS = listOf(
            "INSERT INTO screening_entries (screeningKey, performedDate, note) VALUES ('k', 1, 'at the clinic')",
            "INSERT INTO risk_profile VALUES (1, 1, 0, 0, 0, 0, 0, NULL, NULL, NULL, NULL, NULL, 'father, at 52', 2)",
            "INSERT INTO goals_of_care VALUES (1, 'A', 'B', 'C', 3, 'kitchen drawer', NULL)",
            "INSERT INTO clinical_directive VALUES (1, 1, 0, 1, 'Ada', '+34 600 000 000', 4)",
            "INSERT INTO fast_stroke_events VALUES ('f', 5, 0, 0, 0, 'after the walk')",
            "INSERT INTO esas_reports (timestamp, painScore, tirednessScore, drowsinessScore, nauseaScore, " +
                "appetiteScore, shortnessOfBreathScore, depressionScore, anxietyScore, wellbeingScore) " +
                "VALUES (6, 7, 1, 1, 1, 1, 1, 1, 1, 1)",
            "INSERT INTO emergency_contacts (name, relationship, phoneNumber, minTierLevel, createdAt, note) " +
                "VALUES ('Ada', 'sibling', '+34 600 000 000', 4, 7, 'sister')",
            "INSERT INTO perioperative_baselines VALUES (1, 8, 'knee', 9, 58.0, 2.0)",
        )

        /** As created by the reproductive database's 4 → 5 migration. */
        val REPRODUCTIVE_TABLES = listOf(
            """CREATE TABLE IF NOT EXISTS menopause_stage_entries (
                id TEXT NOT NULL PRIMARY KEY, stage TEXT NOT NULL, recordedDate INTEGER NOT NULL,
                notes TEXT, createdAt INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS gender_affirming_care_entries (
                id TEXT NOT NULL PRIMARY KEY, hormoneType TEXT NOT NULL, startDate INTEGER NOT NULL,
                endDate INTEGER, dose TEXT, notes TEXT, createdAt INTEGER NOT NULL)""",
        )

        val REPRODUCTIVE_ROWS = listOf(
            "INSERT INTO menopause_stage_entries VALUES ('m', 'S', 1, 'since March', 2)",
            "INSERT INTO gender_affirming_care_entries VALUES ('g', 'H', 1, NULL, '2 mg', NULL, 2)",
        )
    }
}
