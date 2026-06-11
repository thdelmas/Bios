# Bios B2B Partner Model — instrument, not data broker

> How institutions (trials, clinics, research cohorts) become paying partners **without** buying
> user data and **without** forcing one-app-per-partner. This is the operational detail behind
> [MONETIZATION_PLAN.md](MONETIZATION_PLAN.md) (Stream A + the consent rail) and is governed by
> [PRIVACY_ARCHITECTURE.md](PRIVACY_ARCHITECTURE.md). Worked 2026-06-08.

## The core principle

**You sell the privacy-preserving measurement instrument + the consent/compliance rail. You never
sell user data.** The owner's device is the single point of capture *and* the single fan-out hub.
Every partner is a scoped, siloed, independently-billed *recipient* hanging off the owner — never a
fork of the app, never a buyer of a data pool.

Two things wearing the word "data revenue", kept strictly distinct:
- **Selling user data** (taking what users gave you, reselling it): **never.** Architecturally impossible — you don't hold it.
- **Licensing a measurement instrument to an institution that collects its *own* consented data from its *own* enrolled patients**: a clean, proven, multi-billion-dollar business (eCOA / decentralized-trial / digital-biomarker platforms — Medable, Castor, Cambridge Cognition, ActiGraph). Data flows patient → *their* trial/clinic (already consented), never patient → you → buyer.

## What a partner buys / doesn't buy

| Buys | Does NOT buy |
|---|---|
| A license to use Bios as a remote-measurement instrument for *their* cohort | User data (you don't sell it; you don't hold it centrally) |
| Reduced data-compliance burden (on-device, consented, scoped — shrinks their GDPR/HIPAA/IRB risk) | A custom app / a fork |
| An integration (API/export) to pipe **consented** results into their data system (EDC) | Visibility into any user outside their own consented cohort |

The privacy architecture is the **selling point**, not an obstacle: data-governance is one of the
biggest costs/risks in a trial, and "stays on the patient's device, shared only by explicit per-study
consent" makes the sponsor's burden smaller.

## The consent rail IS the multi-tenancy (no fork, ever)

A partner is **a named recipient + a config + an API key** — not a codebase.

- **One instrument app per *domain*** (Bios-generic; Fil neuro; W2F mood). Never one per partner.
- A new partner onboards as: a recipient registered in the partner console, a config (which metrics,
  cadence, study ID), an API/export to their system, optional branding badge. **A new partner is a
  database row, not a sprint.**
- Where partner-specific needs go: **configuration** (metrics/schedule/thresholds), **theming/white-label**
  (one codebase, N skins), **integration** (a stable API). Genuinely-custom asks → a bounded, separately-paid
  integration — **never a maintained fork.**
- For partners who already have their own app: **license the measurement engine as an SDK** — they embed
  it, you build zero apps. Maximum picks-and-shovels.

> Discipline: the pressure to fork "for one big paying client" is how a product company quietly becomes
> a consultancy. Build the config/API layer early so "yes" to a partner means *configure*, not *fork*.
> For a solo founder with no medical background, the product architecture is the *only* survivable version.

## Worked example — one study

**NorthTrial**, a CRO running a Phase 2 metabolic-drug study, needs continuous **sleep, resting HR, HRV,
activity** from at-home participants (safety + secondary endpoints). Generic physiology = Bios's
domain-neutral backbone, no specialty required.

1. **They buy:** a per-study license + per-active-participant/month fee + a one-time EDC integration.
   *(Illustrative: study license low-five-figures, per-participant-month in the tens of dollars — directional, not a quote.)*
2. **Participant experience:** installs **the same free Bios** everyone uses (full private hub, theirs
   forever). At enrollment, a code; in the consent rail: *"Share {sleep, RHR, HRV, activity} with
   NorthTrial Study #NT-204 for the study duration?"* — neutral, explicit, scoped, revocable. Using Bios
   never requires enrolling.
