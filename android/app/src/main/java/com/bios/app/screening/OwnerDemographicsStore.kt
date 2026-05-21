package com.bios.app.screening

import android.content.Context

/**
 * Owner-set demographics needed by the cadence engine (#155). Stored in
 * encrypted shared-prefs — these aren't health readings, but birth year
 * and anatomy-presentation are dignity-class data and shouldn't be
 * sniffable by other apps with shared-prefs read.
 *
 * Tiny surface: birth year (more stable than age) and an
 * [Applicability]-domain anatomy presentation flag. The engine derives
 * current age from birth year against the system clock. Owner can clear
 * either to opt out of demographic gating — when both are null, the
 * cadence screen renders a one-time prompt asking the owner to set them
 * (no push, just the in-screen surface).
 */
class OwnerDemographicsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "bios_screening_demographics", Context.MODE_PRIVATE,
    )

    fun setBirthYear(year: Int?) {
        prefs.edit().apply {
            if (year == null) remove(KEY_BIRTH_YEAR) else putInt(KEY_BIRTH_YEAR, year)
            apply()
        }
    }

    fun birthYear(): Int? =
        if (prefs.contains(KEY_BIRTH_YEAR)) prefs.getInt(KEY_BIRTH_YEAR, -1).takeIf { it > 0 } else null

    fun setPresentsAs(applicability: Applicability?) {
        prefs.edit().apply {
            if (applicability == null) remove(KEY_PRESENTS_AS)
            else putString(KEY_PRESENTS_AS, applicability.name)
            apply()
        }
    }

    fun presentsAs(): Applicability? =
        prefs.getString(KEY_PRESENTS_AS, null)?.let {
            runCatching { Applicability.valueOf(it) }.getOrNull()
        }

    /**
     * Builds an [OwnerDemographics] from stored values, deriving age from
     * birth year against [nowYear]. Returns `null` when no birth year is
     * on record — the cadence engine treats that as "no demographics yet."
     */
    fun load(nowYear: Int): OwnerDemographics? {
        val year = birthYear() ?: return null
        return OwnerDemographics(
            ageYears = (nowYear - year).coerceAtLeast(0),
            presentsAs = presentsAs(),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_BIRTH_YEAR = "birth_year"
        const val KEY_PRESENTS_AS = "presents_as"
    }
}
