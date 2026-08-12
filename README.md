# QuickPing 2.6

Clean-room reconstruction of the QuickPing Android VPN client and its private
control plane. The Android UI is rebuilt from the supplied application package
and 24 reference captures; the server side is a new, auditable implementation.

## Repository layout

- `apps/android` — Kotlin/Jetpack Compose Android client (`org.quickping`)
- `apps/control-plane` — REST API, PostgreSQL schema and private admin panel
- `docs` — reconstruction map, API contracts and deployment notes

## Local setup

1. Copy `apps/control-plane/.env.example` to `.env` and set the values.
2. Run `npm install`, `npm run db:generate`, then `npm run dev`.
3. Open the root directory in Android Studio and run `apps:android`.
4. For a physical device, set `QUICKPING_API_BASE_URL` in `gradle.properties`
   to the reachable HTTPS control-plane URL.

No original backend credentials, signing keys, or user data are stored here.

## Current implementation status

- The supplied screens, fonts and recoverable visual assets are represented in
  the Compose client, including the main home, settings, Guardian, account,
  service, notification and login flows.
- The control plane includes the PostgreSQL schema, versioned REST API, private
  Persian admin panel, encrypted node configuration and Railway deployment
  definition.
- Android email-code authentication, refresh-token rotation, encrypted token
  storage, device-bound sessions, bootstrap sync and selected-node configuration
  retrieval are wired to the control plane. Empty accounts remain empty; demo
  services are not used as runtime fallbacks.
- `TunnelCore` is an explicit native boundary. A new auditable sing-box/Xray
  build must be integrated there before the client can carry VPN traffic; no
  unrecoverable binary or signing secret is treated as source code.

## Remaining before release

- Integrate and test an auditable native sing-box/Xray tunnel core.
- Run the Compose application on the target Android devices and complete
  screenshot-based pixel regression against all supplied captures.
- Configure production email delivery, Google OAuth, signing keys and real VPN
  node credentials.
- Complete the client write APIs for account changes, Guardian preferences,
  split tunneling, notification receipts and usage accounting.
