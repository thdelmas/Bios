# Spec: read the watch directly, no COROS app, no COROS account

cost_class: reversible

Origin: 2026-10-19. Bios signed in to the COROS phone API for daily step
totals; COROS allows one app session per account, so each sign-in logged
the real COROS app out and a logged-out app stops syncing the watch
(fixed 2026-10-20, a49eaaff). The owner asked whether Bios could sync the
Kiprun GPS 500 without the app: "if we could get rid of coros cloud it
could be nice, we need to check it doesnt impact data", then "ok go for
it" (2026-10-22) after two captures.

What the captures showed (Bluetooth HCI snoop on the owner's Pixel,
2026-10-19 and 2026-10-22, parsers in the knowledge base
`scripts/coros-ble/`): the link is bonded and encrypted at the radio
layer; above it the app and the watch exchange a proprietary framed
protocol in the clear. The phone writes commands on one characteristic
(command byte, fragment index, payload, checksum), the watch answers on a
second, bulk file reads arrive on a third in 60-byte notifications with a
flow-control write every 1,200 bytes on a fourth. A sync reads files by
type and 4,096-byte offset. File type 0x05 is the daily record (87 KB for
three days), type 0x0c the watch's text log, type 0x36 a small device
file. The daily record is a stream of tagged records: tag 0x41 is one
heart-rate sample per minute (minute counter since 2014-01-01 UTC, value,
flag), tag 0x62 a five-minute block with one value, tag 0xf8 a block
header with seconds since the same epoch. 3,382 heart-rate minutes from
19-10 10:25 to 22-10 10:03 decoded on the first pass, range 49 to 176.
Sleep, nightly HRV, resting heart rate, steps and stress are in the same
file, tags not yet mapped. The COROS app's Java is packed, but its native
data library embeds 54 protobuf schemas (dumped to
`/home/mia/apk-untrusted/coros-4.10.8-protos.txt`, daily record schema =
`DailyHappenDayInfo`) and its raw-file parser keeps readable symbol names
(`create_sleep_info_from_buffer`, `add_device_fitness_hrv`), so the
remaining tags can be read off the owner's own copy of the app.

Not yet shown: whether the watch accepts a client that is not the COROS
app. The login frame (command 0xa5) carries a 16-byte blob whose origin
is unknown; the watch may require it, or may accept any bonded peer.

## Intent

Every source's data lands in the pipeline correctly, whatever the device
or the source is (owner, 2026-10-22). Bios is the place the body data
arrives and is read; the instrument that measured it should reach Bios on
its own, not through a maker's app or cloud that holds the data hostage on
the way. Reading your own device yourself, like a thermometer, is the
general shape; a vendor app in the path is the detour. This spec serves
that intent through one instance.

## Goal

The instance is this watch: Bios reads the Kiprun GPS 500 over Bluetooth
with the COROS app logged out and no COROS account in use, and shows the
same numbers the COROS app would have shown. Verified when: for three
consecutive days with the app logged out, Bios holds for each day the
per-minute heart rate, the night's sleep duration and stages, the nightly
HRV, the resting heart rate and the step total; then the app is logged
back in, syncs the same days, and its values match Bios within 1 ms (HRV),
1 bpm (RHR), 5 min (sleep), 1 % (steps).

Criteria, in the order they are gates (each one can stop the build):

1. Handshake replay. A client that is not the COROS app, first a script
   on this computer's Bluetooth adapter, then Bios, connects to the
   watch, sends the recorded login frame, and reads file type 0x05 from
   offset 0. Pass: the watch returns the file header and the first
   4,096 bytes. Fail: the watch refuses, disconnects, or answers with an
   error to the login frame; then the 16-byte blob is the question, and
   the spec is re-checked before any further work (see limit 1).
2. Reading never alters the watch. A read by Bios leaves the data the
   app would have read untouched: after a Bios read, a sync by the app
   still receives the same days. Test: Bios reads, then the app syncs,
   then the app's daily values for those days are present and equal.
3. Decoder. The daily record decodes into per-minute heart rate,
   five-minute blocks, sleep segments and stages, nightly HRV, resting
   heart rate and step totals, each with its UTC timestamp. Validated
   on the saved captures against the COROS app's numbers for the same
   days before the decoder touches Bios. Unknown tags are counted and
   logged, never guessed; a file whose header the decoder does not
   recognise yields no readings and marks the source "needs attention"
   (fail closed).
4. Bios adapter. A new source type, "COROS watch (direct)", reads the
   watch on the sync worker's schedule when the watch is in range,
   emits readings under its own source id with deterministic ids
   (replace-dedup), sits in the count-source order like any other
   witness, and reports through the source-liveness table (cadence:
   daily record once per sync, nightly HRV once per day). It sends only
   the four frame kinds recorded from the app (login, file read, flow
   control, acknowledge). No other byte goes to the watch.
