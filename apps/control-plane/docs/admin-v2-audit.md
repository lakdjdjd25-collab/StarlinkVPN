# NimHUB Control Center V2 — Admin Audit & Dependency Map

Status: Phase 1 audit baseline for the incremental Admin V2 redesign.

## Non-negotiable boundary

Admin V2 may replace the admin information architecture and presentation aggressively, but it must preserve the Android/client runtime contracts unless a separately reviewed compatibility change is unavoidable.

### Protected client/auth contracts

Do not change for admin-only UX work:

- `/api/v1/auth/login`
- `/api/v1/auth/email/*`
- `/api/v1/auth/google/*`
- `/api/v1/auth/refresh`
- `/api/v1/client/bootstrap`
- `/api/v1/client/account/*`
- `/api/v1/client/services/*`
- `/api/v1/client/traffic/*`
- `/api/v1/client/notifications/*`
- license normalization and generated license format
- QR payload prefix and payload semantics
- service payload fields consumed by Android
- managed/manual server delivery rules
- effective traffic accounting (`usedBytes + manualUsedBytes`)
- quota, expiry, and device-limit enforcement
- `Service.vipAccess` and server access-tier semantics
- notification snapshot/inbox behavior
- app release/bootstrap contract
- PasarGuard synchronization semantics used by login/bootstrap

New data needed only by the admin should be exposed through `/api/v1/admin/...` endpoints or admin-side server components.

## Domain dependency map

```text
Admin UI
  ├─ Admin-only API routes (/api/v1/admin/*)
  │    ├─ Prisma / PostgreSQL
  │    ├─ PasarGuard provider client
  │    ├─ AuditLog
  │    └─ encrypted provider/server configuration
  └─ Server Components (read-only local DB views)

Android
  ├─ /api/v1/auth/*
  └─ /api/v1/client/*
       ├─ User / Device / RefreshToken
       ├─ Service / Plan
       ├─ ServiceNode / VpnNode
       ├─ ManualServer / TrafficSession
       ├─ PasarGuardBinding
       ├─ NotificationDelivery
       ├─ GlobalSetting
       └─ AppRelease

PasarGuard
  ├─ PasarGuardProvider
  ├─ PasarGuardPlanMapping
  ├─ PasarGuardBinding
  └─ managed VpnNode synchronization
```

## Current admin generations

### Generation A — raw account/service administration

- `/admin/users` + `CreateUserForm` / `UserAccessForm`
- `/api/v1/admin/users`
- legacy `CreateServiceForm` / `ServiceUpdateForm`
- `/api/v1/admin/services`

This flow manages raw NimHUB `User` and `Service` records and is still reachable by code. It must not be deleted solely because a newer workflow exists. It should be isolated from the day-to-day managed-customer UX after usage/dependency verification.

### Generation B — managed license workflow

- `/admin/services`
- `ManagedLicenseForm`
- `/api/v1/admin/licenses`
- `/api/v1/admin/vip-access`

This is the current primary operational flow: generated `@nimhub.com` identity, generated password, generated license + QR, PasarGuard user, binding, group/template assignment, quota, expiry, device limit and VIP.

Admin V2 should converge routine customer management around this generation, while preserving legacy endpoints until they are safely deprecated.

## Confirmed architecture problems

### 1. Duplicate Users concepts

`/admin/users` manages raw accounts while `/admin/services` is the real managed customer/license workflow. Operators must understand the underlying User/Service split to choose the correct page.

V2 direction: one primary **Users** surface for managed customers. Raw account/role administration moves to Advanced/admin-account tooling if still required.

### 2. Managed Users page is provider-coupled

`GET /api/v1/admin/licenses` currently requires a live configured PasarGuard client and lists remote users/profiles before it can return managed licenses.

Impact: a temporary provider outage can make routine local user management unavailable even though NimHUB still has local `User`, `Service`, `Device`, `AuditLog`, and binding state.

V2 direction: admin user reads must be local-first. Provider state is an independent `synced / stale / offline / migration-required` status. Provider failure must degrade a widget/field, not the whole Users page.

### 3. VIP create/update can partially succeed

`ManagedLicenseForm` performs the main create/update request and then a second `/api/v1/admin/vip-access` mutation.

Impact: the UI already has explicit partial-success branches such as “main settings saved, but VIP failed”.

V2 direction: create/update admin operations accept VIP in the same operation or use a transactionally safe orchestration. Existing `/vip-access` remains available for compatibility until no caller depends on it.

### 4. User status and Service status are conflated

The schema correctly models `User.status` and `Service.status` independently. The managed-license PATCH currently mirrors service suspension to user suspension.

Impact: suspending one subscription can affect the whole account and all sessions, even though the domain model supports multiple services per user.

V2 direction: define explicit account-level and service-level actions. Do not silently couple them. Preserve current client behavior until regression tests prove the intended business rule.

