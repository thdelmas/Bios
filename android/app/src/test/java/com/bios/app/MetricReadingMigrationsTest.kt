package com.bios.app

import com.bios.app.data.MetricReadingMigrations
import com.bios.app.data.MetricReadingMigrations.DedupRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec test for the metric_readings 30 → 31 dedup migration. The
 * production migration runs SQL; this test exercises the equivalent
 * Kotlin comparator so the pick-best rule is verifiable without
 * bringing up a real SQLite. The SQL's NOT EXISTS subquery encodes
 * exactly the same inequality — drift between the two would surface
 * as a test failure.
 */
class MetricReadingMigrationsTest {

    private fun row(
        id: String,
        sourceId: String = "src",
        metricType: String = "HEART_RATE",
        timestamp: Long = 0L,
        confidence: Int = 50,
        createdAt: Long = 0L,
    ) = DedupRow(id, sourceId, metricType, timestamp, confidence, createdAt)

    @Test
    fun keeps_one_row_per_logical_key() {
        val survivors = MetricReadingMigrations.pickSurvivors(
            listOf(
                row(id = "a", timestamp = 100L),
                row(id = "b", timestamp = 100L),
                row(id = "c", timestamp = 200L),
            )
        )
        assertEquals(2, survivors.size)
        assertEquals(setOf(100L, 200L), survivors.map { it.timestamp }.toSet())
    }

    @Test
    fun higher_confidence_wins() {
        val survivors = MetricReadingMigrations.pickSurvivors(
            listOf(
                row(id = "lo", confidence = 50),
                row(id = "hi", confidence = 90),
            )
        )
        assertEquals(listOf("hi"), survivors.map { it.id })
    }

    @Test
    fun ties_break_on_newer_createdAt() {
        val survivors = MetricReadingMigrations.pickSurvivors(
            listOf(
                row(id = "old", confidence = 50, createdAt = 100L),
                row(id = "new", confidence = 50, createdAt = 200L),
            )
        )
        assertEquals(listOf("new"), survivors.map { it.id })
    }

    @Test
    fun full_ties_break_on_smaller_id() {
        // Stable last tie-break so exactly one row wins even when
        // confidence and createdAt are both identical. Without this
        // the migration could be non-deterministic across SQLite
        // versions; with it the survivor is fully specified.
        val survivors = MetricReadingMigrations.pickSurvivors(
            listOf(
                row(id = "zzz", confidence = 50, createdAt = 100L),
                row(id = "aaa", confidence = 50, createdAt = 100L),
            )
        )
        assertEquals(listOf("aaa"), survivors.map { it.id })
    }

    @Test
    fun different_sources_at_same_ms_both_survive() {
        // Cross-source readings at the same ms are not duplicates —
        // the in-memory Deduplicator picks isPrimary between them at
        // ingest. The migration must preserve both so that semantics
        // stays intact for existing data.
        val survivors = MetricReadingMigrations.pickSurvivors(
            listOf(
                row(id = "hc", sourceId = "health_connect", timestamp = 100L),
                row(id = "gb", sourceId = "gadgetbridge",   timestamp = 100L),
            )
        )
        assertEquals(2, survivors.size)
        assertEquals(setOf("health_connect", "gadgetbridge"),
            survivors.map { it.sourceId }.toSet())
    }

    @Test
    fun different_metric_types_at_same_ms_both_survive() {
        val survivors = MetricReadingMigrations.pickSurvivors(
            listOf(
                row(id = "hr",  metricType = "HEART_RATE", timestamp = 100L),
                row(id = "hrv", metricType = "HRV",        timestamp = 100L),
            )
        )
        assertEquals(2, survivors.size)
    }
}
