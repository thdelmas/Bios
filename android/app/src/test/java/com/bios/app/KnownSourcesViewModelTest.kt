package com.bios.app

import com.bios.app.model.SourceType
import com.bios.app.ui.settings.KnownSourcesViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class KnownSourcesViewModelTest {

    @Test
    fun prettyMetric_humanises_the_key() {
        assertEquals("Heart Rate Variability", KnownSourcesViewModel.prettyMetric("heart_rate_variability"))
        assertEquals("Steps", KnownSourcesViewModel.prettyMetric("steps"))
        assertEquals("Resting Heart Rate", KnownSourcesViewModel.prettyMetric("resting_heart_rate"))
    }

    @Test
    fun typeLabel_names_coros_as_training_hub() {
        assertEquals("COROS Training Hub", KnownSourcesViewModel.typeLabel(SourceType.COROS_API))
        assertEquals("Health Connect", KnownSourcesViewModel.typeLabel(SourceType.HEALTH_CONNECT))
    }

    @Test
    fun typeLabel_covers_every_source_type() {
        // No source type may fall through to a blank label on the known-sources screen.
        for (type in SourceType.entries) {
            val label = KnownSourcesViewModel.typeLabel(type)
            assertEquals("non-blank label for $type", true, label.isNotBlank())
        }
    }
}
