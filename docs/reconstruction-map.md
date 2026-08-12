# QuickPing reconstruction map

## Sources of truth

1. The supplied 716×1536 captures define visual layout and visible state.
2. The supplied `QuickPing_v2.5.0.apk` defines original assets, fonts,
   application capabilities, navigation destinations and data contracts.
3. The new backend is deliberately independent of unrecoverable legacy
   credentials and infrastructure.

## Visual invariants

- Reference canvas: 358×768 dp at 2× density, edge-to-edge dark system bars.
- Base background: near-black; elevated surfaces use three stepped graphite
  tones with 1 dp low-contrast borders.
- Persian and other RTL content uses Peyda. Latin UI uses Mona Sans. Numeric
  diagnostics may use Bitcount; decorative version labels use Unbounded.
- Primary action: saturated blue; destructive action: deep red; disconnected
  artwork: desaturated blue-gray; connected artwork: bright blue.
- Main horizontal inset: 16 dp. Standard row radius: 14 dp. Dialog radius:
  22 dp. Touch targets remain at least 48 dp.

## Captured screens

| Capture | Destination/state |
| --- | --- |
| `1000110693` | cold-start splash |
| `1000110695` | branded animated splash |
| `1000110697` | home, disconnected, selected service |
| `1000110699` | welcome/login |
| `1000110701` | language selector dialog |
| `1000110703` | login helper bottom sheet |
| `1000110705` | login email flow |
| `1000110707` | login progress/error dialog |
| `1000110709` | home, guest/free state |
| `1000110711` | home, server list expanded |
| `1000110713` | home, filtered server list |
| `1000110715` | settings overview |
| `1000110717` | split tunneling overview |
| `1000110719` | Guardian filters |
| `1000110722` | VPN connection configuration sheet |
| `1000110723` | local proxy configuration dialog |
| `1000110725` | DNS provider menu |
| `1000110727` | background connection warning |
| `1000110729` | current version screen |
| `1000110731` | notifications empty state |
| `1000110733` | account and active service |
| `1000110735` | change-email sheet |
| `1000110737` | delete-account confirmation |
| `1000110739` | services list |

## Recovered navigation outside the captures

- QR scanner
- pricing and purchase details
- update available/downloading/latest
- split-tunneling applications and hosts
- landing states: broken app, clock issue, construction, discount, expiration,
  capacity and notification
- Quick Settings tile, boot receiver and background VPN service

## Control-plane responsibilities

- Admin and customer authentication, refresh-token rotation and device binding
- Users, roles, devices, bans, email verification and account deletion
- Plans, services, licenses, quotas, expiry, renewals and payment records
- VPN regions, nodes, health, capacity, protocol configuration and client rules
- Guardian rule profiles, split-tunnel defaults and global application settings
- Notifications, required/optional releases, maintenance and landing messages
- Immutable audit log for privileged changes
- Usage samples and aggregate traffic accounting without storing browsing history

## Recovered client data contracts

The client-facing API preserves the original semantic fields while the database
uses normalized relations:

- `Server`: id, location, ip address, connection, remarks, ping, core type,
  free allowed, unmetered
- `Service`: id, name, plan, license, size, used/remain size, expiry, users count,
  free flag, subscription links, auto-pay and ban state
- `UserInfo`: id, email, verification state, Telegram binding, user id, balance,
  language and OTP state
- `Update`: version, minimum version, changelog, mandatory flag and download link

Actual node credentials are encrypted at rest and are never returned by admin
list endpoints. The Android client receives short-lived, service-scoped runtime
configuration only.
