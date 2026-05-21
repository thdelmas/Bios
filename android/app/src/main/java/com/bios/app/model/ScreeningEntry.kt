package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Owner-recorded last-performed date for a recommended screening test
 * (audit gap §2.2: pull-side screening-cadence engine).
 *
 * One row per (owner, screeningKey, performedDate). The cadence engine
 * computes "next due" from the most recent row per [screeningKey] plus
 * the catalog cadence — owners keep their history (a colonoscopy in
 * 2018 *and* a follow-up in 2024) so multi-cycle screenings render
 * correctly.
 *
 * Distinct from `MetricReading`: screenings are dated procedural events,
 * not quantitative measurements. They never feed the anomaly detector
 * and never enter the baseline engine. Pull-side only — owner navigates
 * in, owner enters, no notifications fire.
 */
@Entity(
    tableName = "screening_entries",
    indices = [Index("screeningKey"), Index("performedDate")],
)
data class ScreeningEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Stable identifier from the ScreeningCatalog (e.g. "colorectal_cancer",
     * "mammogram", "lipid_panel"). Renames are breaking — the catalog pins
     * keys.
     */
    val screeningKey: String,
    /** Epoch millis when the screening was performed. */
    val performedDate: Long,
    /** Optional owner free-text annotation. */
    val note: String? = null,
)
