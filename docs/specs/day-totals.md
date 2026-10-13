# Spec: day totals for count metrics

cost_class: reversible

Origin: 2026-10-13. The owner wanted to show his mother his steps for
Saturday and Sunday. Trends draws a metric's raw samples over a rolling
window counted back from now; Timeline groups alerts and events by day,
not readings. "Steps on Saturday" was not a view the app had. The same
afternoon the owner said the first thing a new person should receive from
Bios is a working app, and named this as the first failure a stranger would
hit too.

References read on the owner's phone the same day: Health Connect's Steps
screen (a day pager "‹ Today ›", one large total "2,844 steps" with the
sources under it, then the interval entries), COROS Progress (a dated
header, large numbers with the unit under them, a calendar strip of days).

## Intent

A count that accumulates over a day (steps, active calories, active minutes)
is read by people as "how many that day", never as "the last sample". Bios
shows such metrics the way the owner and the people around him ask about
them: one total per calendar day, the day named, the number readable at a
glance, with the same two taps that reach any other metric.

## Goal

For every metric in `cumulativeDailyMetrics`, the Trends chart shows one bar
per calendar day (local midnight to local midnight) over the selected
window, the latest day selected by default, and tapping a bar puts that
day's name and total in the chart header. The Home tile for the same
metrics shows today's total and yesterday's. Verified when: the owner opens
Trends → Steps → Week and reads "Sat 11 Oct · N steps" after one tap on
Saturday's bar, and the number equals the sum of that day's primary
readings.

Criteria:

1. Buckets are calendar units in the device time zone, computed by SQLite
   `strftime(..., 'localtime')` in one grouped query, never by dividing epoch
   milliseconds. A DST-change day (23 h or 25 h) is still one bucket.
2. Window → bucket: Day = 24 hourly bars for today; Week = 7 daily bars
   ending today; Month = 30 daily bars ending today; Year and All = monthly
   bars over the window, All capped at 24 months. The Hour window is not
   offered for these metrics.
3. Days inside the window with no readings are drawn as empty bars, not
   skipped, so the x axis stays a calendar.
4. The header reads `<day label> · <total> <unit>` for the selected bucket,
   thousands separated in the device locale ("2,844 steps"). Default
   selection = the most recent bucket that has readings (at 15:00 the Day
   window selects the 14:00 slot, not the empty 23:00 one). Day labels: "Today", "Yesterday",
   else "Sat 11 Oct"; hour buckets "14:00"; month buckets "Oct 2026".
5. Only primary readings count, all sources (same filter as the Home tile's
   today sum and the daily digest). Self-reported readings are included in
   totals, as they are today.
6. Non-cumulative metrics keep the existing line chart unchanged.
7. The Home tile for cumulative metrics shows today's total as now and, under
   it, "yesterday N" when yesterday has readings. No deviation indicator, as
   now.
8. Nothing in this spec writes data, changes the provider, or touches any
   engine or baseline.

## Limits

| Cost | Bearer (seat) | Seat | Balance as the bearer sees it |
| --- | --- | --- | --- |
| One more tap than Health Connect's day pager to read a past day | owner | filled | Same two taps as every other metric, then one tap on the bar; a pager is a later spec if the tap is not enough. Accepted 2026-10-13 ("spec then build"). |
| A grouped SQL query over up to 30 days of step samples on every window change | owner's phone | filled | Health Connect emits a few hundred rows a day; SUM over ~10k rows in SQLite is milliseconds. Year/All group by month in the same query. |
| A total that includes self-reported counts next to sensor counts | owner | filled | Same rule as the Home tile and the digest today; a sensor-only toggle is out of scope. |
| A second owner in a time zone the device is not set to | other owners | empty | Not asked. Buckets follow the device clock, as COROS and Health Connect do. |

## Open

- A day pager with the entries list under the total (Health Connect's shape)
  would make "show my mother Saturday" one tap fewer. Not built until the
  bar tap proves insufficient.
- A daily-sum baseline for cumulative metrics (deviation against "usual
  steps per day") stays a future enhancement, as the Home tile comment says.
- Sleep duration is one reading per night, not cumulative; it is not in
  scope even though people also ask "how did I sleep Saturday".
