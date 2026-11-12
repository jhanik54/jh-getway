# Device matrix — manual verification checklist

Status: **NOT EXECUTED** (environment has no Android SDK/emulator/physical devices).
Last updated: 2026-11-12

Automated verification that does run in CI (`.github/workflows/android.yml`, push to `main`):

| Check | Status |
|---|---|
| `gradle testDebugUnitTest` (65 `@Test` methods) | ✅ passes in CI |
| `assembleDebug` (debug APK build) | ✅ passes in CI |
| Instrumented tests (`connectedAndroidTest`) | ❌ not run — no emulators/devices in CI |
| Physical device matrix below | ❌ not run |

Everything below must be verified on real hardware (or CI emulators) before a release. This file records the procedure and expected results so the work can be delegated; nothing here has been executed.

## Matrix

| # | Scenario | Devices / API levels | Procedure | Expected result | Status |
|---|---|---|---|---|---|
| 1 | Launch on Android 8 (API 26)–15 (API 35) | one device per supported ABI, minSdk and targetSdk | Install debug/release APK, cold start gateway, send chat request to mock provider | App launches, `/healthz` OK, chat request proxied, foreground notification shown | Not executed |
| 2 | Poco / Xiaomi (MIUI/HyperOS) battery saver | Poco X3/X6 or similar, MIUI 13–15 | Enable battery saver + "restrict background activity"; start gateway; observe 30 min idle | Service is NOT killed or restarted noticeably delayed; user prompted/autostart guidance documented | Not executed |
| 3 | Xiaomi autostart whitelist | Same Xiaomi device | Security app → Autostart → enable JH GetWay; reboot; verify gateway starts (or documented limitation) | Gateway reachable after reboot | Not executed |
| 4 | Cold reboot | Any device with gateway running | Reboot device while gateway service is up; unlock; wait 60 s | `/healthz` responds; provider-backed request succeeds (or fails fast with clear error) | Not executed |
| 5 | Wi-Fi connectivity changes | Any device + Wi-Fi access point | Toggle Wi-Fi off/on mid-request; switch networks; hotspot → Wi-Fi | No crash; pending requests error cleanly; new requests succeed on the new network | Not executed |
| 6 | Low storage | Any device with <200 MB free | Fill storage; attempt backup import, provider add, APK update download | Clear "insufficient storage" style errors; no APK partial writes; gateway keeps existing config | Not executed |
| 7 | Update rollback | Two builds on device (e.g., versionCode 34 → test build) | Install older APK over newer, or attempt downgrade via package installer | Installer refuses downgrade (signature/version behavior documented); no data corruption of `gateway_*` prefs | Not executed |
| 8 | Port already in use | Any device | Start a second app holding the gateway port (e.g., 1080/8080); start JH GetWay | Startup failure surfaced in UI with actionable message, no silent crash | Not executed |
| 9 | TalkBack sweep | Accessibility service on | Navigate Home, Providers, Settings, Monitoring, ProviderDetailDialog with TalkBack | All interactive elements labeled; live regions announce; dialogs trap focus | Partially reviewed statically (`docs/ACCESSIBILITY_REVIEW.md`); not executed |
| 10 | Low-memory kill of foreground service | Any device with reduced RAM | `adb shell am send-trim-memory` under pressure during streaming request | Stream cancels without crashing the app; service either restarts or reports stopped state | Not executed |

## How to run

1. Enable Developer Options + USB debugging; `adb devices` confirms connection.
2. Build APK locally (requires Android SDK): `gradle assembleDebug` (or grab the CI artifact).
3. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. For each matrix row, execute the procedure and record results as: Pass / Fail + notes.
5. For CI-based runs, extend `.github/workflows/android.yml` with `reactivecircus/android-emulator-runner` once workflow minutes budget and KVM-capable runners are available.

## Known automated coverage (already green in CI)

- Storage, security, routing, versions, adapters, backup: unit tests (`app/src/test/java/com/jh/gateway/*Test.kt`).
- Mock-provider fallback/limits/streaming: `ProviderRouterTest` via MockWebServer (timeouts and concurrency/semaphore limits not covered).
- No instrumented (`androidTest`) tests exist yet; Robolectric-based JVM tests only.

## Definition of done for Phase 5 acceptance

- [ ] Items 1–10 above executed and recorded (Pass, or Fail with ticket filed in this repo).
- [ ] Per-provider timeout test and `maxConcurrent` semaphore test added to the JVM suite.
- [ ] `connectedAndroidTest` green on an emulator (API 26 and 35) in CI.
