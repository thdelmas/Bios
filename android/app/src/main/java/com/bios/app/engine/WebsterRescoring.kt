package com.bios.app.engine

/**
 * Webster's post-FIR rescoring rules (Webster et al. 1982; carried
 * forward in Cole-Kripke 1992). Applied to the boolean wake/sleep
 * array produced by [ColeKripkeClassifier] to fix two well-documented
 * misclassification patterns the bare FIR cannot:
 *
 *  - Wake bouts naturally **persist** — once an owner has been awake
 *    for several minutes, even quiet sub-minutes during the wake
 *    period (browsing in stillness, reading) are wake from the
 *    actigraphy perspective. Rules **a / b / c** extend wake forward
 *    by 1 / 3 / 4 minutes after sustained wake runs.
 *  - **Brief sleep epochs sandwiched between long wake** are usually
 *    drowsy minutes the FIR caught, not real sleep. Rules **d / e**
 *    absorb short sleep runs surrounded by long wake runs back into
 *    wake.
 *
 * Both halves operate on the FIR output as the "original" — they
 * read the original to decide where rules fire, but write
 * exclusively to the rescored copy. This is deterministic in a
 * single pass (no fixed-point iteration), matching the canonical
 * `ghammad/pyActigraphy` / `dipetkov/actigraph.sleepr` reference
 * implementations.
 *
 * Rules deliberately do **not** address the opposite problem (brief
 * wake bouts during long sleep — posture shifts that the FIR
 * smoothing footprint extends to several minutes). That stays the
 * responsibility of
 * [com.bios.app.engine.PhoneSleepInference.mergeBriefAwake] +
 * `AWAKE_BOUT_MIN_MS`, which operates at the segment level after
 * Webster's rescoring lands.
 *
 * ## Rule reference
 *
 *  - **Rule a**: after at least 4 minutes of wake, the next 1 minute
 *    is wake regardless of FIR score.
 *  - **Rule b**: after at least 10 minutes of wake, the next 3
 *    minutes are wake regardless of FIR score.
 *  - **Rule c**: after at least 15 minutes of wake, the next 4
 *    minutes are wake regardless of FIR score.
 *  - **Rule d**: a sleep run of length ≤ 6 minutes flanked by wake
 *    runs of length ≥ 10 minutes on **both** sides is recoded as
 *    wake.
 *  - **Rule e**: a sleep run of length ≤ 10 minutes flanked by wake
 *    runs of length ≥ 20 minutes on **both** sides is recoded as
 *    wake.
 *
 * Each rule is more aggressive than the prior one in some
 * dimension (longer extension / longer absorbed run) but requires
 * a stronger context (longer preceding wake run / stronger
 * flanking).
 *
 * ## References
 *  - Webster JB, Kripke DF, Messin S, Mullaney DJ, Wyborney G
 *    (1982) — An activity-based sleep monitor system for ambulatory
 *    use. *Sleep* 5(4):389–399.
 *  - Cole RJ, Kripke DF, Gruen W, Mullaney DJ, Gillin JC (1992) —
 *    Automatic sleep/wake identification from wrist activity.
 *    *Sleep* 15(5):461–469.
 */
internal object WebsterRescoring {

    /** Rule a: extend wake by 1 sample after a ≥ 4-sample wake run. */
    const val RULE_A_PREV_WAKE_MIN: Int = 4
    const val RULE_A_EXTEND: Int = 1

    /** Rule b: extend wake by 3 samples after a ≥ 10-sample wake run. */
    const val RULE_B_PREV_WAKE_MIN: Int = 10
    const val RULE_B_EXTEND: Int = 3

    /** Rule c: extend wake by 4 samples after a ≥ 15-sample wake run. */
    const val RULE_C_PREV_WAKE_MIN: Int = 15
    const val RULE_C_EXTEND: Int = 4