### 5. Device-limit edits revoke healthy device state

Managed-license PATCH revokes all refresh tokens and all devices whenever `maxDevices` changes, regardless of increase/decrease.

V2 direction: changing the limit must not blindly revoke healthy devices. Explicit device revocation is a separate action. If a reduced limit leaves too many active devices, surface a deterministic management state/action rather than deleting all healthy sessions.

### 6. Quick traffic/expiry actions are presentation-only arithmetic

Current `+10/+30/+50 GB` and `+30/+90/+180 days` buttons modify total fields in the browser. They are not semantic backend operations.

V2 direction: admin API actions must implement `add_traffic` and `extend_subscription` against current authoritative state. Usage must never reset. PasarGuard must receive the resulting safe total/expiry.

### 7. Provider complexity leaks into routine UX

Provider name, mapping, migration state and sync errors are shown directly in primary managed-user cards. PasarGuard configuration is a top-level navigation item.

V2 direction: routine screens show compact `Needs attention` / provider-state indicators. Detailed provider mapping, binding, migration and debug move under **Settings → VPN Provider → Advanced**.

### 8. Server management is split by implementation detail

Managed nodes and Manual VLESS servers are separate top-level pages.

V2 direction: one **Servers** control center with `Managed` and `Manual` sibling views. Do not merge their backend delivery/accounting semantics.

### 9. Settings is developer-oriented

Global settings are edited as arbitrary JSON and app release management is mixed into the same page.

V2 direction:

- General: typed known settings
- Management: purchase/support information
- VPN Provider: PasarGuard status/configuration
- App Releases: version lifecycle
- Advanced: raw JSON / mapping / migration / debug

Known settings receive schema validation. Raw JSON remains available only in Advanced.

### 10. Dashboard is not operational

The current dashboard is four counts plus recent users. The database already contains enough real data for many operational KPIs and `AuditLog` for recent activity.

V2 direction: add one admin aggregation read model/endpoint. No fake chart/KPI data. Provider health is isolated and failure-tolerant.

## Existing reusable data — no destructive migration required initially

- `Device`: name, platform, appVersion, lastSeenAt, revokedAt
- `Service`: independent status, quota, used/manual usage, expiry, device limit, vipAccess
- `VpnNode`: access tier, health state, capacity, active sessions, provider
- `ManualServer`: category/subcategory, volume, access tier, enabled, traffic sessions
- `UsageSample` and `TrafficSession`
- `NotificationDelivery`: delivered/read state
- `AppRelease`: version/minimum/mandatory/download/SHA/changelog/publish
- `AuditLog`: actor, action, entity, before/after, timestamp
- `PasarGuardProvider/Binding/PlanMapping`

Initial V2 work therefore prefers additive admin APIs/components and avoids schema migrations.

## Navigation target

Primary:

1. Dashboard
2. Users
3. Servers
4. Notifications
5. Settings

Settings children:

- General
- Management
- VPN Provider
- App Releases
- Advanced

Legacy routes can redirect or remain available during migration, but should not remain competing day-to-day destinations.

## Incremental implementation gates

### Phase 2 — regression baseline

Lock down pure contracts first:

- generated license + QR payload
- managed identity/password characteristics
- effective shared usage
- Standard/VIP access semantics
- service/account/quota/expiry access semantics

Then run existing control-plane tests, TypeScript check, production build, and Android CI.

### Phase 4/5 — design system + shell

UI-only first. No client/auth contract changes.

### Phase 6 — dashboard

Add admin-only aggregation. Provider failures must return a degraded provider state while local metrics remain usable.

### Phase 7 — users

Build local-first table + mobile list + detail drawer. Add semantic admin actions with regression tests before replacing current managed-license mutations.

### Phase 8 onward

Servers → Notifications → Settings → Provider/Advanced, each gated by tests and production build.

## Risk matrix

| Area | Risk | Rule |
|---|---|---|
| Auth/login/license | Critical | Preserve routes and payloads; regression tests required |
| Bootstrap/server delivery | Critical | No admin-driven contract edits |
| Traffic/quota | Critical | One effective-usage definition; never reset usage |
| VIP | Critical | Strict server authorization; admin mutation may be consolidated without weakening access |
| Devices | High | Separate limit edits from revocation |
| PasarGuard | High | Provider failure must degrade independently; preserve sync semantics |
| Database | High | Additive migrations only if genuinely needed |
| Admin shell/UI | Low | Can be redesigned aggressively behind the compatibility boundary |

## Definition of done for each milestone

A milestone is not complete because it looks correct. It is complete only when:

1. intended admin workflow works,
2. relevant backend state is verified,
3. protected client contracts remain unchanged,
4. tests/lint/build pass,
5. Android regression CI passes when the branch CI runs,
6. responsive/RTL states are visually checked before production deployment.
