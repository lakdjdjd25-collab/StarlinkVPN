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
4. Install Go 1.25.12 and Android NDK `28.0.13004108`, then run
   `./scripts/build-sing-box-android.sh` before the first Android build.
5. The production client defaults to the deployed Railway control plane. Set
   `QUICKPING_API_BASE_URL` in `gradle.properties` to override that origin.

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
- The Android VPN service is connected to an auditable source build of official
  sing-box `v1.13.18`. The version and upstream commit are pinned, Android owns
  the TUN interface, and no unrecoverable native binary from the supplied APK is
  treated as source code.
- Split tunneling applies Android per-app allow/deny rules plus validated
  domain, IPv4, IPv6 and CIDR route rules. Guardian categories compile into
  active sing-box reject rules, and runtime-affecting settings restart an active
  tunnel so changes take effect immediately.
- Provider configurations are normalized for sing-box 1.13, including legacy
  special outbounds, route fields and WireGuard outbounds that now require
  endpoint syntax.
- The supplied nine-language selector changes locale copy, layout direction and
  fonts. Account password change and account deletion are backed by authenticated
  control-plane APIs rather than visual-only controls.

## Production configuration still required

- Configure `RESEND_API_KEY` and a verified `AUTH_FROM_EMAIL` sender before
  enabling email-code login and password-change delivery in production.
- Configure Google OAuth client credentials before exposing Google sign-in.
- Provide and protect a production Android signing key before store publication.
- Keep provider credentials and VPN node material in Railway secrets/the
  encrypted database; never add them to this repository.

## License

QuickPing is distributed under GPL-3.0-or-later. The native build uses official
sing-box under the same license; attribution and reproducibility details are in
[`NOTICE`](NOTICE) and [`docs/native-core.md`](docs/native-core.md).