    /** Rule d: absorb sleep runs of ≤ 6 surrounded by wake ≥ 10. */
    const val RULE_D_SLEEP_RUN_MAX: Int = 6
    const val RULE_D_FLANKING_WAKE_MIN: Int = 10

    /** Rule e: absorb sleep runs of ≤ 10 surrounded by wake ≥ 20. */
    const val RULE_E_SLEEP_RUN_MAX: Int = 10
    const val RULE_E_FLANKING_WAKE_MIN: Int = 20

    /**
     * Apply Webster's rules a–e to a boolean wake array (`true` =
     * wake). Returns a new array; the input is not modified.
     */
    fun rescore(awake: BooleanArray): BooleanArray {
        if (awake.isEmpty()) return BooleanArray(0)
        val rescored = awake.copyOf()
        applyForwardExtensionRules(awake, rescored)
        absorbBriefSleepRuns(awake, rescored)
        return rescored
    }

    /**
     * Rules a / b / c: forward-extend wake after sustained wake runs.
     * Read the preceding wake-run length from the **original** FIR
     * output so each rule fires at most once per wake → sleep
     * transition (the rescored array drifts ahead of the original but
     * doesn't loop the rule on its own writes).
     */
    private fun applyForwardExtensionRules(original: BooleanArray, rescored: BooleanArray) {
        // Pre-compute, for each index i, the count of consecutive
        // wake samples ending at i-1 (i.e. immediately before i) in
        // the ORIGINAL array.
        val precedingWakeRun = IntArray(original.size)
        var runLength = 0
        for (i in original.indices) {
            precedingWakeRun[i] = runLength
            runLength = if (original[i]) runLength + 1 else 0
        }
        for (i in original.indices) {
            if (original[i]) continue // current is wake → no extension needed
            val prev = precedingWakeRun[i]
            val extendBy = when {
                prev >= RULE_C_PREV_WAKE_MIN -> RULE_C_EXTEND
                prev >= RULE_B_PREV_WAKE_MIN -> RULE_B_EXTEND
                prev >= RULE_A_PREV_WAKE_MIN -> RULE_A_EXTEND
                else -> 0
            }
            for (k in 0 until extendBy) {
                val j = i + k
                if (j < rescored.size) rescored[j] = true
            }
        }
    }

    /**
     * Rules d / e: absorb brief sleep runs sandwiched between
     * sufficiently-long wake runs. Read run lengths from the
     * **original** FIR output (so Webster's own forward-extension
     * writes don't change which sleep runs qualify).
     */
    private fun absorbBriefSleepRuns(original: BooleanArray, rescored: BooleanArray) {
        var i = 0
        while (i < original.size) {
            if (original[i]) { i++; continue }
            val runStart = i
            while (i < original.size && !original[i]) i++
            val runEnd = i - 1 // inclusive
            val sleepRunLength = runEnd - runStart + 1

            val prevWake = countWakeBackward(original, runStart - 1)
            val nextWake = countWakeForward(original, runEnd + 1)

            val absorb = (sleepRunLength <= RULE_D_SLEEP_RUN_MAX &&
                prevWake >= RULE_D_FLANKING_WAKE_MIN &&
                nextWake >= RULE_D_FLANKING_WAKE_MIN) ||
                (sleepRunLength <= RULE_E_SLEEP_RUN_MAX &&
                    prevWake >= RULE_E_FLANKING_WAKE_MIN &&
                    nextWake >= RULE_E_FLANKING_WAKE_MIN)
            if (absorb) {
                for (j in runStart..runEnd) rescored[j] = true
            }
        }
    }

    private fun countWakeBackward(arr: BooleanArray, fromInclusive: Int): Int {
        var count = 0
        var i = fromInclusive
        while (i >= 0 && arr[i]) { count++; i-- }
        return count
    }

    private fun countWakeForward(arr: BooleanArray, fromInclusive: Int): Int {
        var count = 0
        var i = fromInclusive
        while (i < arr.size && arr[i]) { count++; i++ }
        return count
    }
}
