package com.bios.app

import com.bios.app.ingest.WhoopApiAdapter
import com.bios.app.model.ExerciseModality
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-Kotlin tests for the WHOOP adapter's static surface — sport name +
 * sport_id mapping to [ExerciseModality]. Network calls aren't tested here;
 * the JSON parsing path is covered by the IngestManager integration once a
 * real WHOOP token is in play.
 */
class WhoopApiAdapterTest {

    @Test
    fun `mapWhoopSportToModality prefers sport_name over sport_id`() {
        // Even with a numeric id that would map to STRENGTH, a "running"
        // sport_name string should still resolve to CARDIO.
        assertEquals(
            ExerciseModality.CARDIO,
            WhoopApiAdapter.mapWhoopSportToModality(sportName = "running", sportId = 35)
        )
    }

    @Test
    fun `mapWhoopSportToModality buckets common cardio sport_names`() {
        for (name in listOf("running", "walking", "cycling", "swimming",
            "rowing", "elliptical", "hiking", "stair_climber")) {
            assertEquals(
                "sport_name '$name' should map to CARDIO",
                ExerciseModality.CARDIO,
                WhoopApiAdapter.mapWhoopSportToModality(sportName = name, sportId = null)
            )
        }
    }

    @Test
    fun `mapWhoopSportToModality buckets strength sport_names`() {
        for (name in listOf("weightlifting", "weight_lifting", "powerlifting",
            "strength_training", "calisthenics")) {
            assertEquals(
                ExerciseModality.STRENGTH,
                WhoopApiAdapter.mapWhoopSportToModality(sportName = name, sportId = null)
            )
        }
    }

    @Test
    fun `mapWhoopSportToModality buckets HIIT and CrossFit as INTERVAL`() {
        for (name in listOf("hiit", "crossfit", "tabata", "interval_training")) {
            assertEquals(
                ExerciseModality.INTERVAL,
                WhoopApiAdapter.mapWhoopSportToModality(sportName = name, sportId = null)
            )
        }
    }

    @Test
    fun `mapWhoopSportToModality buckets yoga and pilates as MOBILITY`() {
        for (name in listOf("yoga", "pilates", "stretching", "mobility")) {
            assertEquals(
                ExerciseModality.MOBILITY,
                WhoopApiAdapter.mapWhoopSportToModality(sportName = name, sportId = null)
            )
        }
    }

    @Test
    fun `mapWhoopSportToModality normalizes case and separators`() {
        assertEquals(
            ExerciseModality.CARDIO,
            WhoopApiAdapter.mapWhoopSportToModality(sportName = "TRAIL-RUNNING", sportId = null)
        )
        assertEquals(
            ExerciseModality.STRENGTH,
            WhoopApiAdapter.mapWhoopSportToModality(sportName = "Weight Lifting", sportId = null)
        )
    }

    @Test
    fun `mapWhoopSportToModality falls back to sport_id when name is null`() {
        assertEquals(
            ExerciseModality.CARDIO,
            WhoopApiAdapter.mapWhoopSportToModality(sportName = null, sportId = 0)
        )
        assertEquals(
            ExerciseModality.STRENGTH,
            WhoopApiAdapter.mapWhoopSportToModality(sportName = null, sportId = 35)
        )
        assertEquals(
            ExerciseModality.MOBILITY,
            WhoopApiAdapter.mapWhoopSportToModality(sportName = null, sportId = 36)
        )
        assertEquals(
            ExerciseModality.INTERVAL,
            WhoopApiAdapter.mapWhoopSportToModality(sportName = null, sportId = 39)
        )
    }

    @Test
    fun `mapWhoopSportToModality falls back to OTHER for blank name and unknown id`() {
        assertEquals(
            ExerciseModality.OTHER,
            WhoopApiAdapter.mapWhoopSportToModality(sportName = "", sportId = null)
        )
        assertEquals(
            ExerciseModality.OTHER,
            WhoopApiAdapter.mapWhoopSportToModality(sportName = null, sportId = null)
        )
        assertEquals(
            ExerciseModality.OTHER,
            WhoopApiAdapter.mapWhoopSportToModality(sportName = "alien_basketball", sportId = 999)
        )
    }

    @Test
    fun `BASE_URL points to the WHOOP developer v1 API`() {
        assertEquals("https://api.prod.whoop.com/developer/v1", WhoopApiAdapter.BASE_URL)
    }

    @Test
    fun `PROVIDER_KEY is whoop`() {
        // ApiTokenStore keys this string into EncryptedSharedPreferences;
        // renaming would orphan existing tokens after upgrade.
        assertEquals("whoop", WhoopApiAdapter.PROVIDER_KEY)
    }
}
