package com.bios.app.engine

/**
 * Pure descriptive statistics over owner-logged menstruation onsets.
 *
 * Instrument, not coach: lengths are reported exactly as logged — no
 * smoothing, no outlier suppression, no "irregularity" grading. The owner
 * reads the spread; Bios does not editorialize it. Day bucketing is UTC,
 * matching [CycleInference] / `CycleDerivation` row keying.
 */
object CycleStats {

    private const val MS_PER_DAY = 86_400_000L

    /** UTC-day buckets of onsets, ascending, same-day duplicates collapsed. */
    fun onsetBuckets(onsetTimestamps: List<Long>): List<Long> =
        onsetTimestamps.map { it / MS_PER_DAY }.toSortedSet().toList()

    /**
     * Cycle lengths in days between consecutive onsets, oldest first.
     * n distinct onset days yield n-1 lengths; fewer than two yield none.
     */
    fun cycleLengthsDays(onsetTimestamps: List<Long>): List<Int> =
        onsetBuckets(onsetTimestamps).zipWithNext { a, b -> (b - a).toInt() }

    fun median(values: List<Int>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble()
        else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /**
     * Clinical day-of-cycle for a UTC day bucket: 1-based days since the
     * most recent onset on or before it; null before any logged onset —
     * we don't fabricate a numbering without an anchor (mirrors
     * [CycleInference.deriveCycleDays]).
     */
    fun cycleDayFor(dayBucket: Long, onsetTimestamps: List<Long>): Int? {
        val anchor = onsetBuckets(onsetTimestamps).lastOrNull { it <= dayBucket }
            ?: return null
        return ((dayBucket - anchor) + 1).toInt()
    }
}
