package com.bios.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.AlertManager
import com.bios.app.data.BiosDatabase
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** AlertManager: the notify gate, the channel per tier, and the channels an install ends up with. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AlertManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = context.getSystemService(NotificationManager::class.java)

    private fun manager(): Pair<AlertManager, BiosDatabase> {
        val db = Room.inMemoryDatabaseBuilder(context, BiosDatabase::class.java).allowMainThreadQueries().build()
        return AlertManager(context, db) to db
    }

    private fun anomaly(severity: AlertTier) = Anomaly(
        metricTypes = "[\"heart_rate\"]",
        deviationScores = "{\"heart_rate\":2.0}",
        combinedScore = 0.75,
        patternId = "ml_holistic_anomaly",
        severity = severity.level,
        title = "Outside your usual range",
        explanation = "Resting heart rate 66 bpm over the last 24 h, above your usual 53–60 bpm.",
    )

    @Test
    fun `observation is stored silently, every higher tier notifies`() {
        assertFalse(AlertManager.shouldNotify(AlertTier.OBSERVATION))
        for (tier in AlertTier.entries - AlertTier.OBSERVATION) assertTrue(tier.name, AlertManager.shouldNotify(tier))
    }

    @Test
    fun `each tier has its channel and nothing lands on a removed one`() {
        assertEquals(AlertManager.CHANNEL_NOTICE, AlertManager.resolveChannel(AlertTier.NOTICE).first)
        assertEquals(AlertManager.CHANNEL_ADVISORY, AlertManager.resolveChannel(AlertTier.ADVISORY).first)
        assertEquals(AlertManager.CHANNEL_WARNING, AlertManager.resolveChannel(AlertTier.WARNING).first)
        // URGENT stays in the enum for stored rows; it has no channel of its own.
        assertEquals(AlertManager.CHANNEL_WARNING, AlertManager.resolveChannel(AlertTier.URGENT).first)
        for (tier in AlertTier.entries) {
            assertFalse(AlertManager.resolveChannel(tier).first in AlertManager.REMOVED_CHANNELS)
        }
    }

    @Test
    fun `an install keeps four channels and loses the removed ones`() {
        AlertManager.REMOVED_CHANNELS.forEach {
            notifications.createNotificationChannel(NotificationChannel(it, it, NotificationManager.IMPORTANCE_HIGH))
        }
        val (_, db) = manager()
        try {
            assertEquals(
                setOf("bios_notice", "bios_advisory", "bios_warning", "bios_digest"),
                notifications.notificationChannels.map { it.id }.toSet(),
            )
            assertNull(notifications.getNotificationChannel("bios_urgent"))
            assertNull(notifications.getNotificationChannel("bios_followup"))
            assertEquals("Notices", notifications.getNotificationChannel("bios_notice").name)
            assertEquals(
                "A wide deviation from your usual range",
                notifications.getNotificationChannel("bios_warning").description,
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `channel names and descriptions pass the content policy`() {
        val (_, db) = manager()
        try {
            for (channel in notifications.notificationChannels) {
                val text = "${channel.name} ${channel.description}"
                assertTrue(text, AlertContentPolicy.validate(channel.id, text).isEmpty())
                for (word in listOf("health", "condition", "attention", "urgent", "illness")) {
                    assertFalse("'$word' in $text", text.lowercase().contains(word))
                }
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `a notification carries the measured line and the disclaimer, nothing else`() {
        shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val (alerts, db) = manager()
        try {
            val a = anomaly(AlertTier.ADVISORY)
            alerts.sendNotification(a)
            val posted = shadowOf(notifications).allNotifications.single()
            assertEquals(AlertManager.CHANNEL_ADVISORY, posted.channelId)
            assertEquals(a.title, posted.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
            assertEquals(
                "${a.explanation}\n\n${AlertManager.DISCLAIMER}",
                posted.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT).toString(),
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `an observation posts no notification`() {
        shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val (alerts, db) = manager()
        try {
            alerts.sendNotification(anomaly(AlertTier.OBSERVATION))
            assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `the disclaimer is the one sentence`() {
        assertEquals(
            "Bios is not a medical device. It shows measurements and does not interpret them.",
            AlertManager.DISCLAIMER,
        )
    }

    @Test
    fun `anomaly defaults`() {
        val a = anomaly(AlertTier.NOTICE)
        assertFalse(a.acknowledged)
        assertNull(a.acknowledgedAt)
        assertNull(a.suggestedAction)
        assertNotEquals(a.id, anomaly(AlertTier.NOTICE).id)
    }
}
