package com.bios.app

import com.bios.app.ingest.GarminApiAdapter
import com.bios.app.model.ExerciseModality
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-Kotlin tests for [GarminApiAdapter]'s static surface — activity-type
 * → modality mapping plus a couple of contract stability checks. Network
 * calls aren't tested here; live JSON parsing is covered by the
 * IngestManager integration once a real Garmin token is in play.
 */
class GarminApiAdapterTest {

    @Test
    fun `mapGarminActivityToModality buckets cardio types`() {
        for (type in listOf("RUNNING", "TREADMILL_RUNNING", "WALKING", "HIKING",
            "CYCLING", "INDOOR_CYCLING", "ELLIPTICAL", "ROWING", "LAP_SWIMMING",
            "STAIR_CLIMBING")) {
            assertEquals(
                "activityType '$type' should map to CARDIO",
                ExerciseModality.CARDIO,
                GarminApiAdapter.mapGarminActivityToModality(type)
            )
        }
    }

    @Test
    fun `mapGarminActivityToModality buckets strength types`() {
        for (type in listOf("STRENGTH_TRAINING", "WEIGHTLIFTING",
            "BODY_WEIGHT", "CALISTHENICS")) {
            assertEquals(
                ExerciseModality.STRENGTH,
                GarminApiAdapter.mapGarminActivityToModality(type)
            )
        }
    }

    @Test
    fun `mapGarminActivityToModality buckets HIIT and cross-training as INTERVAL`() {
        assertEquals(
            ExerciseModality.INTERVAL,
            GarminApiAdapter.mapGarminActivityToModality("HIIT")
        )
        assertEquals(
            ExerciseModality.INTERVAL,
            GarminApiAdapter.mapGarminActivityToModality("INTERVAL_TRAINING")
        )
        assertEquals(
            ExerciseModality.INTERVAL,
            GarminApiAdapter.mapGarminActivityToModality("CROSS_TRAINING")
        )
    }

    @Test
    fun `mapGarminActivityToModality buckets yoga and pilates as MOBILITY`() {
        for (type in listOf("YOGA", "PILATES", "STRETCHING", "MEDITATION")) {
            assertEquals(
                ExerciseModality.MOBILITY,
                GarminApiAdapter.mapGarminActivityToModality(type)
            )
        }
    }

    @Test
    fun `mapGarminActivityToModality is case insensitive`() {
        assertEquals(
            ExerciseModality.CARDIO,
            GarminApiAdapter.mapGarminActivityToModality("running")
        )
        assertEquals(
            ExerciseModality.STRENGTH,
            GarminApiAdapter.mapGarminActivityToModality("Strength_Training")
        )
    }

    @Test
    fun `mapGarminActivityToModality falls back to OTHER for blank or unknown`() {
        assertEquals(
            ExerciseModality.OTHER,
            GarminApiAdapter.mapGarminActivityToModality(null)
        )
        assertEquals(
            ExerciseModality.OTHER,
            GarminApiAdapter.mapGarminActivityToModality("")
        )
        assertEquals(
            ExerciseModality.OTHER,
            GarminApiAdapter.mapGarminActivityToModality("ALIEN_BASKETBALL")
        )
    }

    @Test
    fun `BASE_URL points to the Garmin Wellness API`() {
        assertEquals("https://apis.garmin.com/wellness-api/rest", GarminApiAdapter.BASE_URL)
    }

    @Test
    fun `PROVIDER_KEY is garmin`() {
        // ApiTokenStore keys this string into EncryptedSharedPreferences;
        // renaming would orphan existing tokens after upgrade.
        assertEquals("garmin", GarminApiAdapter.PROVIDER_KEY)
    }
}
