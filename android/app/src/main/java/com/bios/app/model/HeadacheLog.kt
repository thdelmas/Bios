package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Generic owner-logged headache diary entry (issue #207). Distinct from
 * [MigraineAttack]: the simpler shape, with a type the owner picks. Both
 * are available on the same headache diary screen; the owner picks the
 * entity that matches what they experienced.
 *
 * Same guards as [MigraineAttack]: owner-entered, pull-side only,
 * on-device. Bios never infers a headache from measured data and never
 * classifies an entry.
 */
@Entity(
    tableName = "headache_logs",
    indices = [Index("timestamp")],
)
data class HeadacheLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Epoch millis when the headache occurred / was logged. */
    val timestamp: Long,
    /** Headache intensity on the 0-10 numeric rating scale. */
    val intensity: Int,
    /**
     * The type the owner picked; [HeadacheType.OTHER] when unsure.
     */
    val type: HeadacheType,
    /**
     * Duration of the headache episode in minutes. `null` for ongoing /
     * unknown — the diary surface presents an open-ended entry and the
     * owner sets the duration on close.
     */
    val durationMinutes: Int? = null,
    /**
     * Free-text name of acute treatment taken (e.g. "paracetamol 1g",
     * "ibuprofen 400mg", "aspirin"). Same shape as
     * [MigraineAttack.medicationTaken]; shown as written.
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
                "Intensity must be in $MIN_INTENSITY..$MAX_INTENSITY (got $intensity)"
            }
        }
    }
}

/** Owner-selected headache type. Bios stores the choice and never assigns one. */
enum class HeadacheType {
    TENSION,

    /**
     * Owners who want the richer record (aura, triggers) use
     * [MigraineAttack] instead; this value is here for owners who log
     * every headache through this diary.
     */
    MIGRAINE,

    CLUSTER,

    /** Any other type, or the owner is unsure. */
    OTHER,
}
