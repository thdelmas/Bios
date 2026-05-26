# Bios Ecosystem Design System

**Status:** Canonical. Lives in Bios; mirrored in Miam KB; referenced by each specialist's CLAUDE.md.
**Created:** 2026-05-24
**Scope:** Bios + all current specialists (Smokeless, Fil, W2F, Virgil, SoulRadio) + Idun, and any future Bios-ecosystem app.

## Philosophy

**Specialists own identity; the ecosystem owns structure.**

Cross-app consistency lives in *structure* — typography roles, spacing scale, component anatomy, Bios-integration UI patterns, semantic-color tokens. Identity lives in *expression* — palette, theme mode, mascot, copy voice.

A user moving from Smokeless's dark cyberpunk to Idun's parchment artisanal should feel they're in a different *room* but recognise they're in the same *house* — same furniture shapes, same door handles, same plumbing.

The hub (Bios) intentionally has a quiet visual identity (Material teal, Light). Specialists carry the strong identities; Bios is the dashboard that aggregates them.

## 1. The ecosystem palette wheel

Each app owns one primary identity hue. New apps pick a hue that does not collide with neighbours on the wheel.

```
                  Cyan  W2F      #00BCD4
                    ↑
   Gold  SoulRadio ←──→  Teal  Bios      #009688
    #D4AF37              #009688
        ↑                  ↑
   Sand  Virgil           Orange  Smokeless
    #E0E1DD                #FF7300
        ↑                  ↑
   White  Fil ←──→  Apple-Red  Idun
    #FFFFFF             #B33A3A
                           ↑
                    Coral  Rooster
                     #FF8E53
```

Constraint: **every identity hue must read as "warm" or "rich"** — no cold corporate blues, no pastel mints used as primaries, no neon. The ecosystem reads as a curated kitchen / instrument-room, not a SaaS dashboard.

## 2. Semantic color tokens (role-based, hue-independent)

Every app maps the same *roles* to its own colors. These roles are referenced everywhere in the design system below.

