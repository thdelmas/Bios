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
