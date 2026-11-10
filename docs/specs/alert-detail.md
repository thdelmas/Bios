# Spec: alert detail

cost_class: reversible

Origin: 2026-10-03. A holistic alert fired at 08:36 ("Your resting heart rate
is 3.6 std devs above baseline."). The owner tapped it and nothing happened:
`AlertManager` built the notification without a `contentIntent` (the device
showed `contentIntent=null`). The in-app alert card existed but answered
neither "what is happening" nor "what do I do", and spoke in σ, not bpm.

## Intent

An alert the owner cannot act on is noise. Tapping an alert lands on an
answer to three questions: what was measured, what it might mean, what to do
now.

## Goal

One tap on an alert notification (or the follow-up reminder) opens a screen
for that alert showing, in this order:

1. the deviating values in real units next to the owner's usual range
   ("66 bpm, usually 53–60 bpm"), averaged over the same 24 h the detector used;
2. the other watched signals in the same window, deviating or not;
3. common causes of that change, written as possibilities;
4. what to watch, and when to seek care now (with the regional emergency
   number when known);
5. acknowledge and the journal entry ("What happened?"), which feeds the
   alert ledger.

Verified when: the notification's `contentIntent` is non-null and opens
`alert/{id}` for that alert; the notification body for a holistic alert
states values in units; `AlertDetailContentTest` passes.

## Limits

| Cost | Bearer (seat) | Seat | Balance as the bearer sees it | Fires when |
| --- | --- | --- | --- | --- |
| Causes read as a verdict on the owner ("you drank") | owner | filled by draft, not asked | Causes are a generic list per metric and direction, never inferred from the owner's other data. | Any cause line names an owner-specific event or a companion-app datum. |
| Copy claims a diagnosis the validation does not support | owner, public claim | filled (Mishra replay page) | No "detects", no condition name on holistic alerts. | Copy says the alert detected a condition. |
| "Seek care now" loses credibility through false alarms | owner | filled by draft, not asked | Red flags are symptoms, not data thresholds; the data never by itself says "seek care now". | A data value alone triggers the seek-care wording. |
| Values recomputed at read time differ from detection time | owner | filled by draft | Same window, same SENSOR filter; baseline may have moved, so the range says "usually", not "at the time". | Owner reports a value on the screen that differs from the notification. |

## Open

- Daily digest notifications still have no tap target.
- The causes list is unsourced common knowledge; no `Citation` per line yet.
- A second owner (not the author) has not read the screen.

## Criterion 6 (added 2026-11-07): signals drawn, evidence named

Origin: 2026-11-07. The owner opened the 07-11 alert (resting heart rate
69 bpm, usual 54–65) and read the other signals as a block of text: "the info
is very textual and we could use something more visual". He also asked why the
number was what it was; the screen could not say what stood behind the average.

Goal: every watched signal on the screen is a range strip: the owner's usual
range (baseline p5–p95) as a band, the alert's 24 h average as a dot, the
band's ends labelled in real units, each row on its own scale. Deviating
signals first, then the others, same drawing. Under each strip one caption:
the status ("above your usual range") and the evidence behind the average,
"N readings · <source names>", so a one-reading average or a two-source mix is
visible without opening the database. One legend under the strips defines the
band and the dot once.

Found on the Pixel the same day: the 06-11 08:28 alert ("69 bpm") re-read
as 63 bpm from one COROS reading, within range, because the window is
recomputed from today's readings and the watch had revised its daily value.
The screen contradicted the alert, which is the fourth limit above firing.
So the alert now carries a snapshot of every watched signal at detection
(`anomalies.signalSnapshot`, schema v37, JSON via `SignalSnapshot`): the
strips draw the snapshot, and the caption adds "now 63 bpm from 1 reading ·
COROS" when the re-read window differs. Alerts from before v37 keep the
recomputation only. Sleep duration reads as hours and minutes ("8h24"), not
seconds. One legend sits under the last strip section.

Verified when: `SignalStripTest` passes (dot right of the band when above,
left when below, inside when within; half a band of air each side; a flat
baseline draws); `SignalSnapshotTest` round-trips; `AlertDetailContentTest`
covers the evidence line, the sleep format and the "now" line; the Pixel
shows strips with a readable p5/p95 tick pair and the legend.

Limits added:

| Cost | Bearer (seat) | Seat | Balance as the bearer sees it | Fires when |
| --- | --- | --- | --- | --- |
| Per-row scales invite cross-row comparison of dot positions | owner | filled by draft, not asked | The legend says each row has its own scale; rows are read one at a time. | The owner compares two rows' dot positions as if on one scale. |
| Naming sources reads as blame on a device | owner | filled by draft | The caption states the sources, nothing about their quality. | A caption judges a source. |

## Criterion 7 (added 2026-11-07): the tier is the heading

Origin: his read of the first strips build on the Pixel: "a lot of unused
space on top", "the title Alert is unnecessary", "different levels of warning
so the user knows how critical it is"; and on the strips, "miss indicative
colors".

Goal: the screen opens on a back arrow and a tier meter: the four tiers as
segments filled up to the alert's tier in its color, the four names under
them, and one line saying what that tier means in Bios's own behaviour
(logged quietly / silent notification / notification with sound / highest
tier). No "Alert" app bar; the Scaffold's status-bar padding is the only top
inset. The strip's dot takes a status color by distance from the range:
inside (success), at the edge, |z| ≥ 1 (warning), outside, |z| > 1.5
(attention), far, |z| ≥ 3 (error); band and text stay neutral.

Verified when: `AlertDetailContentTest` covers the four meaning lines against
the content policy and the four color bands; the Pixel shows the meter and a
colored dot with no empty band above the heading.

Limits: the meaning lines describe what the app did, never what the owner
should do (the content-policy test holds them to that).

## Criterion 8 (added 2026-11-07): a re-delivered day-value settles before it is scored

Origin: the 06-11 alert fired on a watch day-value (69 bpm) the vendor cloud
later revised (63). His pick among three shapes: "score a daily reading only
once it has been seen unchanged across two syncs".

Goal: readings from sources that re-deliver day-values on every sync (the
vendor cloud class in `SourceCadence.redeliversDailyValues`: COROS, Oura,
WHOOP, Garmin, Withings, Polar, Dexcom) carry `seenCount`, `firstSeenAt` and
`provisional` (schema v38). `ReadingRevisions` reconciles each sync: same
value seen again counts up, a changed value restarts at one. A row settles
when seen at least twice with the sightings at least 6 h apart; until then
`provisional = 1` and every engine query (`fetchValues`, `fetchTimedValues`,
`countInRange`, so baselines, rules and the holistic detector) skips it.
Health Connect, phone sensors and Bios derivations are untouched.

The 6 h gap is the draft's seat, not asked: two Bios syncs 15 min apart
would both read the preliminary value and "two syncs" alone would have let
the 06-11 alert through. Cost: an alert on last night's day-value arrives
about six hours later than before. Set the gap to 0 to get his literal rule.

Verified when: `ReadingRevisionsTest` passes (first sighting provisional;
second within the gap still provisional; unchanged past the gap settles and
keeps id/createdAt; a revised value restarts); the next COROS day-value on
the Pixel shows `provisional` flipping after the gap.

## Criterion 9 (added 2026-11-07): five tiers

His ask: "a fifth level between advisory and urgent: warning". `AlertTier`
is OBSERVATION 0 · NOTICE 1 · ADVISORY 2 · WARNING 3 · URGENT 4; stored codes
rise with the order, so v38 remaps stored URGENT 3 → 4 in `anomalies` and
`emergency_contacts.minTierLevel`. WARNING has its own notification channel
(`bios_warning`, high importance), its own color (`BiosTokens.severe`), a
meaning line, a segment in the tier meter, and a companion-API state
(`HealthState.WARNING`, so `/health/status` can now answer "warning";
companions that switch on the four old values need the fifth). Emitted today
by the holistic detector only: an advisory-level score with at least one
signal three or more standard deviations out (`classifyHolistic`). Condition
patterns keep their classifier and floors; none declares a WARNING floor yet.

## Criterion 10 (added 2026-11-07): seek-care card reads for where the phone is

His two reads: the card said 911 in Europe, and the list read as if all
items had to hold. The region now resolves from the phone's mobile network
country, then the SIM's, then the locale (`RegionConfigProvider.forDevice`);
the heading says "if any one of these applies" and the line names the
region ("call 112 (Spain)"). The strip's track is tinted by the same zones
the dot is colored by: band = inside (success), to about 3 σ = outside
(attention), beyond = far (error).

Verified when: `RegionDeviceLocaleTest`, the emergency-line and far-zone
tests pass; the Pixel in Spain shows 112.

