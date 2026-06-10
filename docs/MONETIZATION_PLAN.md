# Bios Ecosystem Monetization Plan

> Rewritten 2026-06-08. Supersedes the prior consumer-tiered "reciprocity" plan
> (see [§ What changed](#what-changed-and-why-2026-06-08)). This is an **ecosystem-wide**
> plan — Bios the backbone plus its companions — not a single-app pricing sheet.

## The two problems this plan solves

Bios has two structurally linked problems, and naming them precisely is half the answer:

1. **Data learning** — how do we improve detection algorithms when raw data never leaves the device?
2. **Revenue** — how do we fund the ecosystem when the manifesto forbids the cloud, data sale, ads, and engagement loops?

**They are the same wall.** Both are blocked by one rule: *data can't leave the device.* You can't
aggregate it to train, and you can't aggregate it to sell.

**And that wall is the moat, not just the cost.** The architecture that makes surveillance-monetization
impossible is the same architecture that makes *consented, owner-driven sharing* uniquely valuable and
ethical. The answer to both problems is one piece of infrastructure: **the consent rail** — the owner
owns their data and explicitly chooses to share a scoped slice of it, with a named recipient, for a
named purpose (their clinician, a trial they enrolled in, research-to-improve, or no one). That single
rail is the learning model, the revenue model, and the dignity position at once.

---

## Guiding constraints (from the manifesto + ECOSYSTEM_BOUNDARIES)

Any model must hold these — they are not negotiable, they are the product:

- **On-device by default. Nothing leaves the device unless the owner explicitly sends it.**
- **No data sale. No ads. No third-party tracking SDKs.**
- **Instrument, not coach.** No push-side evaluation, no engagement loops, no streaks/goals/nudges.
  ([MANIFESTO.md](MANIFESTO.md) P7; [ECOSYSTEM_BOUNDARIES.md](ECOSYSTEM_BOUNDARIES.md) pull-side design rule.)
- **Full intelligence for everyone.** No feature gates on detection; a paywall never hides a health signal.
- **Bios does not ask for data.** It works with whatever the owner gives.
- **Free protection tiers stay free** (Virgil's safety net is never paywalled).

---

## What changed and why (2026-06-08)

The prior plan was a conventional health-app plan that no longer fits. Three things were retired:

| Retired | Why |
|---|---|
| **The "reciprocity" model** (free users pay with anonymized aggregated data; private users pay €/mo to opt out) | Contradicts the hardened stance. Even on-device-aggregated, differentially-private contribution is a *default-on* outbound data flow — and "Bios does not ask for data" + "owner decides" can't support default-on. Replaced by **opt-in consent only**. Corollary: when privacy is *total and default*, there is nothing to sell people to "keep private" — so the "pay for privacy" consumer tier loses its rationale entirely. |
| **Insurer / employer-wellness streams** | Aggregated workforce/risk dashboards for insurers and employers are the surveillance-adjacent model the manifesto exists to refuse, even "anonymized." Off-brand. Removed. |
| **In-Bios "Condition Packs"** (Cardiac/Metabolic/Women's/Mental as paid add-ons inside Bios) | These *are* the specialist companions in the real architecture (mood → W2F, etc.). Feature-gating them inside Bios also violates "no feature gates." Folded into the per-app map below. |

Preserved from the old plan: radical transparency, pricing discipline, the risk-table format, and the
instinct that "privacy is a choice" — sharpened into the consent rail.

---

## Architecture context: backbone + specialists

Monetization follows the boundary in [ECOSYSTEM_BOUNDARIES.md](ECOSYSTEM_BOUNDARIES.md):

- **Bios** is the domain-neutral backbone — ingestion (9 adapters), encrypted storage, personal
  baselines, the 12 generic condition patterns, the ContentProvider. It is **not** a vertical and
  **not** a consumer cash cow. It is free, full, private-by-default infrastructure.
- **Companions** are the apps people install for a domain. Some are clinical-grade instruments
  (Fil, W2F); some are consumer/lifestyle (SoulRadio, Idun, Smokeless); some are safety (Virgil).
  They monetize differently — see the per-app map.

The revenue does not come from the backbone. It comes from (a) the **clinical specialists** sold to
institutions and (b) **grants** funding the backbone as public-health infrastructure, with consumer
apps as a validating, funnel-building base.

---

## Problem 1 — Data learning, without raw data leaving the device

Three layers, in priority order. None requires betraying on-device privacy.

1. **Public research datasets for the global cold-start.** Validated open corpora exist for exactly the
   hard problems — gait (PhysioNet, GaitRec), fall detection (SisFall, UMAFall), PPG/HR, accelerometry.
   Calibrate the *shipped baseline* on these. This is the answer to the recurring calibration gap
   (e.g. Virgil's fall thresholds): you do not need *your users'* events if the physiology is covered
   by public data. **Highest-leverage, lowest-friction, do this first.**
2. **On-device n=1 personalization.** Already the pattern — Bios personal baselines/z-scores, Fil's
   drift engine, Virgil's `ActivityBaseline` EWMA. The model adapts to the individual, on their device,
   sharing nothing. Real learning; just not global.
3. **The consent rail — opt-in, owner-driven contribution** for population-level gains. Explicit,
   per-purpose, revocable. Two forms:
   - **Donate-to-improve:** owner chooses to send a scoped, de-identified slice to improve the shared
     model (Virgil's `FalseAlarmSnapshot` opt-in is already this shape).
   - **Federated updates** (optional, manifesto call): model weight-deltas sync, raw data never does.
     Stretches the no-cloud reading — *flagged as an explicit decision for the owner of the project,
     not assumed.*

**Hard line:** no default-on contribution of any kind. Population learning is opt-in or it doesn't happen.
We trade the silent, total data advantage of surveillance competitors for the dignity position — and
substitute public datasets + n=1 + consented donation for it.

---

## Problem 2 — Revenue: monetize institutions and missions, not the sovereign user

Per-app consumer pricing is **pocket money and fights the values** (one-time €3 → ~€2 net → needs
~900 sales/mo, decaying). So the engine is elsewhere.

### Stream A — Clinical B2B (the primary engine)

The clinical specialists are the real revenue.

- **Fil** (MS / neurology) and **W2F** (mood / bipolar) are clinical-grade instruments: gait/drift/fall,
  typing-cadence/ADA-1/HDA-1, active micro-tests.
- The buyer is **not the patient** paying €3 — it's **neurology & psychiatry clinics, academic
  researchers, and pharma running decentralized clinical trials (DCTs)**, who pay substantially for
  *compliant, privacy-preserving remote measurement* that runs on the patient's own phone.
- The patient app stays **free**; the patient **consents** to share their data stream with *their*
  clinician or *their* enrolled trial — pull-side, owner-decides. The consent rail is the delivery mechanism.
- **Pricing shapes:** per-active-patient/month, per-trial site license, or per-study data-collection
  contract.
- **Prerequisite & moat:** clinical validation against gold standards. This is the gating investment
  (cost + 6–18mo timeline) — and once done, it's a deep moat few indie health apps can cross.

> Why this is manifesto-clean: nobody's data is sold. The patient owns it and chooses to share it with
> the institution already in their care. Bios/Fil/W2F are paid as the *instrument*, not as a data broker.

### Stream B — Grants fund the backbone

Bios-the-backbone can't (and shouldn't) monetize consumers. Fund it as **public-health infrastructure**:

- SDG 3 (health) + privacy/digital-sovereignty grant programs (EU Horizon/EIC, privacy-tech, digital-
  health-equity). The existing SDG-alignment map is the framing asset.
- Non-dilutive, mission-aligned, and it funds the substrate that enables Stream A.

### Stream C — Paid consumer apps (the base layer)

Honest paid apps — pocket money individually, but real, and they validate + build a funnel:

- One-time / honest pricing (the **SoulRadio €3.99** model — bought, not extracted).
- Optional **ecosystem "supporter" license / bundle** — pay once to support the family of apps; unlocks
  nothing gated (there are no health gates), purely voluntary patronage of the mission.

### Stream D — explicitly NOT doing (the guardrail list)

- ❌ Selling data, aggregated or otherwise.
- ❌ Insurer risk-data or employer workforce-health dashboards.
- ❌ Ads, affiliate-surveillance, "works best with" kickback funnels as a core model.
- ❌ Default-on data contribution dressed as "community."
- ❌ Feature-gating any health signal behind a paywall.

---

## Per-app monetization map

Archetypes (col. 2) follow the portfolio-wide taxonomy in
[KB monetization-doctrine.md](../../Miam/miam-knowledge-base/docs/monetization-doctrine.md): **1**=instrument/B2B,
**2**=consumer, **3**=free commons. The values are universal; the mechanism follows each app's nature.

| App | Archetype | Role | Model | Notes |
|---|---|---|---|---|
| **Bios** | **1 + 3** | Backbone / hub | **Free, full, private-by-default.** Funded by grants (Stream B) + as the substrate enabling Stream A. Optional voluntary supporter / one-time "Pro" for power-user *export* extras only (never gating health signal). | No "pay for privacy" tier — privacy is total, nothing to sell. |
| **Fil** | **1** (free patient app) | Clinical specialist (neurology/MS) | **Clinical B2B (Stream A).** Patient app free; clinics/MS-research/DCTs license the instrument. | Gated on clinical validation. Primary engine. |
| **W2F** | **1** (free patient app) | Clinical specialist (mood/bipolar) | **Clinical B2B (Stream A).** Patient app free; psychiatry/mood-research/DCTs license it. | Gated on clinical validation. Primary engine. |
| **Virgil** | **3** (+ optional 2) | Safety (standalone) | **Protection tier free, always.** Optional low-cost *convenience* tier (family dashboard, check-in history). Possible secondary B2B: elder-care / assisted-living licensing. | Never paywall the safety net. |
| **SoulRadio** | **2** | Ambient (standalone) | **One-time €3.99** (shipped decision). Subscription as library grows, later. | Base layer. Wellness-audio category. Pure consumer — no data bus, no B2B. |
| **Idun** | **2** | Lifestyle (longevity meals) | **Paid consumer**, subscription the eventual engine (Open Roots brand). | Base layer + content brand. Needs commercial-clearance first. |
| **Smokeless** | **2** (+ minor 1) | Substance ledger (standalone) | Free or low one-time **when ready** (not release-ready per 2026-06-08). Optional opt-in research contribution. | Base layer. |
| **Anastasis / Carnet** | **2** | Lifestyle specialists | TBD — likely free or low one-time. | Base layer. |

---

## Honest economics & sequencing

Tie to reality (see the income-pipeline frame):

- **Consumer apps (base) = pocket money + validation, now.** They do **not** replace salary. Don't bank
  near-term runway on them.
- **Clinical B2B + grants = the real money, 6–18-month horizon.** This is where the ecosystem becomes a
  business — and it requires the upfront investment in clinical validation + the consent rail + grant applications.
- **Net:** "monetizing the ecosystem" is a multi-year build that funds the long-term endgame, not the
  next quarter. Sequenced:

| Window | Move |
|---|---|
| **Now** | Ship SoulRadio (€3.99, done) + Idun (after clearance) — base validation + funnel. Build the **consent rail** in Bios (serves learning *and* B2B). |
| **Mid (6–12 mo)** | Clinical validation for Fil / W2F. First grant applications for Bios (SDG/privacy-tech). First clinic/research pilots. |
| **Long (12–24 mo)** | Clinical B2B contracts + DCT data-collection deals. Research-grade consent rail at scale. Ecosystem supporter license. |

---

## The consent rail — build once, serves everything

The single highest-leverage build in this whole plan. A Bios-side capability where the owner can:

- select a **scoped slice** of their data (metrics, time window),
- choose a **named recipient** (their clinician, a specific enrolled trial, "research-to-improve", export-to-self),
- with **explicit, revocable, per-purpose consent**, logged and inspectable.

This one rail is simultaneously: the **data-learning** mechanism (Problem 1, layer 3), the **clinical-B2B**
delivery mechanism (Stream A), and the concrete expression of the **dignity** principle (owner decides).
It is the place where the constraint becomes the moat. Prioritize it.

**Operational detail — how partners plug into the rail without buying data or forking the app (worked
examples incl. concurrent multi-study enrollment): [B2B_PARTNER_MODEL.md](B2B_PARTNER_MODEL.md).**

---

## Risks & guardrails

| Risk | Mitigation |
|---|---|
| Drift back toward surveillance-monetization (the gravitational pull of "just aggregate a little") | This doc's Stream D no-list is a standing guardrail. Any proposed stream gets tested against "does data leave the owner's control without per-purpose consent?" If yes, it's out. |
| "You're selling health data" accusation | We don't. Publish the consent rail's exact mechanics; open-source it. The patient is the sharer, the institution the recipient, always per-consent. |
| Clinical B2B never closes (validation cost sinks it) | Validation is staged and partly grant-fundable (Stream B can fund the work that unlocks Stream A). Public datasets de-risk the algorithmic side. |
| Consumer apps mistaken for the business | Stated plainly above: base layer, pocket money, not salary. The business is B2B + grants. |
| Regulatory (medical claims) | "Instrument, not coach / inform not diagnose" positioning holds. Clinical-grade claims (Fil/W2F B2B) require the validation + appropriate regulatory path (e.g. CE/FDA SaMD) — budget for it as part of Stream A. |
| Federated-learning / weight-sync stretches no-cloud vow | Flagged as an explicit owner decision, not assumed. Default plan ships without it (public datasets + n=1 + opt-in donation suffice for v1). |

---

*This plan turns the ecosystem's defining constraint — data sovereignty — from a monetization handicap
into its moat. We don't monetize the user; we let the user choose to share, and we charge the institutions
and missions that benefit from privacy-preserving, consented measurement.*
