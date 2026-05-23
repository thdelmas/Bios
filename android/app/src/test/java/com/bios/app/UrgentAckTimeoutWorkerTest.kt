package com.bios.app

import com.bios.app.alerts.UrgentAckTimeoutWorker
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [UrgentAckTimeoutWorker]'s public contract — the schedule/cancel
 * tag and the input-data keys (#179, EM/CC §2.1). These strings are the
 * coupling between [com.bios.app.alerts.AlertManager] (which schedules)
 * and [com.bios.app.alerts.AlertActionReceiver] (which cancels on ack).
 *
 * If either side renames a key, the cancellation silently misses and an
 * SMS-compose prompt fires for an already-acknowledged alert — exactly
 * the failure mode the manifesto's owner-mediated escalation forbids.
 */
class UrgentAckTimeoutWorkerTest {

    @Test
    fun input_data_keys_are_stable() {
        assertEquals("anomaly_id", UrgentAckTimeoutWorker.KEY_ANOMALY_ID)
        assertEquals("alert_title", UrgentAckTimeoutWorker.KEY_ALERT_TITLE)
        assertEquals("tier_level", UrgentAckTimeoutWorker.KEY_TIER_LEVEL)
    }

    @Test
    fun work_name_is_namespaced_per_anomaly() {
        // Tag is what cancel() targets — if it diverged from
        // ExistingWorkPolicy.REPLACE's unique name, ack-cancellation
        // would silently miss.
        val id = "abc-123"
        assertEquals("urgent_ack_abc-123", UrgentAckTimeoutWorker.workName(id))
    }

    @Test
    fun work_name_differs_across_anomalies() {
        // Two URGENT alerts in quick succession must schedule under
        // distinct unique names — otherwise the second alert's worker
        // would silently REPLACE the first's, losing escalation on
        // whichever alert was first.
        assertEquals("urgent_ack_a", UrgentAckTimeoutWorker.workName("a"))
        assertEquals("urgent_ack_b", UrgentAckTimeoutWorker.workName("b"))
    }
}
