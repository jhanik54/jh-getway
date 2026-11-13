# JH GetWay

লোকাল Android AI Gateway — একাধিক AI provider ও API key-এর মধ্যে routing/fallback করার জন্য ব্যক্তিগত sideload app।

A local Android AI gateway that sits between your apps and multiple OpenAI-compatible AI providers: one endpoint on your device, multiple keys, automatic fallback, and a full monitoring story.

> অবস্থা / Status: in active development. `main` is built and unit-tested in CI on every push. The latest published release is **v37.0.0** (versionCode 37). The on-device matrix (Android 8–15, Poco/Xiaomi) is **not yet executed** — see [docs/DEVICE_MATRIX.md](docs/DEVICE_MATRIX.md). Planned improvements are listed in [docs/IMPROVEMENT_BACKLOG.md](docs/IMPROVEMENT_BACKLOG.md).

## বৈশিষ্ট্য (Features)

- **Foreground gateway service** — Ktor/CIO local server (`0.0.0.0:<port>`), foreground notification, boot restart (`BootReceiver`), `/healthz` endpoint
- **Playground** — a chat surface you can use at any time: paste one API key, the app works out which of that provider's models are free and which are paid, and you talk to any of them (through the gateway or straight to the provider)
- **Agent setup** — Settings generates ready-to-paste configuration for OpenCode, Claude Code, Cline and any OpenAI-compatible client, pointed at this gateway
- **Full pages, no popups** — routing chain, request logs, usage, settings, backup, help and updates each open on their own page with a back button
- **Protocol adapters** — OpenAI Chat Completions, Anthropic Messages, OpenAI Responses; streaming (SSE) bridging and tool-call translation between formats
- **Routing** — persistent Best/Good/Basic chain with reorder controls; Priority, Round Robin and Least-Latency strategies; TTL sticky sessions; per-provider upstream model aliases
- **Failover** — classified provider failures, `Retry-After`-aware cooldowns, 401 disable, 5xx cooldown, bounded fallback chain
- **Security** — API keys encrypted with AES-GCM keys held in the Android Keystore; LAN HTTP warning; per-IP rate limiting; LAN allowlist; optional app lock (device PIN/biometrics) with screenshot protection
- **Monitoring** — token & request stats, per-provider/model breakdowns, redacted diagnostics export, provider-health notifications
- **Backup** — password-protected AES export/import of providers and settings
- **Updater** — checks GitHub releases; verifies the downloaded APK (SHA-256, package name, versionCode, signing certificate) before install
- **App** — Jetpack Compose UI, Light/Dark/System themes, English/Bangla resources (`values-bn`), branded cold-launch splash (reduced-motion aware)

## Build (নির্মাণ)

Prerequisites: JDK 17, Android SDK (compileSdk 35, minSdk 26), Gradle 8.10.

```bash
gradle assembleDebug
gradle testDebugUnitTest
```

Debug builds use the stable signing key when present (see Signing below); otherwise AGP's default debug signing is used.

## Signing (সাইন করা)

- The stable keystore used so sideloaded updates install over the old app **is not in this repository**. CI restores it from a cached secret and signs with `JH_SIGNING_PASSWORD` from GitHub Secrets — there is **no fallback password** anywhere.
- Locally, set `JH_SIGNING_PASSWORD` to sign with the stable key; set `JH_SIGNING_STORE` to override the keystore path (default `~/.android/jh-getway-release.keystore`).
- Never commit keystores or passwords.

## Release (রিলিজ)

Releases are built and verified on tag push (`v*`). See [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md) for the full flow — signing, APK verification (package/signature/version/checksum via `scripts/verify-apk.sh`), `latest.json` metadata and the in-app updater.

## Test (টেস্ট)

- 83 JVM unit tests across 11 classes (`app/src/test/java/com/jh/gateway/`), Robolectric + MockWebServer, run in CI on every push — including a real loopback-socket test of the HTTP surface and the permit accounting
- AndroidKeyStore is exercised through a software-backed test provider; no real device or key material is used
- Instrumented tests (`androidTest`) exist for protocol fixtures but are **not** executed in CI (no emulator); the device matrix remains **not executed** — see [docs/DEVICE_MATRIX.md](docs/DEVICE_MATRIX.md)

## Repository layout

```
app/src/main/java/com/jh/gateway/
  GatewayServer.kt / GatewayService.kt / GatewayStateStore.kt   # local server + foreground service
  ProviderStore.kt / ProviderRouter.kt / ChainStore.kt          # registry, routing, chains
  ProtocolAdapter.kt / ModelRegistry.kt                          # protocol translation + models
  GatewaySecurity.kt / GatewaySettings.kt / GatewayStats.kt       # keys, settings, metrics
  UpdateManager.kt / BackupManager.kt / ProviderHealthStore.kt    # updater, backup, health
  MainActivity.kt / SplashScreen.kt                              # Compose UI + cold-launch splash
scripts/verify-apk.sh                                            # CI APK verification
docs/                                                          # roadmap, device matrix, legal, a11y
```

## Copyright & license

Copyright © 2026 JH Soft Corporation. All Rights Reserved.

JH GetWay is proprietary source-available software. Public source visibility does not grant general permission to modify, fork, redistribute, rebrand, sublicense, sell, or commercially exploit the project. See [LICENSE.md](LICENSE.md), [COPYRIGHT.md](COPYRIGHT.md), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Implementation roadmap

See [docs/IMPLEMENTATION_ROADMAP.md](docs/IMPLEMENTATION_ROADMAP.md) for the ordered production work and acceptance sequence.

This project is fully separate; the `source_reference` project is not used or modified by it.
