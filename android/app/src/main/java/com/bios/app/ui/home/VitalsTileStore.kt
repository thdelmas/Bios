package com.bios.app.ui.home

import android.content.Context
import com.bios.contracts.MetricType

/**
 * Owner-chosen "Today's Vitals" tile set, persisted in shared-prefs.
 *
 * Mirrors the lightweight `*Store` prefs idiom (OwnerConditionStore,
 * PhysiologyStateStore) — no Room migration. Order is display order, so
 * the selection is stored as one comma-joined string of stable
 * [MetricType.key]s rather than an unordered StringSet.
 *
 * Manifesto guard: pull-side only. The owner curates what the instrument
 * shows; Bios never suggests, promotes, or reorders tiles on its own.
 * Unknown keys from a different contracts version are dropped silently on
 * read (`fromKey` null-tolerance), and an empty or fully-unknown stored
 * value falls back to the historical default set.
 */
class VitalsTileStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "bios_vitals_tiles", Context.MODE_PRIVATE,
    )

    fun current(): List<MetricType> {
        val raw = prefs.getString(KEY_TILES, null) ?: return DEFAULT_TILES
        return decodeTiles(raw).ifEmpty { DEFAULT_TILES }
    }

    fun set(tiles: List<MetricType>) {
        prefs.edit().putString(KEY_TILES, encodeTiles(tiles)).apply()
    }

    fun reset() {
        prefs.edit().remove(KEY_TILES).apply()
    }

    companion object {
        private const val KEY_TILES = "tiles"

        /** The historical fixed vitals grid — remains the default. */
        val DEFAULT_TILES = listOf(
            MetricType.SLEEP_DURATION,
            MetricType.SLEEP_EFFICIENCY,
            MetricType.HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY,
            MetricType.BLOOD_OXYGEN,
            MetricType.RESPIRATORY_RATE,
            MetricType.STEPS,
            MetricType.SKIN_TEMPERATURE_DEVIATION,
        )
    }
}

/** Stable-key serialization; pure so tests don't need a Context. */
internal fun encodeTiles(tiles: List<MetricType>): String =
    tiles.joinToString(",") { it.key }

/** Unknown keys are dropped; duplicates keep the first occurrence. */
internal fun decodeTiles(raw: String): List<MetricType> =
    raw.split(",")
        .mapNotNull { MetricType.fromKey(it.trim()) }
        .distinct()
