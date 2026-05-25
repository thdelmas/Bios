package com.bios.app.model

import androidx.room.Entity

// Owner-controlled per-(source, metric) ingest gate. Row presence with
// enabled=false suppresses writes for that pair in IngestManager's sync
// path; absent rows fall through to the default (enabled). Re-enabling
// only resumes ingest from that point forward — historical readings that
// landed before disable remain.
@Entity(
    tableName = "source_metric_toggles",
    primaryKeys = ["sourceTypeKey", "metricTypeKey"],
)
data class SourceMetricToggle(
    val sourceTypeKey: String,
    val metricTypeKey: String,
    val enabled: Boolean,
)
