# Spec: one source per day for count metrics

cost_class: reversible

Origin: 2026-10-14. The owner's watch read 3,286 steps for the day; Bios read
7,475. COROS's own daily totals for the week before (Sat 10 Oct 3,798, Mon
12 Oct 2,863) sat five to six times under what Bios showed for the same days
(21,443 and 18,848, the numbers verified in docs/specs/day-totals.md). Three
producers write steps on the owner's phone: Health Connect (the watch's
records), Direct Sensors and Phone Sensors (the same hardware pedometer read
by two adapters), and every total in the app is SUM over all of them.
Day-totals criterion 5, "all sources", wrote that into the spec. The owner's
answer the same day: a preference order of sources the owner can choose, the
next source used only as fallback.

Read on the plugged phone the same morning: Health Connect's Steps screen
lists three writers, COROS, Fitbit and "Pixie2", with Fitbit first in Health
Connect's own priority list; its deduplicated total for the day was 5,082
(Fitbit's phone count) while the watch said 3,286, and 8,436 for the day
before against the watch's 5,464. Bios reads every raw record of all three
writers under one "Health Connect" source and sums them, then adds its own
two phone-pedometer readings. The multiplier is three writers inside Health
Connect plus two inside Bios.

## Intent

A number Bios reports is one witness's account, the witness the owner
chose, never the pile of every witness that saw the same walk. People do it
this way between themselves: someone who wears a watch answers "how many
steps today" from the watch, and from the phone only when the watch was not
on. Two counts of one walk are one count, not two.

## Goal

For every metric in `cumulativeDailyMetrics` and every calendar day, the
total Bios shows is the sum of exactly one source type's readings for that
day: the first source type in the owner's order that has any reading that
day. Trends bars, the Home tile, the daily digest and the provider's day
summary agree on it. Verified when: with Health Connect first in the order,
Trends → Steps → Week → Sat 10 Oct reads 3,798 steps, the watch's number,
and the Home tile's today reads the watch's live count within one sync.

Criteria:

1. A Health Connect record's source is the app that wrote it. Bios keeps
   one source per writing app ("COROS via Health Connect", "Fitbit via
   Health Connect"), named from the record's data origin, and the order
   lists them as separate rows. "Health Connect" as a single source stops
   existing for count metrics. Rows ingested before this spec are re-keyed
   in place by the 30-day re-read: the stable id of a Health Connect reading
   no longer embeds the Bios source id, so the same record lands on its
   per-app source instead of beside its old row. Rows older than the re-read
   window keep the generic source, which sits in the order like any other.
2. The winner is chosen per (metric, calendar day in the device time zone):
   the first source in the order with at least one primary reading that
   day. A day where no source has readings stays empty. Readings of every
   other source that day do not count, whatever their timestamps. A
   coverage rule (a lower source wins with more hours of readings) was
   built and withdrawn the same day: on the owner's phone the phone app
   writes a record in more hours than a complete watch mirror, so the rule
   took five of six days from the watch, four of them days the watch had
   right; a "partial" mark on the same measure fired on the same five days.
   Hours with readings do not measure completeness. The answer to a partial
   mirror is the vendor's own total (2a), not a heuristic.
2a. A vendor adapter that can fetch a day's total from the vendor emits one
   reading per day, stamped at the start of that day with a duration of 24
   hours, under the adapter's own source. It is a witness like any other in
   the order, ranked with the worn devices by default. COROS is the first
   adapter wired; the class is generic because "some people may not use
   COROS" (owner, 2026-10-14).
2b. COROS wiring (owner 2026-10-14, "proceed with Coros as well"): the
   Training Hub web API has no steps, so the adapter also signs in to the
   COROS mobile API (the host the phone app uses) with the same e-mail and
   password, encrypted the way the app does it, and reads the daily
   statistics for the window. The mobile token lives about an hour, so the
   adapter signs in again from the stored password when refused; without a
   stored password the daily total stops after the first hour and the
   source reads "needs attention" with the sign-in action, as
   docs/specs/source-liveness.md already says. A refusal of the mobile
   sign-in never touches the Training Hub readings (HRV, resting HR).
   Amended 2026-10-19 (owner: "it keeps logging me out"): the mobile
   sign-in is WITHDRAWN. COROS allows one phone-app session per account;
   each Bios mobile sign-in (hourly token, retry on every sync) logged the
   real COROS app out, and a logged-out app does not sync the watch, so the
   path starved its own source. Step totals come from the Health Connect
   mirror under the COROS source (criterion 2, partial-mirror days stay
   partial). The parser and client stay in the tree, unused. The Training
   Hub sign-in (HRV, resting HR) is untouched; it never caused a logout.
3. The order is one owner-editable list of source types, applied to all
   cumulative metrics. Default, when the owner has set nothing:
   self-reported first, then the watch vendors' Health Connect sources and
   the vendor APIs (COROS, Oura, WHOOP, Garmin, Polar, Withings),
   Gadgetbridge, then phone-tracking apps via Health Connect (Fitbit and
   any other), Direct Sensors, Phone Sensors. A count the owner typed is the owner's word and wins the day;
   below it the order is the confidence ranking the ingest already assigns.
   (Owner 2026-10-14: "manual entry for step should win".)
4. Selection happens at read time. Every source keeps being stored as today;
   nothing is deleted or rewritten; changing the order changes every past
   day at once. The per-(source, metric) toggle in Settings stays an ingest
   gate and is unchanged.
5. Hour buckets (Day window) come from that day's winner only, so the 24
   hourly bars sum to the day total. Month and year buckets are sums of
   per-day winners, never a winner picked per month.
6. One function serves all consumers of a day total: Trends bucket totals,
   Home today and yesterday, DailyDigestWorker steps, HealthApiServer's day
   summary. No consumer sums `fetchValues` for a cumulative metric any more.
7. The hardware pedometer is read by one adapter. Phone Sensors keeps
   `readStepCounter`; Direct Sensors stops emitting steps. One hardware
   witness, one source.
8. Settings → Metric sources shows the order for count metrics as a list
   with move-up and move-down, and names which source won today for steps,
   so the owner can see the rule at work without a chart.
9. A self-reported count carries where it came from, the way a clinical
   reading already carries its clinic: a device or method text ("public
   machine, pharmacy", "watch read by eye") and an optional note, stored on
   the reading. A day won by a self-reported reading is marked as such in
   the Trends header and the Home tile, so a later reader can judge the
   count by its method. No manual entry screen for steps exists today; this
   criterion binds the first one that does.
10. Baselines, rules and the anomaly engine are not touched by this spec;
   they keep reading all SENSOR sources. Companions reading raw readings
   through the provider keep receiving every source's rows.

## Limits

| Cost | Bearer (seat) | Seat | Balance as the bearer sees it | Fires when |
| --- | --- | --- | --- | --- |
| A day the watch was worn briefly is counted by the watch alone, and the phone's fuller count for that day is dropped | owner | filled | Per day, not per gap: a gap-filling rule brings the overlap back inside the day. Accepted 2026-10-14 ("yes" to per-day fallback). | A day total far below the phone's count on a day the owner knows the watch was on for an hour or two. Then the question is a per-gap rule, a later spec. |
| A typed count overrides a worn sensor for that day, so a wrong or careless entry hides the watch's number | owner | filled | Manual entry wins; it is rare, and the entry must say which device or method counted so its quality can be judged later. 2026-10-14. | A day marked self-reported where the owner cannot say what counted it: the provenance field was skippable, and criterion 9 failed. |
| Duplicated rows inside one source (the Health Connect stale-row hypothesis) are not cured by an order | owner | filled | Named before the "yes" 2026-10-14; a separate finding once the database is read. | After the order is set, the Health Connect day alone exceeds the watch's own total by more than a tenth on a full day. |
| A steps-based rule or baseline still sums every source, so an alert can cite a number the Trends chart does not show | owner | filled | Count metrics first, engine later. Accepted 2026-10-14. | A steps alert or the daily digest's baseline line names a total different from the day's Trends bar. |
| The default order is a guess for owners with Gadgetbridge or a vendor API and no Health Connect | other owners | empty | Not asked. The owner-editable order is the remedy. | A second owner reports a day total from the wrong device. |
| Dropping the Direct Sensors step producer loses nothing only if Phone Sensors is bound on the same phone | owner | filled | Both bind on the Pixel today; Phone Sensors binds whenever a step counter exists. | A phone with a pedometer and no Phone Sensors source shows no phone-fallback days at all. |
| The COROS mobile sign-in imitates the phone app (a fixed device fingerprint and an encryption key read out of the app) and the vendor may refuse or change it at any time | owner | filled | Same seat the Training Hub adapter already sits in; accepted 2026-10-14 ("proceed with Coros as well"). | The mobile sign-in is refused while the Training Hub one still works: the COROS source shows "needs attention" naming the mobile sign-in, step totals fall back to the Health Connect mirror, nothing else changes. |
| COROS's terms on automated access; the price is the account, not a fine | everyone who keeps COROS's terms (pre-existing seat) | filled | COROS Terms of Service (coros.com/terms, updated 2023-04-23), read 2026-10-14: §4(e) forbids "any robot, spider, scraper, or other automated means to access the Service" without written permission, §4(h) forbids bypassing access restrictions, §17 lets COROS terminate the account for a breach. The Training Hub adapter has sat on this seat since 2026-09-15 with the owner's knowledge; the mobile sign-in adds §4(h). Price = loss of the COROS account and its history; trust cost borne by the pair. | COROS blocks or warns the account. |
| A password kept on the device so an hourly token can be renewed without the owner | owner | filled | Opt-in "keep password" on the COROS sign-in dialog, encrypted, wiped with everything else (source-liveness, 2026-09-28). Without it the daily total lasts one hour. | The daily-total source reads stale while Training Hub readings keep arriving: the owner did not keep the password. |

## Open

- A manual entry screen for count metrics does not exist; criterion 9
  governs it when it is built. Whether a typed count without a device or
  method should be refused or only warned is unset.
- Per-gap fallback (fill the hours the watch did not cover) stays unbuilt.
- In the Day window a vendor daily total has no hours; it draws as one bar
  in the first slot and the header says so.
- COROS's Training Hub day record carries no steps (keys read on the phone
  2026-10-14); its mobile API does, behind an encrypted login the community
  reverse-engineered. Wiring it is a separate decision.
- The same order could feed baselines and rules for count metrics. Not in
  scope; see criterion 9.
- "Pixie2" is the phone's own name (owner, 2026-10-14): the handset's
  pedometer written into Health Connect under the device name. It is a
  phone-tracking source and sorts with Fitbit in the default order.
  Health Connect's own priority list (Fitbit above COROS today) keeps
  deciding what Health Connect's screen shows; Bios does not read or write
  that list.
- Health Connect's aggregate API, which applies that list, was the other
  route. Not taken: the owner would then order sources in two places, and
  the entries list and provenance per record would be lost.
