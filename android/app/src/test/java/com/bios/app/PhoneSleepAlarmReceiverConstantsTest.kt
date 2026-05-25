package com.bios.app

import com.bios.app.ingest.PhoneSleepAlarmReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM contract checks for the alarm-relay receiver (#315).
 * Verifies the two pieces of public surface the receiver exposes
 * to other parts of the app — the action string and the schedule
 * delay — without needing Robolectric.
 *
 * The action string is part of the manifest contract: changing it
 * silently in source while leaving the manifest entry behind would
 * make the receiver never fire. The delay is part of the
 * UX-perceived "starts immediately on plug-in" contract — anything
 * much longer would feel laggy and risk the owner unplugging before
 * collection begins.
 */
class PhoneSleepAlarmReceiverConstantsTest {

    @Test
    fun action_string_matches_manifest_filter() {
        // The receiver in AndroidManifest.xml declares this exact action
        // in its <intent-filter>. Drift between this constant and the
        // manifest would silently break the alarm path.
        assertEquals(
            "com.bios.app.ingest.action.START_SLEEP_COLLECTION",
            PhoneSleepAlarmReceiver.ACTION_START_COLLECTION,
        )
    }

    @Test
    fun schedule_delay_is_short_enough_to_feel_immediate() {
        // 3 s is the chosen balance: long enough that the alarm
        // subsystem registers it as a fresh event (and grants the
        // FGS-launch exemption); short enough that the owner perceives
        // collection as starting at plug-in.
        assertTrue(
            "schedule delay must be under 10s to feel immediate",
            PhoneSleepAlarmReceiver.SCHEDULE_DELAY_MS < 10_000L,
        )
        assertTrue(
            "schedule delay must be > 0 so the alarm path is taken",
            PhoneSleepAlarmReceiver.SCHEDULE_DELAY_MS > 0L,
        )
    }
}
