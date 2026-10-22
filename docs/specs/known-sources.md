# Spec: known sources — what Bios reads, and when it last did

cost_class: reversible

Origin: 2026-10-22, owner's ask during the COROS direct-BLE work: "we will
need in bios in data sources a way to know which devices is known, what
data they report and their last synced timestamp". The existing Data &
sources screen shows connect/disconnect state per provider, and the Data
coverage screen is metric-first (per metric, which route could serve it).
Neither answers device-first: for each source Bios actually holds data
from, what it delivered and when it last delivered.

## Intent

The owner can see the pipeline from the source side: every device or
source that has put data into Bios, named, with what it reports and how
fresh it is. The same intent as the direct-BLE work, correct ingestion
whatever the source, read from the other end: not "is this provider
connected" but "what has actually arrived, from where, and when".

## Goal

A screen lists every source Bios has stored a reading from, newest first,
and for each shows: a human name, the metrics it has delivered, and the
timestamp of its last stored reading. Verified when: on the owner's phone
the list includes the COROS Training Hub source (HRV, resting HR) and the
Health Connect writers (steps, heart rate, sleep) with last-sync times
that match the newest reading each has in the database, and a source that
has not delivered in over its own cadence window reads as stale.

Criteria:

1. One row per source. The rows come from the `data_sources` registry
   joined to the readings by `sourceId` (a reading's `sourceId` is the
   `DataSource.id`). A `sourceId` present in readings but absent from the
   registry still gets a row, named from the id, so nothing is hidden.
2. Name. `DataSource.deviceName` and `deviceModel` when present, else a
   friendly label for the `SourceType`, else the raw `sourceId`. Truthful,
   never invented.
3. What it reports. The distinct metric types the source has delivered
   (`sourceMetricTypes`), shown as friendly names, capped with "+N more"
   past a few, so a source with many metrics stays one readable row.
4. Last synced. The timestamp of the newest stored reading for that
   source (`sourceFreshness.lastTimestamp`), shown as a relative time
   (e.g. "2 h ago", "3 d ago"), with the absolute date available. This is
   the last reading, not the worker's last run: a worker that ran and
   fetched nothing does not count as a sync.
5. Freshness state. A source whose last reading is older than its own
   cadence window (`SourceCadence`) is marked stale; a source with an open
   error in `SourceHealthStore` is marked "needs attention" with the
   owner action. A source inside its window reads as current.
6. Read-only. The screen never writes, connects or disconnects anything;
   those live on the existing Data & sources screen. One tap off it.

## Limits

| Cost | Bearer (seat) | Seat | Balance as the bearer sees it | Fires when |
| --- | --- | --- | --- | --- |
| A source shown as "known" that is actually dead or gone could read as if Bios still gets data from it. | owner | filled | He asked to SEE last-sync, which is itself the guard: last-sync leads every row and a stale source is marked, so the list is a truth surface and a long-dead source showing its real old date is correct, not misleading. | A row shows a recent-looking state for a source whose newest reading is old → the relative time or the stale mark is wrong, fix it. |
| The registry has one row per source type, so two devices of the same type (two phones, two watches of one brand) could collapse into one name. | owner | empty | Not hit today: one of each type on his phone. The per-writer Health Connect split already separates COROS from Fitbit. | A second device of an existing type delivers and the row name does not distinguish it → split the registry key by device, later spec. |
| Screen cost: another settings screen to maintain. | owner | filled | Reuses existing DAO queries and stores; no new query, no new table. | The screen needs a new schema or a migration → reconsider, it was meant to be a read over what exists. |

## Open

- Friendly metric names: a prettified metric key for now (underscores to
  spaces, capitalised); a curated label map is a later nicety.
- Whether to show reading counts per source; left off the first cut to
  keep a row scannable, available if the owner wants it.
