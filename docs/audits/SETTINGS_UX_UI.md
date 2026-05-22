# Settings UX / UI Audit

**Scope:** [android/app/src/main/java/com/bios/app/ui/settings/](../../android/app/src/main/java/com/bios/app/ui/settings/) — the Settings tab reached via the bottom-nav `settings` route ([MainActivity.kt:340-353](../../android/app/src/main/java/com/bios/app/ui/MainActivity.kt#L340-L353)).
**Date:** 2026-05-21
**Branch:** `fix/db-downgrade-fallback`
**Auditor:** Claude (Opus 4.7)

Files reviewed:

| File | LOC |
|---|---|
| [SettingsScreen.kt](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt) | 496 |
| [SettingsNotificationsCard.kt](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsNotificationsCard.kt) | 100 |
| [PdfSummaryButton.kt](../../android/app/src/main/java/com/bios/app/ui/settings/PdfSummaryButton.kt) | 158 |
| [SettingsFeedbackCard.kt](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsFeedbackCard.kt) | 60 |
| [CompanionAppsRow.kt](../../android/app/src/main/java/com/bios/app/ui/settings/CompanionAppsRow.kt) | 58 |
| [DisconnectAlertToggle.kt](../../android/app/src/main/java/com/bios/app/ui/settings/DisconnectAlertToggle.kt) | 53 |
| [ConnectableSourceRow.kt](../../android/app/src/main/java/com/bios/app/ui/settings/ConnectableSourceRow.kt) | 42 |
| [PasteTokenDialog.kt](../../android/app/src/main/java/com/bios/app/ui/settings/PasteTokenDialog.kt) | 74 |
| [SettingsRow.kt](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsRow.kt) | 22 |

---

## Executive summary

The Settings screen is functional and largely consistent with the rest of the app, but is **carrying ~6 months of additive growth without re-aligning the information architecture**. The most pressing issues are:

1. **Stale UI state after destructive actions** — `Delete All Data` does not reset the locally-remembered `isOuraConnected` / `isWithingsConnected` / … flags, so connection chips lie until the next process restart. ([SettingsScreen.kt:54-70](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L54-L70), [SettingsScreen.kt:410-413](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L410-L413))
2. **Security-meaningful actions are one-tap** — Privacy Tier `Private → Community` toggle has no confirmation, and the token-paste field renders secrets in clear text with no `PasswordVisualTransformation`. ([SettingsScreen.kt:336-345](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L336-L345), [PasteTokenDialog.kt:53-59](../../android/app/src/main/java/com/bios/app/ui/settings/PasteTokenDialog.kt#L53-L59))
3. **IA conflates configuration with data entry** — "Add Lab Values", "Track BBT", "Log period start" are workflow entry points sitting inside the **Data Sources** card. They belong in a Log/Entry surface, not in Settings.
4. **Accessibility floor is below baseline** — every icon uses `contentDescription = null`, all copy is hardcoded (no `stringResource`), and there are no semantics roles. ([CompanionAppsRow.kt:53](../../android/app/src/main/java/com/bios/app/ui/settings/CompanionAppsRow.kt#L53), [SettingsScreen.kt:374](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L374))
5. **Silent failure modes** — exports swallow exceptions in `finally { isExporting = false }`; token-paste never validates the token; the owner only learns at the next ingest cycle. ([SettingsScreen.kt:213-232](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L213-L232))
6. **Code hygiene at the cap** — `SettingsScreen.kt` is **496 / 500** lines with two dead imports. One more adapter and the project's pre-commit length check fails. ([SettingsScreen.kt:26-28](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L26-L28))

The current screen is **safe to use** but no longer scales. A small refactor + a handful of accessibility & confirmation fixes would address the bulk of it.

---

## 1. Information architecture

### 1.1 Workflow actions buried inside a configuration card

[SettingsScreen.kt:182-192](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L182-L192) places six `SettingsActionButton` rows inside the **Data Sources** card:

- Add Lab Values
- Add clinical reading
- Log sleep
- Track BBT
- Log period start
- Data coverage

Of these, only **Data coverage** is genuinely a settings-adjacent surface (it inspects coverage). The other five are *recurring data-entry workflows* that the owner will tap weekly or daily. Forcing them through `Settings` makes them invisible, breaks the manifesto promise that "Bios is the instrument the owner reads," and inflates the Data Sources card.

**Recommendation:** move these into a dedicated `Log` / `Self-report` tab (a thin shell already exists per [SELF_REPORTED_DATA_HOME.md](../SELF_REPORTED_DATA_HOME.md)), or surface them as a quick-action sheet on the Home screen. Settings → Data Sources should only describe and configure data *sources*.

### 1.2 Highest-stakes setting is the least visible

`Companion Apps` (the per-package owner allowlist that controls what other apps can read from `BiosHealthProvider`) is one row inside Data Sources. Yet it is the single highest-consequence security surface on the screen — granting it leaks live vitals to another process. It should not be co-mingled with "Oura Ring".

**Recommendation:** promote **Companion Apps** to a top-level card alongside Privacy. Keep the pending-count badge.

### 1.3 No app-bar, no search, no anchors

The screen is a single `Column { verticalScroll(...) }` with **7 cards** today and a clear trajectory toward 10+ as more adapters land. There's no top-app-bar with a title, no scroll-anchor jump menu, no search. On a typical phone, "Delete All Data" is 4 scroll-pages below the top. This is fine at the current size but will become a usability cost.

**Recommendation:** wrap the screen in `Scaffold { TopAppBar(title = { Text("Settings") }) }`. The current inline `Text("Settings", headlineLarge)` ([SettingsScreen.kt:93](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L93)) duplicates the bottom-nav label and is the only screen affordance.

### 1.4 Card visual hierarchy is flat

Every card uses identical `surfaceVariant` + 16dp padding ([SettingsScreen.kt:96](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L96), [197](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L197), [317](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L317), [361](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L361), [394](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L394)). There's no visual difference between **Privacy Tier** (data leaves device!) and **About** (version string). A subtle accent on privacy/destructive sections would help the eye triage.

---

## 2. State management & correctness

### 2.1 Connection flags go stale after `Delete All Data`

[SettingsScreen.kt:54-70](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L54-L70) seeds five `mutableStateOf(viewModel.…hasToken())` once at composition. The dialog confirm handler ([SettingsScreen.kt:410-413](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L410-L413)) only resets `totalReadings = 0`. If `DataDestroyer.destroyAll(context)` clears the OAuth tokens (it should — verify), the UI still renders **"Connected"** for each provider until the screen is recomposed from scratch.

**Severity:** medium — the UI lies about the state of the privacy-sensitive token store.

**Fix:** either drive each connection chip from a `viewModel.ouraTokenStore.observeHasToken(): Flow<Boolean>` exposed on the token stores, or — minimally — flip all five flags inside the confirm handler:

```kotlin
onClick = {
    com.bios.app.platform.DataDestroyer.destroyAll(context)
    totalReadings = 0
    isOuraConnected = false
    isWithingsConnected = false
    isWhoopConnected = false
    isGarminConnected = false
    isPolarConnected = false
    showDeleteDialog = false
}
```

### 2.2 Privacy tier is not observable

[SettingsScreen.kt:76-80](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L76-L80) reads `privacy_tier` from raw `SharedPreferences` inside a `remember { ... }`. If anything else in the codebase ever changes that key (a CLI bridge, a LETHE agent intent, a Settings widget) the UI is stale. Consider a `PrivacyTierStore` with a `Flow<PrivacyTier>` similar to other repositories.

### 2.3 Two dead imports

[SettingsScreen.kt:26-28](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L26-L28) imports `DailyDigestWorker` and `PushRegistrationManager`. Both moved to `SettingsNotificationsCard.kt` and are no longer used in the host file. Lint will flag, and they add to the 496/500 line count.

### 2.4 File is one row away from the 500-line cap

`SettingsScreen.kt` is 496 / 500 lines (project rule, [CLAUDE.md](../../CLAUDE.md)). Adding a single adapter (Dexcom, Levels, Apple-Health-via-ADB-bridge) breaks the pre-commit hook. The per-adapter pattern is **4 repetitions × 5 adapters = 20 boilerplate blocks**:

- `var isXxxConnected by remember { mutableStateOf(viewModel.apiTokenStore.hasToken(XxxApiAdapter.PROVIDER_KEY)) }`
- `var showXxxDialog by remember { mutableStateOf(false) }`
- One `ConnectableSourceRow(...)` block
- One `if (showXxxDialog) { PasteTokenDialog(...) }` block

**Recommendation:** collapse to a single data-driven list:

```kotlin
data class ConnectableAdapter(
    val name: String,
    val providerKey: String,
    val helperText: String,
)

val adapters = remember { listOf(
    ConnectableAdapter("Oura Ring", OuraTokenStore.KEY, SettingsHelperText.OURA),
    ConnectableAdapter("Withings",  WithingsApiAdapter.PROVIDER_KEY, SettingsHelperText.WITHINGS),
    …
) }
```

…and render with a single `forEach`. Saves ~120 lines and makes adding Dexcom a one-line change.

---

## 3. Security & privacy

### 3.1 Token paste field renders secrets in clear text

[PasteTokenDialog.kt:53-59](../../android/app/src/main/java/com/bios/app/ui/settings/PasteTokenDialog.kt#L53-L59) uses a plain `OutlinedTextField` with no `visualTransformation = PasswordVisualTransformation()`, no `KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false)`. Implications:

- The token is visible to anyone glancing at the screen (clinic waiting rooms, transit, partner).
- The IME may offer autocorrect suggestions, capturing the token in the system's learned-words store.
- Screenshots in the recents-task overview show the secret in plaintext.

The file's own doc-comment claims "The dialog never logs the entered token" — which is true of Bios's own logging, but the OS and IME are not Bios.

**Fix:** add `visualTransformation = PasswordVisualTransformation()` (with a reveal toggle), `keyboardType = KeyboardType.Password`, `autoCorrect = false`. Consider `FLAG_SECURE` on the host activity while this dialog is up to prevent recents-screenshot leakage.

### 3.2 No token shape validation

`PasteTokenDialog` accepts any non-blank trimmed string and writes it straight into `EncryptedSharedPreferences`. A pasted Slack invite, an email address, or a stray clipboard fragment is silently stored and produces 401s at the next ingest cycle — with no signal back to the owner that the paste was wrong.

**Fix:** each adapter exposes a `validateTokenShape(s: String): Boolean` (regex against expected JWT / API-key shape) and ideally a lightweight `probe()` call that hits `/me` and returns 200/401. Surface the result inline in the dialog.

### 3.3 Privacy Tier toggle is one-tap, no confirmation

[SettingsScreen.kt:336-345](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L336-L345): switching from `PRIVATE` to `COMMUNITY` immediately calls `ContributionWorker.enqueueNextContribution(context)`. There is no confirm step, no "here's what will leave the device next" preview. For a system whose manifesto says "the owner decides what leaves the device — nothing does by default," giving up that default on a single tap is at odds with the spirit.

**Fix:** when transitioning `PRIVATE → COMMUNITY`, raise a confirmation sheet that previews **the exact aggregated payload** that will be sent on the next worker run. The current explanatory caption ([SettingsScreen.kt:349-356](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L349-L356)) is good but reads *after* the consequential tap.

### 3.4 Delete All Data lacks a friction gate

[SettingsScreen.kt:403-423](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L403-L423) uses a standard two-button AlertDialog. The destructive action is a TextButton with `error` content color, the dismiss is a TextButton with default color. On a smaller phone with thumb-reach Cancel/Delete buttons are easy to mistap.

**Fix:** add a type-to-confirm field ("Type DELETE to confirm") or a long-press-hold pattern. The Bios manifesto frames erasure as a feature, not a bug — making it slightly harder to trigger by accident protects the very autonomy the toggle is designed to express.

### 3.5 OAuth tokens may not be wiped on Delete All Data

`DataDestroyer.destroyAll` is called at [SettingsScreen.kt:411](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L411). Verify that it iterates **every** `apiTokenStore` provider key (Oura, Withings, WHOOP, Garmin, Polar), not just the encrypted database. Worth pinning a test that asserts `apiTokenStore.hasToken(...) == false` for each provider key post-wipe.

---

## 4. Accessibility (TalkBack, dynamic font, RTL, i18n)

### 4.1 Every icon is invisible to TalkBack

[CompanionAppsRow.kt:53](../../android/app/src/main/java/com/bios/app/ui/settings/CompanionAppsRow.kt#L53) — `ChevronRight` with `contentDescription = null`.
[SettingsScreen.kt:374](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L374) — `Shield` with `contentDescription = null`.

The Shield is doubly bad: it sits next to a button labeled "Privacy Dashboard" — Material's pattern wants the icon to *be* the primary affordance for screen readers ("Privacy Dashboard, opens").

**Fix:** every decorative icon stays `null`; every functional icon gets a real description (e.g. `contentDescription = "Open Privacy Dashboard"`).

### 4.2 Strings are not externalised

No `stringResource(R.string.*)` in the entire settings package — every label is a Kotlin literal. Blocks i18n, blocks RTL string-pair checking, and means any user-research run requires a code change. The `SettingsHelperText` object ([SettingsScreen.kt:480-486](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L480-L486)) is a step in the right direction but stops short of `strings.xml`.

### 4.3 Switch row labels have no `Modifier.semantics(mergeDescendants = true) { ... }`

`SettingsNotificationsCard.kt` and `DisconnectAlertToggle.kt` lay out a label + sub-copy next to a `Switch`. TalkBack will read the three nodes (title, sub-title, switch state) separately. Merging the row's descendants and giving it a single `toggleableState` semantics would give "Daily Digest, Morning summary of your vitals at 8 AM, switch, on" as one focusable node.

### 4.4 FilterChips lack predictable role labels

Privacy Tier ([SettingsScreen.kt:326-346](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L326-L346)) uses two `FilterChip`s with weight(1f). TalkBack reads them as generic chips. They're really a 2-state segmented-button — `SingleChoiceSegmentedButtonRow` is the Material 3 idiom and is announced as a radio group.

### 4.5 Color is the only signal for "Connected" / "Not Connected"

[SettingsScreen.kt:102-106](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L102-L106): "Connected" in primary, "Not Connected" in error. Failure for color-blind users. Pair with an icon (✓ / ⚠) or a status pill with text contrast guaranteed by tonal-background.

---

## 5. Consistency & polish

### 5.1 "Health Connect" row is a snowflake

The first row in Data Sources is a hand-rolled `Row { Text("Health Connect"); Text(if (hasPermissions) "Connected" else "Not Connected") }` ([SettingsScreen.kt:100-107](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L100-L107)) while every other adapter uses `ConnectableSourceRow`. The result is **no Connect / Disconnect affordance on Health Connect** — if it's "Not Connected," the owner is stuck. They have to discover the permission flow elsewhere.

**Fix:** convert Health Connect to a `ConnectableSourceRow` whose `onConnect` triggers the Health Connect permission intent.

### 5.2 BLE row reuses the wrong abstraction

[SettingsScreen.kt:166-172](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L166-L172) — "Air-quality sensor (BLE)" uses `ConnectableSourceRow` but both `onConnect` and `onDisconnect` navigate to the same screen with a code-comment justifying it. The right idiom is "Pair / Manage" (one button, chevron, navigates).

### 5.3 Export-button vocabulary drifts

- "Export as JSON" / "Export as CSV" — verb: Export
- "Export as FHIR Bundle (for doctors)" — verb: Export
- "Share PDF summary (for doctors)" — verb: Share

Either all four are "Export" (and the share-sheet is the implementation), or all four are "Share". Inconsistent verbs make the owner second-guess what they're doing.

### 5.4 Single `isExporting` flag couples all three export buttons

[SettingsScreen.kt:52](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L52) — when one export runs, the other two disable. Acceptable. But the PDF button has its **own** `isExporting` state ([PdfSummaryButton.kt:54](../../android/app/src/main/java/com/bios/app/ui/settings/PdfSummaryButton.kt#L54)), so the user can fire a JSON export *and* a PDF export concurrently. Either share a single hoisted state, or genuinely allow all four to run in parallel.

### 5.5 Hardcoded version string

[SettingsScreen.kt:398](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L398) — `SettingsRow("Version", "0.2.0")`. Use `BuildConfig.VERSION_NAME` so it stays accurate as releases ship.

### 5.6 Inline single-line statements with `;` separators

[SettingsScreen.kt:431](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L431) and four sibling lines — three statements on one line separated by `;`. Reads as "save token, flip flag, dismiss dialog" — fine logic, bad style. Repeated five times.

---

## 6. Feedback & error reporting

### 6.1 Export failures are silent

Each export wraps its work in `try { … } finally { isExporting = false }` with **no `catch`**. An IOException, FileProvider misconfiguration, or rotation during export will throw, the button will re-enable, and the owner will get zero feedback. ([SettingsScreen.kt:213-232](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L213-L232), [SettingsScreen.kt:249-271](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L249-L271), [SettingsScreen.kt:280-302](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt#L280-L302))

**Fix:** add a `SnackbarHostState` to the Scaffold and surface a snackbar on failure ("Export failed — tap to retry").

### 6.2 Token paste has no success feedback

The dialog dismisses on Connect. The "Connected" pill flips. If the token is bad, nothing changes until the next ingest cycle (potentially hours away). See §3.2 — adding a `probe()` call removes this entire failure mode.

### 6.3 Daily Digest schedule is buried

[SettingsNotificationsCard.kt:48-52](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsNotificationsCard.kt#L48-L52) — sub-copy says "Morning summary of your vitals at 8 AM". 8 AM is not configurable. There's also a manifesto-tension here: a daily summary regardless of state contradicts "speak when something matters." Either make the digest opt-in to *deviation only* ("post when something is out of band") or accept that this is the one routine push and make 8 AM configurable.

---

## 7. Manifesto alignment

What's good:

- "Disconnect alerts" copy is exemplary — "Bios reporting on Bios" ([DisconnectAlertToggle.kt:39-43](../../android/app/src/main/java/com/bios/app/ui/settings/DisconnectAlertToggle.kt#L39-L43)). Keep this voice everywhere.
- "Data Age / Total Readings / Baseline Status" are pure instrument-not-coach. ✓
- "Your data never leaves this device. Zero-knowledge architecture." is the right tone for Private tier.
- The owner has direct, one-screen access to **Delete All Data** with no upsell, no friction-for-retention. ✓

What drifts:

- The Daily Digest fires regardless of state (see §6.3) — pushes-to-fill-feed.
- Privacy Tier `Community` description ("help improve detection for all users") edges toward a normative nudge. The manifesto's "Evaluation belongs to the owner" suggests neutral wording: "Send anonymous aggregates. Off by default."
- The token-paste dialog's helper text emits provider/marketing voice ("personal-access-token issued via the provider's developer portal"). Acceptable, but a one-line "Bios never sees your account password" reaffirmation would land.

---

## 8. Prioritized fix list

Roughly ordered by impact-per-effort.

| # | Fix | Effort | Risk if ignored |
|---|---|---|---|
| 1 | Mask token field + disable autocorrect + `FLAG_SECURE` (§3.1) | S | Shoulder-surf / IME-store leak of secrets |
| 2 | Reset `isXxxConnected` flags inside Delete-All-Data handler (§2.1) | XS | UI lies about credential state post-wipe |
| 3 | Remove dead imports + collapse 5 adapter blocks into a data-driven list (§2.4) | M | Pre-commit length check fails on next adapter |
| 4 | Add real `contentDescription` to all functional icons (§4.1) | XS | App is unusable on TalkBack |
| 5 | Move "Add Lab Values / BBT / Period / Sleep / Clinical" out of Settings (§1.1) | M | Workflow surfaces hidden; Settings card oversized |
| 6 | Confirmation sheet on `PRIVATE → COMMUNITY` (§3.3) | S | Owner consent quality below manifesto bar |
| 7 | Externalise strings to `strings.xml` (§4.2) | M | Blocks i18n, RTL audits |
| 8 | Convert Health Connect row to `ConnectableSourceRow` with permission-intent action (§5.1) | S | "Not Connected" is a dead end |
| 9 | `try/catch` + Snackbar on export failures (§6.1) | S | Silent failures erode trust |
| 10 | Promote Companion Apps to top-level card (§1.2) | S | Most security-meaningful surface is hard to find |
| 11 | Token-shape validation + lightweight `probe()` in `PasteTokenDialog` (§3.2) | M | Bad tokens stored silently |
| 12 | Type-to-confirm gate on Delete All Data (§3.4) | S | Mistap risk |
| 13 | Scaffold + TopAppBar; consider tabbed/anchored layout as cards grow (§1.3) | M | Discoverability degrades as adapters land |
| 14 | Use `BuildConfig.VERSION_NAME` (§5.5) | XS | About card lies after every release |
| 15 | Use `SingleChoiceSegmentedButtonRow` for Privacy Tier (§4.4) | S | TalkBack misannouncement; tonal mismatch |

---

## 9. Out of scope (flagged for follow-up)

- **`DataDestroyer.destroyAll` audit** — verify it clears all `apiTokenStore` provider keys and `ouraTokenStore`, not just the encrypted Room database. Pin a regression test.
- **`PrivacyTierStore` extraction** — replace inline `SharedPreferences` read with a repository exposing `Flow<PrivacyTier>` (§2.2). Touches non-settings code so deferred.
- **Daily Digest UX rethink** — manifesto vs implementation tension (§6.3). Needs product decision, not a code fix.
- **Companion Apps screen audit** — referenced from settings but not reviewed in this pass.
