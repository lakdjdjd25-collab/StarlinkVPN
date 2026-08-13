# Native VPN core

The Android application embeds the official sing-box mobile library through a
source-built `libbox.aar`. The build is pinned to tag `v1.13.18`, commit
`45ca32dcb966f07f97fc888fe8586e359dbe8405`, and SagerNet gomobile `v0.1.12`.

## Reproducible build

Prerequisites are Java 17, Go 1.24.7, Android SDK 36, and Android NDK
`28.0.13004108`. With `ANDROID_HOME` configured, run:

```bash
./scripts/build-sing-box-android.sh
./gradlew :apps:android:assembleDebug :apps:android:testDebugUnitTest
```

The script clones the exact official tag, rejects a mismatched commit, builds
all Android ABIs through the upstream build command, verifies the AAR archive,
and writes `apps/android/libs/libbox.aar`. The generated AAR is intentionally
ignored by Git; CI repeats or restores this deterministic build before compiling
the application.

## Runtime boundary

`SingBoxTunnelCore` owns the libbox command server. `AndroidSingBoxPlatform`
implements socket protection, Android network discovery, certificate access,
per-app routing, and `VpnService.Builder` TUN creation. Runtime JSON is fetched
only after authenticated service and node checks, then validated by libbox
before the tunnel starts.

The control plane must store a complete sing-box configuration containing a TUN
inbound and at least one usable outbound. Node credentials remain encrypted in
PostgreSQL and are never committed to this repository.

## Licensing

Both this application and the linked sing-box core are GPL-3.0-or-later. Keep
`LICENSE`, `NOTICE`, this source repository, and the pinned upstream source
available to recipients of distributed APKs.
