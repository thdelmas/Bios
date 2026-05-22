package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.UUID

/**
 * Owner-logged structured migraine attack record. Closes
 * [docs/audits/NEUROLOGY_POV.md] §2.6 (issue #207). The neurology, primary-
 * care, and headache-medicine audits all converge on the migraine-diary
 * shape — a dated record carrying onset / end / peak intensity / aura /
 * triggers / acute medication so the owner can read their own pattern over
 * time and the FHIR exporter can ship a structured record to a neurologist.
 *
 * Distinct from the more generic [HeadacheLog]: a [MigraineAttack] carries
 * migraine-specific structure (aura phase, the migraine trigger taxonomy,
 * the acute-treatment slot for triptan / NSAID). Owners who don't classify
 * their headaches as migraine use [HeadacheLog] instead — both surfaces are
 * available on the same Identity → "Headache & migraine diary" screen.
 *
 * Manifesto guards:
 *  - **Owner-administered, never inferred.** Bios never derives a migraine
 *    attack from biomarker data. The owner reports; Bios stores.
 *  - **Pull-side only.** No notification fires on a recorded attack; no
 *    reminder fires to fill one in. The owner records on their own terms.
 *  - **Stays on-device.** Lives in the main encrypted DB; leaves only when
 *    the owner explicitly invokes the FHIR exporter.
 *
 * IHS ICHD-3 (International Classification of Headache Disorders, 3rd ed.)
 * defines migraine without aura (§1.1) and migraine with aura (§1.2) by
 * duration (4–72h untreated), unilateral pulsating quality, moderate-to-
 * severe intensity, and aggravation by physical activity. The diary shape
 * carries the fields that map to those clinical criteria so an owner who
 * shares this record with a neurologist has the substrate the diagnostic
 * conversation needs.
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
     * True when the owner experienced aura (visual disturbance, sensory
     * symptoms, speech changes) before or during the attack — IHS ICHD-3
     * §1.2 distinguishes migraine with vs. without aura.
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
     * "sumatriptan 50mg", "ibuprofen 600mg", "rizatriptan"). Used by the
     * [com.bios.app.alerts.MedicationOveruseHeadacheEvaluator] to count
     * acute-headache-treatment days for the IHS ICHD-3 §8.2 MOH screen.
     * `null` means no acute treatment was taken.
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
