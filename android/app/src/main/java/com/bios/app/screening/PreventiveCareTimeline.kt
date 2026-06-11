package com.bios.app.screening

/**
 * Turns the cadence engine's per-entry [ScreeningStatus] results into a
 * single chronological timeline the Preventive Care screen renders against
 * one shared time axis: what's coming up (above TODAY), what's done and
 * current (below it), what hasn't been recorded, and what doesn't apply.
 *
 * Pure function, no Android dependency — placement and ordering are
 * unit-testable without a device, the same way [ScreeningCadenceEngine]
 * stays pure. The screen supplies the engine results, the owner's
 * latest-performed dates, and `now`; this returns the ordered [Item] list.
 *
 * Manifesto guard: this only *arranges* what the engine already decided.
 * Minimum-interval checkups that came due read as the neutral
 * [ScreeningStatus.IntervalElapsed] and stay out of the overdue treatment —
 * the no-shame rule is preserved by carrying the status through untouched.
 */
object PreventiveCareTimeline {

    /** Where an item sits relative to the TODAY anchor. */
    enum class Section {
        /** Due, overdue, or eligible-again — sits above TODAY, soonest first. */
        UPCOMING,
        /** Recorded and current — sits below TODAY, most-recent first. */
        PAST,
        /** Eligible but no date on file — recommended, owner can record. */
        NOT_RECORDED,
        /** Filtered out by age / anatomy / risk gate — shown last, with reason. */
        NOT_APPLICABLE,
    }

    data class Item(
        val entry: ScreeningCatalogEntry,
        val status: ScreeningStatus,
        val section: Section,
        /**
         * Epoch millis this item anchors at on the axis: next-due for
         * [Section.UPCOMING], last-performed for [Section.PAST]. Null for the
         * undated sections.
         */
        val anchorDate: Long?,
        /**
         * Next-due epoch for dated [Section.PAST] items (rendered as a
         * subtitle). Null for one-time tests (no repeat) and undated sections.
         */
        val nextDue: Long?,
    )

    private const val DAYS_PER_MONTH = 30.4375  // matches ScreeningCadenceEngine
    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * Build the ordered timeline. [results] is the engine's output (already
     * filtered for hidden hereditary gates by the caller); [lastPerformedByKey]
     * maps screening key → most-recent performed date (manual ledger merged
     * with health-derived dates). Returns items grouped by section in render
     * order: upcoming (soonest first), past (newest first), not-recorded,
     * not-applicable (both alphabetical).
     */
    fun build(
        results: List<Pair<ScreeningCatalogEntry, ScreeningStatus>>,
        lastPerformedByKey: Map<String, Long?>,
        now: Long,
    ): List<Item> {
        val items = results.map { (entry, status) ->
            val last = lastPerformedByKey[entry.key]
            val cadence = cadenceMillis(entry)
            val nextDue = if (last != null && cadence != null) last + cadence else null
            when (status) {
                is ScreeningStatus.NotEligible ->
                    Item(entry, status, Section.NOT_APPLICABLE, anchorDate = null, nextDue = null)
                ScreeningStatus.NoRecord ->
                    Item(entry, status, Section.NOT_RECORDED, anchorDate = null, nextDue = null)
                is ScreeningStatus.DueNow ->
                    Item(entry, status, Section.UPCOMING, anchorDate = nextDue ?: now, nextDue = nextDue)
                is ScreeningStatus.IntervalElapsed ->
                    Item(entry, status, Section.UPCOMING, anchorDate = nextDue ?: now, nextDue = nextDue)
                is ScreeningStatus.Current ->
                    Item(entry, status, Section.PAST, anchorDate = last, nextDue = nextDue)
                is ScreeningStatus.WithinInterval ->
                    Item(entry, status, Section.PAST, anchorDate = last, nextDue = nextDue)
            }
        }

        val upcoming = items.filter { it.section == Section.UPCOMING }
            .sortedBy { it.anchorDate ?: now }
        val past = items.filter { it.section == Section.PAST }
            .sortedByDescending { it.anchorDate ?: 0L }
        val notRecorded = items.filter { it.section == Section.NOT_RECORDED }
            .sortedBy { it.entry.displayName }
        val notApplicable = items.filter { it.section == Section.NOT_APPLICABLE }
            .sortedBy { it.entry.displayName }

        return upcoming + past + notRecorded + notApplicable
    }

    /** Recommended-interval window in millis, or null for one-time tests. */
    private fun cadenceMillis(entry: ScreeningCatalogEntry): Long? =
        if (entry.cadenceMonths == Int.MAX_VALUE) null
        else (entry.cadenceMonths * DAYS_PER_MONTH * MILLIS_PER_DAY).toLong()
}
