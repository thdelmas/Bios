package com.bios.app.physiology

import android.content.Context

/**
 * Owner-declared [OwnerCondition] set persisted in shared-prefs.
 *
 * Mirrors [PhysiologyStateStore] in shape (lightweight prefs, no Room
 * migration); the difference is that this surface is a *set* rather
 * than a single slot — the owner can hold multiple known conditions
 * simultaneously. Default is the empty set.
 *
 * Manifesto guard: pull-side only. Adding a condition never pushes a
 * notification. The owner navigates in, the owner edits. Removing a
 * condition is instant and irreversible from Bios's side (no clinical
 * confirmation required, no waiting period).
 */
class OwnerConditionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "bios_owner_conditions", Context.MODE_PRIVATE,
    )

    fun current(): Set<OwnerCondition> {
        val raw = prefs.getStringSet(KEY_CONDITIONS, emptySet()) ?: emptySet()
        return raw.mapNotNull { name ->
            runCatching { OwnerCondition.valueOf(name) }.getOrNull()
        }.toSet()
    }

    fun set(conditions: Set<OwnerCondition>) {
        prefs.edit().putStringSet(KEY_CONDITIONS, conditions.map { it.name }.toSet()).apply()
    }

    fun add(condition: OwnerCondition) {
        set(current() + condition)
    }

    fun remove(condition: OwnerCondition) {
        set(current() - condition)
    }

    fun clear() {
        prefs.edit().remove(KEY_CONDITIONS).apply()
    }

    private companion object {
        const val KEY_CONDITIONS = "conditions"
    }
}
