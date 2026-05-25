package com.bios.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the shape of the three owner-PRO neurology attack event types
 * added across #207 + #283 Cut 1. All three must share
 * (EVENT, NEUROLOGICAL, allowsManualEntry=true) so:
 *
 *  - the #283 Cut 2 entry-payload writer treats them uniformly,
 *  - the #283 Cut 3 chronic-migraine evaluator can count
 *    migraine-days separately from headache-days from a single
 *    `metric_readings` scan,
 *  - the #284 cluster periodicity histogram has its own row stream
 *    independent of generic headache rows.
 *
 * Lifted out of [MetricTypeTest] to keep that file under the 500-line cap.
 */
class NeurologyAttackEventInvariantsTest {

    @Test
    fun attack_events_share_the_owner_pro_shape() {
        for (t in setOf(
            MetricType.MIGRAINE_ATTACK_EVENT,
            MetricType.HEADACHE_ATTACK_EVENT,
            MetricType.CLUSTER_HEADACHE_ATTACK_EVENT,
        )) {
            assertEquals(MetricDomain.NEUROLOGICAL, t.domain)
            assertEquals(MetricUnit.EVENT, t.unit)
            assertTrue(
                t.allowsManualEntry,
                "${t.key} must allow manual entry — owner is the only source",
            )
        }
    }
}
