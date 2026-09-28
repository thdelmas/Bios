# Spec: source liveness

cost_class: reversible

Origin: 2026-09-28. The COROS session token died on 2026-09-23. For six days
the ingest worker reported SUCCESS, the settings row read Connected, the
adapter's error sat in an encrypted preference nobody opens, and the
disconnect push never qualified because a two-rows-a-day source cannot reach
the 50-reading activity floor. The owner learned of it from a companion app
asking for a camera reading.

## Intent

The consumer of a source, not the source, owns the knowledge that the source
has stopped delivering, and turns that knowledge into a state the owner can
read and, when there is something to do, a notification that names the
action. Silence from a source is a fact Bios reports, never a fact Bios hides.

## Goal

For every source that has delivered across at least two of its own intervals,
a stop in delivery or a refusal from the vendor is visible in Bios within one
sync as a state on the source (healthy, stale, needs attention), readable by
companions through the provider, and pushed once to the owner when an owner
action exists. Verified when: a COROS token refusal flips the row to
"Needs attention" with the vendor message on the next sync, W2F reads that
state through `/sources`, and one push names the sign-in action.

Criteria:

1. Staleness is measured in the source's own interval (declared per source
   type), never in rows. Two missed intervals = stale in the app; five missed
   intervals = pushable. Sources with no declared interval are never stale.
2. A vendor refusal with an owner action (re-authenticate) is "needs
   attention" immediately, pushable immediately, cool-down seven days.
3. Transient transport errors do not flip state; they show as the last
   message only. Staleness catches a persistent outage.
4. "Connected" on a settings row means the last use succeeded. A row with a
   recorded refusal reads "Needs attention" with the message and opens the
   sign-in dialog without clearing anything.
5. Re-authentication from stored credentials is opt-in per source, off by
   default, and stored in the same encrypted store the token uses. On a token
   refusal the adapter retries login once before reporting attention.
6. The provider exposes `/sources` with one row per registered source: type,
   label, state, since, owner action, message, last delivery, metric types
   delivered.
7. Nothing in this spec re-scores or backfills any consumer's past days.

## Limits

| Cost | Bearer (seat) | Seat | Balance as the bearer sees it |
| --- | --- | --- | --- |
| A stale push for a watch left unsynced over a weekend | owner | filled | Interval-scaled and five intervals before a push; one push per seven days. Accepted 2026-09-28. |
| A vendor password kept on the device | owner | filled | Opt-in only, encrypted, wiped with everything else. "Let the user choose." 2026-09-28. |
| Companions that read `/sources` must tolerate an older Bios without it | companion maintainers | filled (same person today) | Empty cursor means unknown, treat as before. |
| A second Bios owner with a different cadence tolerance | other owners | empty | Not asked. Interval table is per source type, not per owner. |

## Open

- Only COROS classifies its refusals today. Oura, Withings, WHOOP, Garmin,
  Polar reach stale through silence, not attention through a message.
- The interval table is a guess per source type until a second owner reports.
- Whether in-process sources (phone, direct sensors) should ever push is
  unchanged: no.
