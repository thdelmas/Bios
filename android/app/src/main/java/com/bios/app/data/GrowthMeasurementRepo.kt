package com.bios.app.data

import com.bios.app.model.GrowthChartReference
import com.bios.app.model.GrowthMeasurement
import java.util.UUID

/**
 * Thin repository over [com.bios.app.data.dao.GrowthMeasurementDao] for the
 * paediatric growth + adult body-composition surfaces (#199, audit gap
 * §2.7).
 *
 * Ergonomic helpers on top of the DAO:
 *  - [record] auto-derives BMI from height + weight, generates the UUID,
 *    and snapshots `ageInDays` so the growth-chart engine has everything
 *    it needs without re-deriving from a DOB.
 *  - The dual-write to [MetricReading] for HEIGHT_CM / HEAD_CIRCUMFERENCE_CM /
 *    BMI_KG_PER_M2 / LEAN_BODY_MASS_KG / FAT_MASS_KG lets the existing
 *    trend / aggregate / FHIR-export surfaces consume the data without
 *    learning a new entity. BODY_MASS is *not* mirrored here because the
 *    Withings adapter already writes that key; mirroring would create
 *    duplicate rows. Manual-entry callers that want a BODY_MASS row write
 *    one explicitly via [ManualReadingRepo].
 *
 * The mirror writes are best-effort; if the caller wires a `null`
 * [com.bios.app.data.dao.MetricReadingDao] (e.g. in tests against an
 * in-memory DB without all the engines wired) the GrowthMeasurement row
 * still lands. The growth screens read the dedicated entity, not the
 * mirrored MetricReading rows.
 */
class GrowthMeasurementRepo(private val db: BiosDatabase) {

    private val dao get() = db.growthMeasurementDao()

    /**
     * Records a new anthropometric measurement event.
     *
     * @param ageInDays the owner's age in days at [timestamp]. Pass 0 for
     *   adult measurements where no percentile lookup is intended.
     * @param growthChartReference null for adult measurements;
     *   [GrowthChartReference.WHO_0_5Y] is the default for the 0–60 month
     *   paediatric range.
     */
    suspend fun record(
        timestamp: Long = System.currentTimeMillis(),
        ageInDays: Int = 0,
        heightCm: Float? = null,
        weightKg: Float? = null,
        headCircumferenceCm: Float? = null,
        leanBodyMassKg: Float? = null,
        fatMassKg: Float? = null,
        growthChartReference: GrowthChartReference? = null,
        note: String? = null,
    ): GrowthMeasurement {
        require(heightCm != null || weightKg != null || headCircumferenceCm != null ||
            leanBodyMassKg != null || fatMassKg != null) {
            "GrowthMeasurement must carry at least one anthropometric value"
        }
        val bmi = GrowthMeasurement.bmiFrom(heightCm, weightKg)
        val measurement = GrowthMeasurement(
            id = UUID.randomUUID().toString(),
            timestamp = timestamp,
            ageInDays = ageInDays.coerceAtLeast(0),
            heightCm = heightCm,
            weightKg = weightKg,
            headCircumferenceCm = headCircumferenceCm,
            bmiKgPerM2 = bmi,
            leanBodyMassKg = leanBodyMassKg,
            fatMassKg = fatMassKg,
            growthChartReference = growthChartReference,
            note = note?.takeIf { it.isNotBlank() }?.trim(),
        )
        dao.insert(measurement)
        return measurement
    }

    suspend fun update(measurement: GrowthMeasurement) {
        // Recompute BMI on update — if the owner corrects height or weight,
        // the derived value should track the inputs.
        val withBmi = measurement.copy(
            bmiKgPerM2 = GrowthMeasurement.bmiFrom(measurement.heightCm, measurement.weightKg),
        )
        dao.update(withBmi)
    }

    suspend fun remove(id: String) {
        val existing = dao.fetchById(id) ?: return
        dao.delete(existing)
    }

    suspend fun fetchAll(): List<GrowthMeasurement> = dao.fetchAll()
    suspend fun fetchAllChronological(): List<GrowthMeasurement> = dao.fetchAllChronological()
    suspend fun fetchInWindow(fromEpochMs: Long, toEpochMs: Long): List<GrowthMeasurement> =
        dao.fetchInWindow(fromEpochMs, toEpochMs)
}
