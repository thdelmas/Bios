package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Owner-set menopause stage per STRAW+10 staging (issue #209).
 *
 * Staging follows Harlow SD et al. (2012) "Executive summary of the
 * Stages of Reproductive Aging Workshop + 10: addressing the unfinished
 * agenda of staging reproductive aging," J Clin Endocrinol Metab 97(4):
 * 1159–1168. STRAW+10 is the international reference standard; ACOG and
 * NAMS both anchor their menopause guidance on it.
 *
 * Bios collapses STRAW's seven stages into the four the owner can
 * realistically self-identify without bloodwork:
 *
 * - **PREMENOPAUSE** — regular cycles, no perimenopausal symptoms (STRAW -5/-4/-3)
 * - **PERIMENOPAUSE_EARLY** — variable cycle length (STRAW -2 "early menopausal transition")
 * - **PERIMENOPAUSE_LATE** — gaps ≥60 days between periods (STRAW -1 "late menopausal transition")
 * - **POSTMENOPAUSE** — ≥12 months since last period (STRAW +1/+2)
 *
 * Why this matters for the engine:
 *
 * - The `menstrual_cycle_anomaly` pattern's central premise — that the
 *   owner's cycle has an *established* baseline — breaks during peri.
 *   Variable cycle length IS the perimenopausal physiology; flagging it
 *   as anomaly is exactly the kind of normative-misread the audit warns
 *   against.
 * - Vasomotor symptoms (hot flashes / night sweats) skew nighttime skin
 *   temperature and HR; sleep architecture shifts; HRV declines. These
 *   are perimenopausal physiology, not pathology.
 *
 * Manifesto guard: STRAW+10 is the medical reference, but the *owner*
 * stages themselves. Bios does not infer perimenopause from cycle gaps
 * or BBT pattern loss.
 */
enum class MenopauseStage(val displayName: String) {
    PREMENOPAUSE("Premenopause — regular cycles"),
    PERIMENOPAUSE_EARLY("Perimenopause — early (variable cycle length)"),
    PERIMENOPAUSE_LATE("Perimenopause — late (≥60-day gaps)"),
    POSTMENOPAUSE("Postmenopause (≥12 months since last period)"),
}

/**
 * One owner-recorded menopause-stage transition. History-preserving
 * (multiple rows allowed) so earlier readings can be interpreted in the
 * stage the owner was in at the time — perimenopause can last years and
 * the owner's stage matters when reviewing past data.
 *
 * Lives in [com.bios.app.data.ReproductiveDatabase] for the same
 * threat-model reasons as cycle data.
 */
@Entity(
    tableName = "menopause_stage_entries",
    indices = [Index("recordedDate")],
)
data class MenopauseStageEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** [MenopauseStage.name] of the recorded stage. */
    val stage: String,
    /** Epoch millis when the owner recorded this stage. */
    val recordedDate: Long,
    /** Owner-recall free text (symptoms, treatments). Engines do not read. */
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