3. **Data flow:** device → (consented, scoped) → NorthTrial's EDC. Never device → Bios server → sold.
   The trial owns its trial data under its own IRB consent. Bios is the ruler and the pipe.
4. **No fork:** NT-204 is a recipient + config + API export in the partner console. SouthTrial is the next row.
5. **Money + free hub intact:** NorthTrial pays; the participant pays nothing and gets the same full Bios;
   the non-millionaire who never enrolls gets the identical free hub. No feature gate, no data sale.

## Worked example — the same user in TWO studies at once

Add **SouthTrial #ST-110** wanting {HRV, sleep, temperature}, concurrent with NT-204. This isn't an edge
case to handle — it's the model proving it was right.

- **Two independent grants, one owner.** Grant A → NT-204 {sleep, RHR, HRV, activity}; Grant B → ST-110
  {HRV, sleep, temperature}. Independent scopes/windows/recipients; revoking A never touches B. N studies = N rows.
- **Overlapping metrics don't collide — data isn't "spent".** The device **captures HRV once** (from the
  wearable) and **fans it out per grant** to both pipes, because the owner authorized both. No contention,
  no double-sampling.
- **Partners stay siloed.** NorthTrial sees only NT-204; SouthTrial sees only ST-110. **Neither knows the
  user is in the other.** The two grants assemble in exactly one place: the **owner's consent dashboard.**
  Cross-study linkage exists only at the owner — never on your server, never between partners.
- **Billing is per-grant.** One user, two studies = two participant-month line items, one per sponsor.
  Revenue scales with *grants*, not users.

**Why this is the whole point** — the test the other architectures fail:
- *One-app-per-partner:* two studies = two apps = HRV captured twice, fragmented, batteries doubled.
- *Central data lake (surveillance model):* you hold all data and "share" it = you're the broker between
  two sponsors and a user = the data-sale risk you refuse.
- *Owner-as-hub (this model):* capture once on device, fan out per independent consent, you broker nothing.
  Concurrency is trivial **and** ethical. The thing that made it private is the thing that made it scale.

## Boundaries & rules

- **Clinical-protocol / co-enrollment conflicts are the *trials'* job, not Bios's.** Some trials forbid
  co-enrollment via their own eligibility rules. Bios is the neutral instrument — it measures and shares
  what the owner consents to; it does not police trial protocols.
- **Optional owner-facing co-enrollment notice** (not a block): *"You're already sharing with another study;
  some trials restrict this — check with your investigators."* Factual, owner-facing, no judgment — consistent
  with "instrument, not coach." Owner decides.
- **Server design rule (bake in early):** the partner console learns the *minimum per grant* (e.g. "U active
  in NT-204" for billing) and **never correlates grants across partners.** This is the line that keeps you an
  instrument vendor and not, accidentally, a data broker.

## Why Bios-generic is likely the *first* B2B door

Bios-direct has a **lower validation bar than the specialists.** For background physiology it mostly
**aggregates already-validated wearable data** (Oura HRV, Garmin sleep, Health Connect activity) — leaning
on the device-makers' validation rather than inventing a biomarker. Fil's novel gait algorithm needs its own
clinical validation before a trial trusts it; Bios orchestrating Garmin HRV does not. So the sequencing is
likely: **Bios-generic remote-physiology monitoring first** (lower validation, no specialty, it's the substrate
you're building anyway) → specialist digital-biomarker plays (Fil, W2F) after a clinical champion + validation exist.

## Why this fits a non-medical solo founder

You are the **instrument/tech vendor**, not the data-interpreter. You provide the validated measurement +
privacy rail + integration (your skill). The partner brings the clinical interpretation, the patient
relationship, and the regulatory wrapper (their skill). Picks and shovels: you make the shovel; you don't
mine the gold or own the claim. *(Regulatory note: confirm the instrument-vs-medical-device line with a
regulatory consult before any clinical-care sale — not now, but before money changes hands.)*
