# Bios Governance: Async-First, Written Decisions

> Inspired by Alan's no-meetings culture. Adapted for an open-source health project where every decision must be auditable and every contributor must be able to participate regardless of timezone.

---

## Core Principle

**If it wasn't written down, it didn't happen.**

Every decision — technical, product, or policy — is made in writing. No decision is made in a call, DM, or hallway conversation unless it is subsequently documented in a GitHub issue with the rationale and outcome.

---

## How Decisions Are Made

### 1. Propose in Writing

Open a GitHub issue with:
- **What** you want to change or build
- **Why** it matters (link to the roadmap principle it serves)
- **Trade-offs** you considered
- **Scope** — what this does and does not include

Use the `proposal` label. Tag relevant maintainers.

### 2. Discuss in Threads

All discussion happens in the issue thread. Threaded replies keep context together. No side channels.

**Response expectations:**
- Maintainers respond within 48 hours (acknowledge, not necessarily decide)
- Contributors can comment at any time — no timezone penalty
- Silence after 48 hours from all tagged maintainers = soft approval to proceed

### 3. Decide with Rationale

The decision is recorded in the issue:
- **Decision:** what was chosen
- **Rationale:** why (not just "we discussed and agreed" — the actual reasoning)
- **Dissent:** if anyone disagreed, their argument is preserved (disagreement is signal, not noise)

Close the issue with the `decided` label. Link from any resulting PR.

### 4. Implement with Traceability

Every PR references the decision issue. Reviewers can trace any code change back to the reasoning behind it.

---

## What Gets a Proposal

Not everything needs a formal proposal. Use judgment:

| Needs a proposal | Just do it |
|---|---|
| New condition pattern | Bug fix with obvious cause |
| Architecture change | Dependency version bump |
| New data source adapter | Typo / docs fix |
| Privacy model change | Test coverage improvement |
| Alert content policy change | Refactor within existing patterns |
| New region config | Adding translations |
| Anything touching wipe/erasure | CI/build improvements |

**When in doubt:** open an issue. The cost of writing is low; the cost of an undocumented decision is high.

---

## Roles

### Maintainers
- Final decision authority on proposals within their domain
- Responsible for 48-hour response SLO on tagged proposals
- Can merge PRs in their domain

### Contributors
- Can open proposals, comment, and submit PRs
- Cannot merge without maintainer approval
- Encouraged to dissent — protected by the dissent-preservation rule

### Owner (project lead)
- Tiebreaker when maintainers disagree
- Veto on anything that violates non-negotiable principles (see ROADMAP.md)
- Veto is rare and always explained in writing

---

## Meetings Policy

**Default: no meetings.**

Meetings are permitted only when:
1. A written discussion has reached genuine impasse (not just "it would be faster to talk")
2. All participants agree a synchronous session would help
3. The outcome is documented in the original issue within 24 hours

A meeting without a written summary is a meeting that didn't happen.

---

## Communication Channels

| Channel | Purpose |
|---|---|
| **GitHub Issues** | Proposals, decisions, bug reports |
| **GitHub Discussions** | Open questions, ideas not yet ready for a proposal |
| **Pull Requests** | Code review, implementation discussion |
| **Git commit messages** | "Why" behind each change (conventional format required) |

No Slack, no Discord, no email threads for project decisions. These are allowed for social/community purposes but never for technical decisions.

---

## Why This Matters for Bios

Bios protects the owner. That protection is only as trustworthy as the decisions behind it. When a user asks "why does Bios handle my data this way?" — we can point to a specific issue, with the reasoning, the trade-offs considered, and any dissent. That's not just good governance; it's accountability to the people we protect.
