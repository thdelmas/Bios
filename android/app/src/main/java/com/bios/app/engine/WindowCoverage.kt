package com.bios.app.engine

import com.bios.app.data.dao.TimedValue
import com.bios.app.model.Anomaly
import kotlin.math.ceil
import kotlin.math.max

/** A rule window's sensor values plus whether they are enough to speak about the window. */
data class WindowSample(
    val values: List<Double>,
    /** Coverage and freshness both hold (docs/specs/rule-window-coverage.md). */
    val sufficient: Boolean,
    val newestTimestamp: Long?,
)

/**
 * Coverage, freshness and evidence-reuse rules for baseline-deviation alerts
 * (docs/specs/rule-window-coverage.md). Pure so the Sunday case (one night
 * left in a 48 h window after the watch was not worn) is unit-testable.
 */
object WindowCoverage {
    /** Newest reading older than this makes the window stale. */
    const val FRESHNESS_HOURS = 30

    /** Share of the window's 24 h slots that must hold at least one reading. */
    const val MIN_SLOT_COVERAGE = 0.6

    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 24 * HOUR_MS

    fun slotsFor(windowHours: Int): Int = max(1, ceil(windowHours / 24.0).toInt())

    fun requiredSlots(windowHours: Int): Int = max(1, ceil(slotsFor(windowHours) * MIN_SLOT_COVERAGE).toInt())

    fun sample(readings: List<TimedValue>, windowHours: Int, now: Long): WindowSample {
        if (readings.isEmpty()) return WindowSample(emptyList(), sufficient = false, newestTimestamp = null)
        val newest = readings.maxOf { it.timestamp }
        val fresh = now - newest <= FRESHNESS_HOURS * HOUR_MS
        val slots = slotsFor(windowHours)
        val covered = readings.map { ((now - it.timestamp) / DAY_MS).toInt().coerceIn(0, slots - 1) }.toSet().size
        return WindowSample(
            values = readings.map { it.value },
            sufficient = fresh && covered >= requiredSlots(windowHours),
            newestTimestamp = newest,
        )
    }

    /**
     * True when an earlier alert sharing one of [metricKeys] was raised at or
     * after [newestEvidence]: no reading of these metrics has arrived since,
     * so a new alert would re-count the same night under another name.
     */
    fun evidenceAlreadyAlerted(metricKeys: Set<String>, newestEvidence: Long, recent: List<Anomaly>): Boolean =
        recent.any { a -> a.detectedAt >= newestEvidence && metricKeys.any { a.metricTypes.contains("\"$it\"") } }
}