| Token | Role | Example mappings |
|---|---|---|
| `role.primary` | App's identity color. Toolbar, FAB, primary buttons. | Smokeless: `#00E5A0`. Idun: `#B33A3A`. W2F: `#00BCD4`. |
| `role.on_primary` | Foreground on primary. | Usually white (dark themes) or black. |
| `role.primary_container` | Soft variant of primary. Tonal buttons, chips. | Idun: `#F4D5D5` (soft red). Smokeless: `#1C1A28` (elevated surface tinted). |
| `role.secondary` | Optional second accent. **Cross-app suggestion: borrow another ecosystem app's primary.** Idun uses W2F-cyan as secondary; this kinship is encouraged. | Idun secondary = W2F primary. |
| `role.background` | Window background. | Smokeless: `#080810` (deep space). Idun: `#FAF6EE` (parchment). |
| `role.surface` | Cards, sheets. | Often same as background, sometimes slightly elevated. |
| `role.surface_variant` | Subtle elevation, dividers. | Smokeless: `#15131F`. Idun: `#F0EAD8`. |
| `role.text_primary` | Body text. | Smokeless: `#F5F5FA`. Idun: `#1A1A1A`. |
| `role.text_secondary` | Meta, captions. | Smokeless: `#9896A8`. Idun: `#5C5C5C`. |
| `role.divider` | Hairline outlines. | Often `text_secondary` at 30–40% alpha. |
| `role.success` | Positive state. | Universal mint `#00E5A0` (borrowed from Smokeless) or app's choice. |
| `role.warning` | Caution. | Universal amber `#FFB830` (Smokeless) or app's choice. |
| `role.error` | Failure. | Universal red `#F43F5E` or app's choice. |
| `role.pending` | Awaiting external state — used by Bios `PENDING_APPROVAL` banner. | Universal soft yellow `#FFF4DA` (Idun's banner_pending). |

Each app's `colors.xml` MUST define every role above. Apps MAY add identity-specific colors beyond the role set, but the role set is mandatory.

## 3. Typography

Material 3 base scale. Roboto for default body type. Plus one cross-app extension:

### Instrument readout (mandatory cross-app)

Origin: W2F's cockpit typography. Mandatory across all apps for data-bearing text — times, durations, quantities, biomarker values, eating windows, distances, scores.

```xml
<style name="TextAppearance.Bios.Instrument" parent="TextAppearance.Material3.BodyMedium">
    <item name="android:fontFamily">monospace</item>
    <item name="android:letterSpacing">0.04</item>
    <item name="android:textColor">@color/text_primary</item>
</style>

<style name="TextAppearance.Bios.Instrument.Small" parent="TextAppearance.Bios.Instrument">
    <item name="android:textSize">13sp</item>
    <item name="android:textColor">@color/text_secondary</item>
</style>
```

Apps name it `TextAppearance.<App>.Instrument` matching their own theme namespace, but the styling MUST match.

**Rule:** Apply to data only. Never to body copy, recipe names, mood labels, or anything humans wrote. The warmth of the default face is part of each app's identity; instrument-readout is the cross-app "this is a measurement" signal.

## 4. Spacing scale

Fixed across the ecosystem. All `padding` / `margin` values MUST come from this set:

```
4dp · 8dp · 12dp · 16dp · 24dp · 32dp · 48dp
```

48dp is the touch-target floor. 16dp is the default screen edge inset. 8dp is the inter-item spacing in lists.

## 5. Component anatomy

### App bar
- `com.google.android.material.appbar.MaterialToolbar`
- Background: `role.background` (not primary — keeps app feeling lighter)
- Title color: `role.text_primary`
- Status bar: matches background, light-icon mode toggled per theme

### Cards
- `com.google.android.material.card.MaterialCardView`
- Corner radius: **12dp or 14dp**, picked once per app, used everywhere
- Elevation OR stroke, not both:
  - `cardElevation="1dp"` + `cardElevation="0dp"` + soft shadow — dark themes
  - `cardElevation="0dp"` + `strokeWidth="1dp"` + `strokeColor="@color/divider"` — light themes
- Padding: 12-14dp inside card; 4dp horizontal margin in lists

### List rows
- Leading: checkbox OR icon (24dp), 6dp end margin
- Body: `LinearLayout vertical`, `layout_weight=1`
- Lines: title (16sp, bold, `text_primary`, max 2 lines + ellipsize), meta (12sp, `text_secondary`), optional tags (11sp, `text_secondary` 75% alpha, max 1 line)
- Whole row clickable; checkbox toggle independent of row click

### FAB
- `ExtendedFloatingActionButton`
- Identity-colored (`role.primary`)
- 16dp margin, anchored bottom-end
- **Show/hide pattern:** hidden when target state is empty (no selection, no plan, no items). Surfaces when there's something to act on. Avoids dead UI.

### Section headers
- Label + count: e.g. `Blueprint · 14 recipes`
- Used to group when both ecosystem-equal-weight collections need rendering (Idun's recipe sources)
- Apps with single-source content can omit headers

### Chips (tags, filters)
- `com.google.android.material.chip.Chip`
- Filter style: tonal, `colorSecondaryContainer` background
- Min height: 48dp (touch target)
- Corner radius: 18dp

## 6. Bios integration UI (cross-app required)

Any specialist that writes to Bios via the companion URI MUST render these UI elements identically:

### a. Status pill (Settings screen)

Renders one of three states:

| `BiosClient.Status` | Visual | Copy |
|---|---|---|
| `NOT_INSTALLED` | dimmed pill, `text_secondary` | "Bios not installed" |
| `NOT_ENABLED` | outlined pill, `text_secondary` | "Disabled" |
| `CONNECTED` | filled pill, `role.success` | "Connected" |

### b. Pending-approval banner

Surfaced when `BiosClient.lastPushOutcome == PENDING_APPROVAL`. The owner has to flip per-app permission in Bios → Settings → Companion Apps before any data lands; without this banner the user logs an event, sees nothing in Bios, has no signal.

```
+-------------------------------------------------+
| ⚠  Pending approval in Bios → Companion Apps   |
|    [Open Bios]                                  |
+-------------------------------------------------+
```

- Background: `role.pending` (soft yellow)
- Text: `role.text_primary`
- Action button: deep-link via `BIOS_EXTRA_NAVIGATE_TO_COMPANIONS` extra

### c. "Open Bios" deep-link button

Mandatory whenever Bios integration is active and Bios is installed. Opens Bios's launcher intent with `EXTRA_NAVIGATE_TO_COMPANIONS = true` so Bios drops the user on the right Settings screen.

```kotlin
val launch = packageManager.getLaunchIntentForPackage(BiosClient.BIOS_PACKAGE)
launch?.putExtra(BiosClient.BIOS_EXTRA_NAVIGATE_TO_COMPANIONS, true)
launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
startActivity(launch)
```

### d. Settings screen anatomy

Every specialist's Settings screen MUST contain a "Bios integration" section with:
- Section header (`role.text_primary`, bold)
- Toggle (`SwitchMaterial`): "Send <noun> events to Bios"
- Status pill (a)
- Pending banner (b), conditionally
- "Open Bios" button (c), conditionally

This block is the *Bios-family handshake* — a user who toggled it in Smokeless should find it identically in Idun.

## 7. Day/Night strategy

Each app picks once and locks. Mixed-mode within the ecosystem is allowed and intentional — variety expresses identity.

| App | Mode | Why |
|---|---|---|
| Bios | Light | Hub / medical-instrument register |
| Smokeless | Dark | Intensity of breaking addiction |
| W2F | Dark | Cyberpunk cognitive Flow |
| Idun | Light (locked) | Parchment / artisanal warmth |
| Virgil | TBD | Likely Light — safety/guide register |
| SoulRadio | Dark | Broadcast warmth on black |
| Fil | TBD | |

If an app supports both modes (`Theme.Material3.DayNight`), its dark variant MUST keep its identity hue intact — Smokeless's mint is mint in both modes.

## 8. Accessibility floors

- **Contrast:** WCAG AA on every primary text + background pair
- **Touch targets:** 48dp minimum
- **Text scaling:** layouts must survive 130% font-scale without truncation
- **RTL:** every layout uses `start/end`, never `left/right`

## 9. Token files

Canonical token files live in [design-tokens/](design-tokens/):

- [`colors.yaml`](design-tokens/colors.yaml) — semantic role definitions + per-app mappings
- [`typography.yaml`](design-tokens/typography.yaml) — type scale + instrument-readout spec
- [`spacing.yaml`](design-tokens/spacing.yaml) — spacing scale
- [`components.yaml`](design-tokens/components.yaml) — component anatomy snippets

These are *source of truth*. Each app's `colors.xml` / `themes.xml` / `dimens.xml` is the platform-specific mapping. **When the canonical file changes, every app's mapping should be re-synced.**

## 10. How to apply (per-app checklist)

When creating a new specialist app or refreshing an existing one:

1. **Pick identity hue.** Check the palette wheel; choose a non-colliding warm/rich color.
2. **Define all 14 role tokens** in `res/values/colors.xml`.
3. **Inherit Material 3 base theme** (Light or Dark variant per identity), override the `colorPrimary/Secondary/Surface/On*` items pointing at your role colors.
4. **Add the instrument-readout** TextAppearance styles.
5. **Implement the Bios integration UI block** in Settings (toggle + status pill + pending banner + Open-Bios button).
6. **Pick card style** (elevation OR stroke) and corner radius (12 or 14dp), use consistently.
7. **Reference this doc** from your app's `CLAUDE.md` design-constraints section.

## 11. Drift detection

The ecosystem will drift without a periodic audit. Recommended cadence: every 3 specialists or every major Bios release, re-audit:

- Do all apps render the Bios integration block identically?
- Has any specialist invented a color role outside the 14-token set that should be promoted to canonical?
- Has typography drifted from the spec?

Audit findings amend this document, not the apps.

## 12. Open questions

- **Iconography style.** Each app has a launcher icon today; cross-app icon-style consistency (line-weight, fill convention, mascot vs symbol) is not yet specified. Defer until ≥1 more specialist ships.
- **Notification visual treatment.** Smokeless and Idun both have reminder/notification flows; the visual layer of notifications (color, icon tinting, action style) is not yet specified.
- **Onboarding pattern.** Each app currently rolls its own (Smokeless has a full onboarding activity, Idun has none). Worth a future cross-cutting spec.
- **Empty-state illustration policy.** Smokeless uses bare text, Idun TBD. May or may not warrant illustrations.
