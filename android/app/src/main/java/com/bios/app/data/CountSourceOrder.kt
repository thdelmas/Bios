package com.bios.app.data

import android.content.Context
import com.bios.app.model.DataSource
import com.bios.app.model.SourceType

/**
 * The owner's preference order over the sources that can count a day
 * (steps, active calories, active minutes). One list of source keys; the
 * first key with readings on a calendar day owns that day and every other
 * source's readings that day are ignored (docs/specs/source-preference.md,
 * criteria 2 and 3).
 *
 * A key names a witness, not a transport: a Health Connect reading's key
 * is the app that wrote it (`health_connect:com.coros.coros`), so COROS,
 * Fitbit and the handset's own pedometer are three rows even though all
 * three arrive through Health Connect. Every other source keys on its
 * [SourceType].
 */
object CountSourceOrder {

    const val HEALTH_CONNECT_PREFIX = "health_connect:"

    /** Stable key of a registered source. */
    fun keyOf(source: DataSource): String =
        if (source.sourceType == SourceType.HEALTH_CONNECT.key && !source.deviceModel.isNullOrBlank()) {
            HEALTH_CONNECT_PREFIX + source.deviceModel
        } else {
            source.sourceType
        }

    /** Package names (prefixes) of apps that mirror a worn device into Health Connect. */
    private val WEARABLE_PACKAGES = listOf(
        "com.yf.smart.coros", "com.coros", "com.ouraring", "com.whoop", "com.garmin", "fi.polar", "com.withings",
        "com.sec.android.app.shealth", "com.samsung.android", "com.huami", "com.xiaomi.wearable",
        "com.huawei.health", "com.mobvoi", "nodomain.freeyourgadget.gadgetbridge",
    )

    private val VENDOR_API_TYPES = setOf(
        SourceType.COROS_API, SourceType.OURA_API, SourceType.WHOOP_API, SourceType.GARMIN_API,
        SourceType.POLAR_API, SourceType.WITHINGS_API,
    ).map { it.key }

    private const val RANK_SELF = 0
    private const val RANK_WEARABLE = 1
    private const val RANK_GADGETBRIDGE = 2
    private const val RANK_PHONE_APP = 3
    private const val RANK_DIRECT = 4
    private const val RANK_PHONE_SENSOR = 5
    private const val RANK_OTHER = 6

    /**
     * Default rank when the owner has set nothing (criterion 3): the owner's
     * own word first, worn devices next, phone-tracking apps and the phone's
     * sensors last. Lower is preferred.
     */
    fun defaultRank(key: String): Int = when {
        key == SourceType.SELF_REPORTED.key -> RANK_SELF
        key.startsWith(HEALTH_CONNECT_PREFIX) ->
            if (WEARABLE_PACKAGES.any { key.removePrefix(HEALTH_CONNECT_PREFIX).startsWith(it) }) RANK_WEARABLE
            else RANK_PHONE_APP
        key in VENDOR_API_TYPES -> RANK_WEARABLE
        key == SourceType.GADGETBRIDGE.key -> RANK_GADGETBRIDGE
        key == SourceType.HEALTH_CONNECT.key -> RANK_PHONE_APP
        key == SourceType.DIRECT_SENSOR.key -> RANK_DIRECT
        key == SourceType.PHONE_SENSOR.key -> RANK_PHONE_SENSOR
        else -> RANK_OTHER
    }

    /**
     * Full order over [keys]: the owner's [stored] keys first, in their
     * order, then every other key by [defaultRank] (ties alphabetical, so
     * the result is stable). Stored keys with no registered source are kept
     * so a source that reappears keeps its place.
     */
    fun effectiveOrder(stored: List<String>, keys: Collection<String>): List<String> {
        val rest = keys.filter { it !in stored }.sortedWith(compareBy({ defaultRank(it) }, { it }))
        return stored + rest
    }

    /** Position of [key] in [order]; unknown keys sort after everything. */
    fun rank(order: List<String>, key: String): Int =
        order.indexOf(key).let { if (it < 0) order.size + defaultRank(key) else it }

    /** [order] with the item at [index] moved one place up (`-1`) or down (`+1`). */
    fun moved(order: List<String>, index: Int, delta: Int): List<String> {
        val target = index + delta
        if (index !in order.indices || target !in order.indices) return order
        return order.toMutableList().also { it[index] = order[target]; it[target] = order[index] }
    }

    /** Owner-facing label: the source's device name, else its type. */
    fun labelOf(key: String, sources: List<DataSource>): String = when (key) {
        SourceType.SELF_REPORTED.key -> "Self-reported"
        SourceType.HEALTH_CONNECT.key -> "Health Connect, rows older than the split"
        else -> sources.firstOrNull { keyOf(it) == key }?.deviceName ?: key.removePrefix(HEALTH_CONNECT_PREFIX)
    }

    private const val PREFS = "bios_count_source_order"
    private const val KEY_ORDER = "order"
    private const val SEPARATOR = "|"

    /** The owner's stored order; empty when nothing was set. */
    fun load(context: Context): List<String> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ORDER, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun save(context: Context, order: List<String>) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ORDER, order.joinToString(SEPARATOR)).apply()
    }
}
