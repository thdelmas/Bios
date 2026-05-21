package com.bios.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Owner-recorded personal and family-history risk factors (#160, audit
 * gap §2.6). Calibrated primary-care risk scores (ASCVD-pooled-cohort,
 * FRAX, Gail, QRISK3) all take first-degree-relative history as input;
 * Bios had nowhere to store it before this entity.
 *
 * **Single-row design.** Always `id = SINGLETON_ID`; the repo upserts.
 * This is one owner's profile, not a history table — every save replaces
 * the previous row. Owner-edits track `updatedAt` for surface-side
 * "last updated N days ago" rendering.
 *
 * **Booleans only for the well-defined ASCVD-relevant family-history
 * questions**; longer-context fields (personal cancer / cardiac event
 * history) stay free-text so the owner can record what's relevant
 * without Bios imposing a taxonomy. The screening-cadence engine (#155)
 * and the future pattern-explanation builder read this surface.
 *
 * Manifesto guard: pull-side only. Setting these flags never pushes a
 * notification. The owner navigates in, the owner edits.
 */
@Entity(tableName = "risk_profile")
data class RiskProfile(
    @PrimaryKey val id: Long = SINGLETON_ID,
    /** First-degree relative with CAD <55 (♂) or <65 (♀) — strongest ASCVD risk multiplier. */
    val firstDegreeCadEarly: Boolean = false,
    /** First-degree breast or ovarian cancer — Gail / IBIS / Tyrer-Cuzick input. */
    val firstDegreeBreastOvarianCancer: Boolean = false,
    /** First-degree colorectal cancer — USPSTF colorectal-screening age shifts from 45 → 40. */
    val firstDegreeColorectalCancer: Boolean = false,
    /** First-degree type 2 diabetes — USPSTF HbA1c-cadence modifier. */
    val firstDegreeDiabetes: Boolean = false,
    /** First-degree osteoporosis or hip fracture — FRAX input. */
    val firstDegreeOsteoporosisHipFracture: Boolean = false,
    /** First-degree melanoma — skin-screening cadence input. */
    val firstDegreeMelanoma: Boolean = false,
    /** Owner's smoking duration in years; null = unknown / not applicable. */
    val personalTobaccoYears: Int? = null,
    /** Owner's pack-years (ASCVD + lung-cancer-screening input); null = unknown. */
    val personalTobaccoPackYears: Int? = null,
    /** Epoch millis when the owner quit; null = never quit / not applicable. */
    val personalTobaccoQuitDate: Long? = null,
    /** Owner's personal cancer history — free text. */
    val personalCancerHistory: String? = null,
    /** Owner's personal cardiac event history — free text. */
    val personalCardiacEventHistory: String? = null,
    /** Owner-set comment or extra context — free text. */
    val note: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /**
         * Stable primary key for the single owner-profile row. The repo
         * upserts on this id so save() always replaces the previous row.
         */
        const val SINGLETON_ID: Long = 1L
    }
}
