package com.bios.app.data

import com.bios.app.data.dao.EsasReportDao
import com.bios.app.model.EsasReport

/**
 * Thin repository wrapper over [EsasReportDao] for the ESAS-r capture
 * screen and the trend view (issue #185). Closes audit gap §2.8.
 *
 * Stores in the main encrypted DB ([BiosDatabase]), **not** the
 * reproductive DB. ESAS-r covers general symptom burden — pain,
 * tiredness, nausea, anxiety, depression, wellbeing — and the panel
 * has agreed it is the right shape for the main store.
 *
 * Validates each item is in `0..10` at the insert boundary; UI sliders
 * also pin to that range. The split-validation is intentional: a
 * direct DAO call from a test or a future engine path still gets the
 * range check at the repo boundary.
 */
class EsasReportRepo(private val db: BiosDatabase) {

    private val dao get() = db.esasReportDao()

    /**
     * Records a new ESAS-r report.
     *
     * @param timestamp epoch millis (defaults to now)
     * @param painScore 0-10
     * @param tirednessScore 0-10
     * @param drowsinessScore 0-10
     * @param nauseaScore 0-10
     * @param appetiteScore 0-10 (lack of appetite — higher = worse appetite)
     * @param shortnessOfBreathScore 0-10
     * @param depressionScore 0-10
     * @param anxietyScore 0-10
     * @param wellbeingScore 0-10 (0 = best, 10 = worst — ESAS-r convention)
     * @param otherSymptomLabel optional free-text label for the owner-named
     *   "other" symptom; pairs with [otherSymptomScore]
     * @param otherSymptomScore optional 0-10 for the "other" slot
     * @param note optional owner free-text
     *
     * @return the new row id
     */
    suspend fun add(
        timestamp: Long = System.currentTimeMillis(),
        painScore: Int,
        tirednessScore: Int,
        drowsinessScore: Int,
        nauseaScore: Int,
        appetiteScore: Int,
        shortnessOfBreathScore: Int,
        depressionScore: Int,
        anxietyScore: Int,
        wellbeingScore: Int,
        otherSymptomLabel: String? = null,
        otherSymptomScore: Int? = null,
        note: String? = null,
    ): Long {
        EsasReport.validateScore("painScore", painScore)
        EsasReport.validateScore("tirednessScore", tirednessScore)
        EsasReport.validateScore("drowsinessScore", drowsinessScore)
        EsasReport.validateScore("nauseaScore", nauseaScore)
        EsasReport.validateScore("appetiteScore", appetiteScore)
        EsasReport.validateScore("shortnessOfBreathScore", shortnessOfBreathScore)
        EsasReport.validateScore("depressionScore", depressionScore)
        EsasReport.validateScore("anxietyScore", anxietyScore)
        EsasReport.validateScore("wellbeingScore", wellbeingScore)
        // Pair the "other" label and score — either both or neither.
        val trimmedLabel = otherSymptomLabel?.trim()?.takeIf { it.isNotBlank() }
        val pairedScore = if (trimmedLabel != null) {
            requireNotNull(otherSymptomScore) {
                "otherSymptomScore must be provided when otherSymptomLabel is set"
            }
            EsasReport.validateScore("otherSymptomScore", otherSymptomScore)
            otherSymptomScore
        } else null

        return dao.insert(
            EsasReport(
                timestamp = timestamp,
                painScore = painScore,
                tirednessScore = tirednessScore,
                drowsinessScore = drowsinessScore,
                nauseaScore = nauseaScore,
                appetiteScore = appetiteScore,
                shortnessOfBreathScore = shortnessOfBreathScore,
                depressionScore = depressionScore,
                anxietyScore = anxietyScore,
                wellbeingScore = wellbeingScore,
                otherSymptomLabel = trimmedLabel,
                otherSymptomScore = pairedScore,
                note = note?.trim()?.takeIf { it.isNotBlank() },
            )
        )
    }

    suspend fun remove(id: Long) = dao.deleteById(id)

    suspend fun fetchAll(): List<EsasReport> = dao.fetchAll()

    /**
     * Reports recorded in `[fromMillis, toMillis]`, newest first.
     * Both bounds inclusive.
     */
    suspend fun fetchInRange(fromMillis: Long, toMillis: Long): List<EsasReport> =
        dao.fetchInRange(fromMillis, toMillis)
}
