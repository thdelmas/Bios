package com.bios.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Owner-declared metadata about advance-directive documents (#184).
 *
 * Note: this entity captures **metadata about** documents — not the
 * documents themselves. The authoritative paper or electronic
 * directives live with the owner's clinical team or in the owner's
 * personal records. Bios stores enough information to surface the
 * existence of those documents when a healthcare-proxy contact is
 * needed, and to remind the owner to keep them current.
 *
 * Companion to [GoalsOfCare] — where GoalsOfCare captures *what* the
 * owner wants, ClinicalDirective captures *which legal instruments*
 * have been completed and *who* can speak for the owner if they
 * cannot speak for themselves.
 *
 * **Reference framework:** Aligns with the POLST / MOLST (Physician /
 * Medical Orders for Life-Sustaining Treatment) and Five Wishes /
 * advance-directive frameworks recognised across US states; concept
 * carries over to "Anticipatory Care Planning" (UK), "directives
 * anticipées" (FR), and equivalents internationally. Bios is
 * jurisdiction-agnostic — the entity records the owner's own answer,
 * not a verified legal status.
 *
 * **Single-row design.** Always `id = SINGLETON_ID`; the repo upserts.
 *
 * Manifesto guard: pull-side only. Bios never asks unprompted whether
 * the owner has an advance directive, and never weighs this surface
 * into push notifications. The owner records what they have.
 */
@Entity(tableName = "clinical_directive")
data class ClinicalDirective(
    @PrimaryKey val id: Long = SINGLETON_ID,
    /** Owner has a written advance directive (living will or equivalent). */
    val hasAdvanceDirective: Boolean = false,
    /** Owner has a POLST / MOLST or jurisdiction-equivalent portable medical order. */
    val hasPolst: Boolean = false,
    /** Owner has a designated healthcare proxy / surrogate decision-maker. */
    val hasHealthcareProxy: Boolean = false,
    /** Optional name of the designated healthcare proxy. Free text. */
    val proxyContactName: String? = null,
    /**
     * Optional phone number of the designated healthcare proxy.
     * Stored as free text — Bios does not auto-dial this number.
     */
    val proxyContactPhone: String? = null,
    /** Epoch millis when the owner last reviewed this declaration. */
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /** Stable primary key for the single owner-directive row. */
        const val SINGLETON_ID: Long = 1L
    }
}
