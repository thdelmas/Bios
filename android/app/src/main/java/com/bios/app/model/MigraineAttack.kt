package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.UUID

/**
 * Owner-logged migraine attack record (issue #207): onset, end, peak
 * intensity, aura, triggers and acute treatment, as the owner entered
 * them.
 *
 * Distinct from the more generic [HeadacheLog]: owners who don't call
 * their headaches migraine use [HeadacheLog] instead. Both are available
 * on the same headache diary screen.
 *
 *  - **Owner-entered, never inferred.** Bios never derives an attack from
 *    measured data. The owner reports; Bios stores.
 *  - **Pull-side only.** No notification fires on a recorded attack; no
 *    reminder fires to fill one in.
 *  - **Stays on-device.** Lives in the main encrypted DB; leaves only when
 *    the owner explicitly invokes an export.
 */
@Entity(
    tableName = "migraine_attacks",
    indices = [Index("onsetTimestamp")],
)
@TypeConverters(MigraineTriggerConverter::class)
data class MigraineAttack(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Epoch millis the owner records as the attack start. */
    val onsetTimestamp: Long,
    /**
     * Epoch millis the owner records as the attack end. `null` means the
     * attack is still ongoing — the diary surface presents an "in progress"
     * row that the owner closes when the migraine resolves.
     */
    val endTimestamp: Long? = null,
    /**
     * Peak headache intensity on the 0-10 numeric rating scale. Pinned at
     * the repo boundary; the UI slider also pins to 0-10.
     */
    val peakIntensity: Int,
    /**
     * True when the owner recorded aura before or during the attack.
     */
    val aura: Boolean = false,
    /**
     * Owner-identified triggers for this attack. A migraine attack
     * frequently has multiple triggers; the set lets the owner record
     * every one they notice. The taxonomy is the consensus migraine-
     * trigger list (Andress-Rothrock 2010, J Headache Pain; Pellegrino
     * 2018, Cephalalgia review).
     */
    val triggers: Set<MigraineTrigger> = emptySet(),
    /**
     * Free-text name of the acute treatment the owner took (e.g.
     * "sumatriptan 50mg", "ibuprofen 600mg", "rizatriptan"). Shown as
     * written. `null` means no acute treatment was taken.
     */
    val medicationTaken: String? = null,
    /** Optional owner free-text note. */
    val notes: String? = null,
) {
    companion object {
        const val MIN_INTENSITY = 0
        const val MAX_INTENSITY = 10

        /** Validates a 0-10 intensity; throws if out of range. */
        fun validateIntensity(intensity: Int) {
            require(intensity in MIN_INTENSITY..MAX_INTENSITY) {
                "Peak intensity must be in $MIN_INTENSITY..$MAX_INTENSITY (got $intensity)"
            }
        }
    }
}

/**
 * Owner-identified migraine trigger taxonomy. The set of well-attested
 * triggers in the headache-medicine literature (Andress-Rothrock 2010,
 * Pellegrino 2018) plus an [OTHER] slot for triggers the owner identifies
 * outside the canonical set.
 */
enum class MigraineTrigger {
    STRESS,
    SLEEP_DEPRIVATION,
    FOOD,
    HORMONAL,
    WEATHER,
    BRIGHT_LIGHT,
    OTHER,
}

/**
 * Room type converter for the [MigraineTrigger] set. Stored as a
 * comma-separated string of enum names; empty set is the empty string.
 * Survives enum-value renames at the converter layer — if a trigger
 * stored on disk no longer maps to a known [MigraineTrigger], it is
 * silently dropped from the in-memory set (defensive against forward
 * compatibility).
 */
class MigraineTriggerConverter {
    @TypeConverter
    fun fromSet(triggers: Set<MigraineTrigger>): String =
        triggers.joinToString(",") { it.name }

    @TypeConverter
    fun toSet(stored: String): Set<MigraineTrigger> {
        if (stored.isBlank()) return emptySet()
        return stored.split(",")
            .mapNotNull { name ->
                runCatching { MigraineTrigger.valueOf(name.trim()) }.getOrNull()
            }
            .toSet()
    }
}