5. Coexistence. The COROS app may stay installed, logged out, for
   firmware updates and GPS assistance files when the owner wants them.
   While the app is logged in and connected, Bios does not open the
   watch; while Bios holds the watch, the app cannot. The owner picks
   which one reads, in Bios settings; default after this spec ships:
   Bios.
6. Known devices, what they report, when they last synced. The Data
   Sources screen lists every known device and source (the watch, the
   phone, each Health Connect writer, each vendor API), and for each: the
   metrics it has ever delivered, and the timestamp of its last delivery
   (newest reading stored, not the worker's last run). The direct watch
   source appears there the day it exists. This criterion covers all
   sources, not only this one; it is the owner's ask of 2026-10-22
   ("a way to know which devices is known, what data they report and
   their last synced timestamp"). Backed by what Bios already keeps:
   `SourceHealthStore` (last delivery, last error) and the readings table
   (metrics and newest timestamp per source).
7. Tools stay with the owner. The capture parsers, the descriptor
   dumper and the decoder live in the Bios repo (`tools/coros-ble/`),
   with the saved captures' derived files but never the bugreports
   (they hold the whole phone).

## Limits

| Cost | Bearer (seat) | Seat | Balance as the bearer sees it | Fires when |
| --- | --- | --- | --- | --- |
| The watch may require a secret only the app holds: the login frame's 32-byte blob differs between the two captures (19-10 and 22-10). Chasing it means reverse-engineering the packed app at runtime, a rooted phone or an emulator, days of work with no guarantee. | owner | filled | Worth one evening of replay (login omitted, login replayed, blob zeroed); not worth rooting or weeks. "Go for it" 2026-10-22 on the gate as the first step. | Gate 1 fails in all three variants and the blob has no visible derivation from the captures → stop; the goal reverts to the Training Hub bridge. |
| Writes to the watch can reset or reboot it: SySS showed unauthenticated characteristics and an out-of-bounds read that reboots the PACE 3 (SYSS-2025-028). A malformed frame from Bios could end an activity or wipe settings. | owner | filled | A reboot during a test is tolerable once; data loss is not. The frames Bios sends are byte copies of the app's own; his go 2026-10-22. | Any watch reboot, reset or lost activity during or after a Bios session → build stops until the frame that caused it is identified. |
| Days that no client syncs are lost when the watch's record buffer rotates. With the app logged out, Bios is the only reader; a silent failure in Bios loses nights. | owner | filled | Covered by the source-liveness push if the cadence table lists this source (his 2026-09-28 answer: pushable, names the action). | The "COROS watch (direct)" row reads stale for more than 24 h without a push → the liveness wiring is wrong, fix before shipping. |
| Firmware updates and GPS assistance arrive only through the app. Keeping the app installed for those is a residual third-party dependency, the very thing the governing limit rejects. | owner | filled | His governing limit 2026-10-22: "not depend on a third party app like coros". Balance: the ingestion path must not depend on the app at all; an occasional manual login purely for a firmware flash or GPS almanac, with no data read, is a tolerated exception, not a dependency of the pipeline. | The app becomes required for any routine data read, not just a firmware flash → the goal has not met the intent, reopen it. GPS fix over two minutes or a wanted firmware release unappliable → manual login for that alone. |
| Time. This is intrinsic work; money is the owner's priority #1 (d019). | owner | filled | His 13-10 rule: intrinsic projects take free time by his own sort, never the Work blocks. Evenings and the empty 19:30–23:30 band. | A Work block goes to this build → park it until the day's pipeline work is done. |
| COROS's terms (coros.com/terms, 2023-04-23) §4(e) and §4(h) govern the Service, their cloud; reading one's own device is outside it. Decompiling the app for interoperability is allowed by Directive 2009/24/EC art. 6 when the information is not otherwise available, and it is not. | everyone who keeps those rules (pre-existing seat) | filled | Rows exist: terms read 2026-10-14, Directive cited here. Cost to the pair: none while nothing from the decompiled app is published; the decoder may be published, COROS's code, keys or descriptor text may not. | Any COROS code, key or protobuf descriptor text is committed to a public repo → remove it; the decoder stays. |
| Other Bios owners with other COROS models or firmware. The frames and the record layout were read from one Kiprun GPS 500 on firmware 3.0808.0. | other owners | empty | Unknown until one exists. | A second device yields no readings or wrong ones → the source marks "needs attention", nothing is guessed. |
| Battery. A Bluetooth client that polls keeps the watch's radio awake. | owner | empty | Unmeasured. | Watch battery drops faster than with the app over three days → lengthen the cadence. |

## Open

- The 16-byte blob (limit 1): same across the two captures or not, to be
  checked first; it is the cheapest observation available.
- Which of the four files the app reads each sync are needed for the
  goal. Type 0x05 holds the daily record; the sport file (type 0x00 per
  `SynFileType`) is not in the goal.
- Whether the watch keeps one read cursor per client or re-serves by
  offset; criterion 2 decides it empirically.
- Gadgetbridge has no COROS driver; if gates 1 to 3 pass, offering the
  frames and the decoder upstream is a later spec, not this one.
