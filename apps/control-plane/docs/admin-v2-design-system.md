# NimHUB Control Center V2 — Design System

Status: Phase 4 design-system contract.

## Principles

1. Dense, not cramped.
2. Technical, not developer-only.
3. Calm dark hierarchy, not decorative glass/neon.
4. Operational state must be readable in under one second.
5. Daily actions are primary; infrastructure internals are progressive disclosure.
6. Persian RTL layout is first-class; technical values remain LTR.
7. Mobile is a separate usable composition, not desktop collapsed to one column.
8. Motion is brief and functional, with reduced-motion support.

## Color tokens

The implementation lives under `.admin-v2-shell` to isolate the V2 visual system from login/client-facing pages and legacy compatibility views.

### Surfaces

- `--v2-bg`: application background
- `--v2-s1`: base surface
- `--v2-s2`: elevated surface
- `--v2-s3`: interactive/selected surface
- `--v2-border`: deliberate separator
- `--v2-border-soft`: low-emphasis separator

### Text

- `--v2-text`: primary text
- `--v2-text2`: secondary content
- `--v2-muted`: labels, metadata, hints

### Semantic

- `--v2-primary`: focus and selected emphasis
- `--v2-primary-strong`: primary actions
- `--v2-success`: operational/active
- `--v2-warning`: stale/degraded/attention
- `--v2-danger`: destructive/offline/error
- `--v2-vip`: VIP entitlement/accent

Semantic colors are never the sole carrier of meaning; labels/icons/state text accompany them.

## Radius scale

- 6px: compact badges / technical chips
- 8–10px: icon controls / compact inputs
- 10–12px: buttons / navigation rows
- 13–15px: primary panels
- 18px: command/dialog surfaces only where visual separation benefits

Avoid nested rounded panels unless the nested element is independently interactive.

## Spacing scale

Primary spacing units:
- 4px micro
- 6px compact
- 8px component
- 10px control
- 12px dense section
- 16px normal section
- 18–20px panel
- 24px page rhythm
- 28–32px desktop page separation

## Typography scale

Peyda remains the Persian UI font.

- 9–10px: micro metadata / keyboard hints
- 11px: table headers / badges
- 12px: secondary body / dense controls
- 13px: primary table body / navigation
- 15–17px: section titles
- 22–27px: page titles
- 26–34px: KPI values depending on density

Technical data uses:
`ui-monospace, SFMono-Regular, Menlo, Consolas, monospace`
with explicit LTR direction.

## Control heights

- compact icon button: 32–36px
- normal input/select: 40px minimum
- standard button: 39–42px
- compact table row target: 44–50px depending on content
- mobile tap targets: at least 40px for daily controls

## Buttons

Variants:
- Primary: one dominant action per local action group
- Secondary: normal workflow alternatives
- Ghost: tertiary/contextual action
- Destructive: suspend/delete/revoke/critical mutations
- Icon-only: compact utilities with `aria-label`

Required states:
- default
- hover
- pressed
- focus-visible
- disabled
- loading/pending

## Inputs

All field controls require:
- visible label
- consistent height
- focus ring
- disabled state
- inline validation/error state
- helper text where the business meaning is not obvious

Technical inputs use LTR even inside RTL forms.

## Status system

### Account / subscription
- Active: success
- Suspended: danger
- Expiring: warning
- Quota exhausted: danger
- Needs attention: warning

### Servers
- Online: success dot
- Degraded: warning dot
- Offline: danger dot
- Maintenance: neutral/info

### Provider
- Synced: success
- Stale: warning
- Offline: danger
- Migration required: warning/danger depending on impact

### VIP
VIP is an entitlement marker, not a general decorative gradient. Use the dedicated VIP token and concise label/crown treatment only where it clarifies access.

## Table system

Desktop resource tables must support the same interaction grammar:
- search/filter toolbar
- sortable header where meaningful
- sticky header when vertical list is long
- compact hover/selected row
- status/warning badges
- row actions
- empty/loading/error states
- pagination or bounded server-side result sets

Technical internals do not occupy the default table unless they are essential to an admin decision.

Mobile never depends on horizontal table scrolling for daily workflows; transform to compact resource rows/cards.

## Drawer / dialog system

Use a Drawer/Sheet for resource detail and editing where preserving list context is useful.
Use a modal/alert dialog for confirmations and compact focused actions.

Dangerous operations require explicit consequence text:
- suspend
- revoke all devices
- delete manual server
- provider migration
- critical settings

## Navigation

Desktop:
- persistent right-side RTL sidebar
- grouped navigation labels
- compact active state
- collapsible width
- sticky topbar
- command/search trigger
- system-health state

Mobile:
- sidebar removed from layout
- menu opens a dedicated drawer
- topbar remains compact

## Icon system

Use the single project-owned `AdminIcon` component for Admin V2. New admin icons must be added there rather than mixing multiple icon families. Icons are outline, minimal, consistent in stroke width, and inherit semantic color from the parent control.

## Motion

Allowed:
- drawer entrance
- command/dialog entrance
- hover/selection transitions
- status/progress transitions
- skeleton/loading shimmer only when useful

Guidelines:
- 100–180ms for small UI transitions
- avoid large transform travel
- no continuous decorative animation
- honor `prefers-reduced-motion`

## Accessibility

- persistent focus-visible ring
- icon-only controls require accessible names
- keyboard command palette shortcut is supplemental, never the only access path
- Escape closes transient layers
- dialogs/drawers should manage focus as their implementation is hardened
- text contrast must remain readable on every semantic surface
- state labels accompany status color

## Responsive breakpoints

Design/QA targets:
- 1440px large desktop
- 1024px laptop/tablet landscape
- 768px tablet
- ~390px mobile

Current shell switches from desktop sidebar to mobile drawer below 960px. Resource pages may introduce page-specific layout changes at 760/640px as required.

## Page composition rules

Every page should have:
- compact page header
- one clear primary action area
- primary resource/content region
- progressive disclosure for advanced information
- explicit loading/empty/error states

Avoid:
- hero sections
- decorative KPI overload
- redundant nested cards
- duplicate controls for the same business concept
- raw JSON or provider internals in default workflows

## Quality gate

A page is not complete until it has been checked for:
- RTL/LTR correctness
- desktop density
- mobile usability
- keyboard focus
- empty/error/loading states
- backend error resilience
- no client-contract regressions
