# NimHUB Control Center V2 — Design Research

Status: Phase 3 research baseline, completed before the production UI refactor.

## Product direction

The approved product direction is a **premium dark infrastructure / operations control center**: dense but readable, technical, calm, data-oriented, and optimized for repetitive admin work rather than marketing presentation.

The redesign must not copy one dashboard template. It combines interaction patterns proven in current infrastructure/SaaS consoles with a NimHUB-specific information architecture.

## Current primary-source references reviewed

### Vercel dashboard navigation (2026)

Useful pattern:
- persistent/resizable sidebar for primary workflows;
- consistent navigation across contexts;
- priority ordering by common tasks;
- mobile-specific navigation rather than stacking the desktop sidebar.

NimHUB decision:
- persistent grouped sidebar on desktop;
- compact/collapsible mode;
- real mobile drawer;
- top-level navigation limited to Dashboard, Users, Servers, Notifications, Settings.

### Railway project/dashboard model

Useful pattern:
- infrastructure resources grouped around a project/control-center mental model;
- operational state visible near the resource rather than hidden in a separate status page;
- settings separated from day-to-day service operations.

NimHUB decision:
- Dashboard becomes an operational control center;
- server/provider health is visible in-context;
- provider configuration moves under Settings instead of remaining a daily top-level workflow.

### Radix Primitives

Useful pattern:
- accessible dialog, dropdown, tooltip, tabs, switch, and toolbar primitives;
- managed focus, Escape behavior, keyboard navigation, typeahead, RTL-aware components;
- composable primitives rather than visually opinionated component-library output.

NimHUB decision:
- interaction behavior follows accessible primitive semantics even where implemented without a third-party dependency;
- keyboard focus remains visible;
- dialog/drawer/command palette must trap or restore focus correctly as implementation matures;
- icon-only controls always receive accessible labels;
- RTL is the document direction, while technical values are explicitly LTR.

### shadcn/ui dashboard/data-table patterns

Useful pattern:
- practical table/filter/action composition;
- component ownership inside the product codebase;
- avoid framework-owned visual identity.

NimHUB decision:
- build product-owned components and CSS rather than importing a pre-styled dashboard theme;
- use a reusable table/action/filter architecture for Users, Servers, Notifications, Releases, and activity.

## Anti-patterns explicitly rejected

- card grids as the primary desktop user-management interface;
- card-inside-card nesting;
- decorative charts without real backend data;
- excessive glow, glass, gradients, or neon;
- giant hero areas and oversized buttons;
- horizontal-scroll-only mobile tables;
- exposing provider IDs, binding IDs, migration internals, or raw configs in daily workflows;
- treating every button as a primary action;
- displaying raw backend/provider errors as the main user-facing message;
- depending on live PasarGuard availability just to render the Users page.

## Information architecture selected

### Primary navigation

1. Dashboard
2. Users
3. Servers
4. Notifications
5. Settings

### Settings sections

- General
- Management
- VPN Provider
- App Releases
- Advanced

### Advanced-only concepts

- provider mapping;
- binding/debug state;
- migrations;
- raw JSON settings;
- low-level provider diagnostics;
- other compatibility/legacy workflows that remain required but are not daily admin tasks.

## Interaction model selected

### Desktop

- fixed/collapsible sidebar;
- compact sticky topbar;
- command/search entry point;
- data-table-first resource screens;
- contextual drawers for details;
- row actions and compact toolbars;
- small status indicators instead of large status cards.

### Mobile

- no desktop sidebar stacking;
- drawer navigation;
- resource tables transform into compact lists/cards;
- primary actions remain reachable without horizontal scrolling;
- details use full-height sheet/drawer patterns.

## Visual hierarchy selected

Surface levels:
1. Background
2. Surface
3. Elevated surface
4. Interactive surface
5. Selected surface
6. Critical surface

Borders are secondary separators, not the primary layout mechanism. Shadow is subtle. Glow is reserved for online/active/VIP/critical states and used sparingly.

## Typography decision

The project already ships Peyda. The redesign keeps Peyda because it is already integrated, avoids a font migration risk, and is suitable for Persian UI. Technical values use a monospace stack and explicit `dir="ltr"` where required.

Targets:
- Persian labels: Peyda;
- KPI numbers: Peyda semibold with tabular/controlled numeric rendering where possible;
- email/license/IP/host/SHA/config/URL/password/remote username: monospace + LTR.

## Status language

Daily UI uses human-readable states:
- Active / Suspended;
- Online / Degraded / Offline / Maintenance;
- Synced / Stale / Offline / Migration Required;
- VIP / Standard;
- Needs Attention.

Raw provider/backend error codes remain available in details/advanced views.

## Dashboard composition selected

Operational blocks only, backed by real data:
- user/subscription KPIs;
- expiry/quota warnings;
- VIP/Standard split;
- server health;
- provider/backend health;
- release state;
- recent activity from AuditLog;
- traffic trends only where reliable sampled data exists.

No fake chart data will be introduced. If a reliable traffic history source is not available, the chart is omitted until a real aggregation is implemented.

## Users composition selected

Desktop:
- dense data table;
- search, filters, sort, pagination;
- warning/status badges;
- row actions;
- detail drawer.

Mobile:
- compact list/card rows;
- key values only;
- direct actions and detail sheet.

Provider internals do not occupy primary columns.

## Server composition selected

One top-level Servers area with segmented tabs:
- Managed;
- Manual.

Manual flow:
`Paste VLESS → Parse/Validate → Preview → Save`, with advanced transport/security/config values hidden unless needed.

## Safety conclusion

The design research reinforces the existing engineering boundary: redesign Admin aggressively, but keep Android/auth/client contracts stable. New admin-specific aggregation and mutation endpoints are preferred over modifying `/api/v1/client/*` payloads.
