package com.bios.app.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bios.app.data.BiosDatabase
import com.bios.app.data.HeadacheEventWriter
import com.bios.app.data.HeadacheLogRepo
import com.bios.app.data.MigraineAttackRepo
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import java.util.concurrent.TimeUnit

/**
 * Daily worker that runs the MOH screening evaluator over the rolling
 * diary window (#276 / #207).
 *
 * The MOH `ConditionPattern` ([HeadachePatterns.medicationOveruseHeadacheScreen])
 * is intentionally not in the standard signal-rule pipeline — its
 * inputs are diary rows, not metric streams ([HeadachePatterns.kt:99-119]).
 * Until this worker, the pattern was registered for text + content-policy
 * compliance but never fired in production. The worker is the missing
 * wiring: fetch → [MedicationOveruseHeadacheEvaluator.evaluate] →
 * insert an [Anomaly] only on a true-verdict transition or post-ack
 * re-fire.
 *
 * ## Dedup
 *
 * Verdict state is persisted to SharedPreferences across worker
 * invocations. The worker fires (writes an Anomaly) only when:
 *
 *   1. The verdict is `meetsScreeningThreshold = true`, **and**
 *   2. Either:
 *      - the previously-stored verdict was `false` (false→true
 *        transition), **or**
 *      - the previously-stored verdict was `true` and the previously-
 *        written Anomaly has been acknowledged by the owner (re-surface
 *        after ack).
 *
 * The verdict-state flip and the last-Anomaly-id pointer are updated
 * on every tick. A false verdict resets the stored state so the next
 * true verdict counts as a fresh transition.
 *
 * ## Cadence
 *
 * Daily. The IHS ICHD-3 §8.2 screen is a chronic-pattern metric — a
 * day's worth of diary entries won't change the 3-month verdict in any
 * clinically interesting way, and a more frequent cadence would burn
 * battery without earning information. Mirrors [DailyDigestWorker]
 * scheduling shape.
 */
class MohScreeningWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = BiosDatabase.getInstance(context)
        val migraineRepo = MigraineAttackRepo(db)
        val headacheRepo = HeadacheLogRepo(db)

        val now = System.currentTimeMillis()
        val windowStart = now - HeadachePatterns.MOH_WINDOW_DAYS * MS_PER_DAY
        val attacks = migraineRepo.fetchInRange(windowStart, now)
        val logs = headacheRepo.fetchInRange(windowStart, now)
        // #283 Cut 3 follow-up: structured MEDICATION_INTAKE rows the
        // HeadacheEventWriter mirror produces. Filter to rows linked
        // to a parent headache event so non-headache intake (future
        // supplements / caffeine / etc.) doesn't count toward MOH.
        val headacheLinkedIntakes = fetchHeadacheLinkedIntakes(db, windowStart, now)

        val verdict = MedicationOveruseHeadacheEvaluator.evaluate(
            migraineAttacks = attacks,
            headacheLogs = logs,
            headacheLinkedIntakes = headacheLinkedIntakes,
            nowMillis = now,
        )

        val decision = decideFireOrSkip(
            verdict = verdict,
            previousState = readPreviousState(context),
            previouslyAcked = wasPreviouslyAcked(context, db),
        )
        when (decision) {
            FireDecision.SuppressVerdictFalse -> {
                writePreviousState(context, verdictTrue = false, lastAnomalyId = null)
            }
            FireDecision.SuppressVerdictUnchanged -> {
                // Keep the existing state intact — the previously-fired
                // Anomaly is still the active surface.
            }
            FireDecision.Fire -> {
                val anomaly = buildAnomaly(verdict, now)
                db.anomalyDao().insert(anomaly)
                writePreviousState(context, verdictTrue = true, lastAnomalyId = anomaly.id)
            }
        }
        return Result.success()
    }

    /**
     * Read MEDICATION_INTAKE rows in the window that carry the
     * [HeadacheEventWriter.PARENT_HEADACHE_EVENT_FIELD] payload
     * back-link. The back-link is the unambiguous "this intake was
     * for a headache event" signal that lets us count toward MOH
     * without sweeping up unrelated dosed-intake rows.
     */
    private suspend fun fetchHeadacheLinkedIntakes(
        db: BiosDatabase,
        startMs: Long,
        endMs: Long,
    ): List<MetricReading> {
        val intakeRows = db.metricReadingDao()
            .fetch(MetricType.MEDICATION_INTAKE.key, startMs, endMs)
        if (intakeRows.isEmpty()) return emptyList()
        val payloads = db.eventPayloadFieldDao()
            .fetchForReadings(intakeRows.map { it.id })
            .groupBy { it.readingId }
        return intakeRows.filter { row ->
            payloads[row.id]?.any {
                it.fieldKey == HeadacheEventWriter.PARENT_HEADACHE_EVENT_FIELD
            } == true
        }
    }

    companion object {
        const val WORK_NAME = "bios_moh_screening"
        private const val MS_PER_DAY: Long = 24L * 60L * 60L * 1000L

        // SharedPreferences keys.
        private const val PREFS_NAME = "bios_moh_state"
        private const val KEY_LAST_VERDICT_TRUE = "last_verdict_true"
        private const val KEY_LAST_ANOMALY_ID = "last_anomaly_id"

        /** Daily cadence. Idempotent via WorkManager unique-work policy. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MohScreeningWorker>(
                24, TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        internal data class PreviousState(
            val verdictTrue: Boolean,
            val lastAnomalyId: String?,
        )

        internal sealed interface FireDecision {
            object Fire : FireDecision
            object SuppressVerdictFalse : FireDecision
            object SuppressVerdictUnchanged : FireDecision
        }

        /**
         * Pure dedup logic, extracted from [doWork] so the firing
         * transitions are directly unit-testable without a worker
         * scaffold.
         */
        internal fun decideFireOrSkip(
            verdict: MedicationOveruseHeadacheEvaluator.Verdict,
            previousState: PreviousState,
            previouslyAcked: Boolean,
        ): FireDecision {
            if (!verdict.meetsScreeningThreshold) return FireDecision.SuppressVerdictFalse
            // Verdict is true. Fire only on a transition or after the
            // previous Anomaly has been acknowledged by the owner.
            if (!previousState.verdictTrue) return FireDecision.Fire
            if (previouslyAcked) return FireDecision.Fire
            return FireDecision.SuppressVerdictUnchanged
        }

        /**
         * Build the [Anomaly] row carried by a fired MOH screen. The
         * pattern text comes straight from
         * [HeadachePatterns.medicationOveruseHeadacheScreen]; the per-
         * month substrate from [MedicationOveruseHeadacheEvaluator.describeVerdict]
         * lands appended to [Anomaly.explanation] so the timeline /
         * detail surface can render both the static pattern text and
         * the actual per-month counts that triggered the fire.
         */
        internal fun buildAnomaly(
            verdict: MedicationOveruseHeadacheEvaluator.Verdict,
            nowMillis: Long,
        ): Anomaly {
            val pattern = HeadachePatterns.medicationOveruseHeadacheScreen
            val substrate = MedicationOveruseHeadacheEvaluator.describeVerdict(verdict)
            return Anomaly(
                detectedAt = nowMillis,
                metricTypes = "[]",
                deviationScores = "{}",
                // Threshold-met is a binary verdict — no probability score
                // to surface. 1.0 keeps the column populated without
                // implying a calibrated certainty.
                combinedScore = 1.0,
                patternId = pattern.id,
                severity = AlertTier.ADVISORY.level,
                title = pattern.title,
                explanation = "${pattern.explanation}\n\n$substrate",
                suggestedAction = pattern.suggestedAction,
            )
        }

        private fun readPreviousState(context: Context): PreviousState {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return PreviousState(
                verdictTrue = prefs.getBoolean(KEY_LAST_VERDICT_TRUE, false),
                lastAnomalyId = prefs.getString(KEY_LAST_ANOMALY_ID, null),
            )
        }

        private fun writePreviousState(
            context: Context,
            verdictTrue: Boolean,
            lastAnomalyId: String?,
        ) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LAST_VERDICT_TRUE, verdictTrue)
                .apply {
                    if (lastAnomalyId == null) remove(KEY_LAST_ANOMALY_ID)
                    else putString(KEY_LAST_ANOMALY_ID, lastAnomalyId)
                }
                .apply()
        }

        private suspend fun wasPreviouslyAcked(context: Context, db: BiosDatabase): Boolean {
            val state = readPreviousState(context)
            val id = state.lastAnomalyId ?: return false
            // No direct fetch-by-id on AnomalyDao; scan the recent window.
            // 50 is well above the per-day fire ceiling, so even an
            // owner with a dense alert history will still see their
            // last MOH fire here.
            return db.anomalyDao().fetchRecent(50).firstOrNull { it.id == id }?.acknowledged == true
        }
    }
}
