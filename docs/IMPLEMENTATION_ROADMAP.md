# JH GetWay implementation roadmap

This roadmap orders the supplied technical audit and legal/splash specifications by risk and dependency. The original specification files are reference documents and are not modified.

## Phase 0 — Documentation and ownership

- Proprietary source-available notice and copyright documentation.
- Third-party notice inventory with unverified items clearly marked.
- Legal/About content specification and splash specification.

## Phase 1 — Critical correctness and security

1. Enforce routing-chain enabled flags and remove orphan entries.
2. Use release metadata with a monotonic versionCode for updates.
3. Validate provider URLs, redirects, request body size, JSON shape, and timeouts.
4. Classify provider errors and honor Retry-After.
5. Synchronize service state with UI and surface port/start failures.
6. Add LAN warning, rate limits, allowlist policy, and security-event cleanup.

## Phase 2 — Provider registry and routing engine

- UUID provider IDs and complete CRUD. — implemented (ProviderStore IDs, get/add/update/remove, chain/health cleanup).
- Edit/replace/delete/test provider credentials with cleanup. — implemented (update validates duplicates; remove purges stored key and health records).
- Model registry, aliases, capabilities, and deterministic `/v1/models`. — implemented (ModelRegistry, capability detection, sorted unique `/v1/models` response).
- Persistent sticky-session TTL, selectable strategies, and route simulation. — implemented (GatewaySettings sticky TTL persisted; PRIORITY/ROUND_ROBIN/LEAST_LATENCY selectable; `POST /v1/gateway/simulate` returns ordered candidates without network calls).

## Phase 3 — Protocol conformance

- Canonical internal request/response model. — implemented (`GatewayModels.kt`: CanonicalRequest/CanonicalResponse, structural validation via `parse()` enforced on the provider forwarding path).
- OpenAI Chat, Anthropic Messages, and Responses fixtures. — implemented (`ProtocolFixtureTest.kt`, plus fixed a stale fixture in `ProtocolAdapterTest.kt`).
- Tool calls/results, JSON mode, multimodal policy, stop reasons, usage, SSE errors, and cancellation. — implemented (bidirectional tool-call translation incl. streaming deltas; `response_format` pass-through for OpenAI and explicit 400 for Anthropic; image sources translated to Anthropic blocks (http/base64), unsupported sources rejected; stop reasons and usage mapped both ways; upstream SSE error events re-emitted in client-native error chunks; client disconnect stops the upstream pump and closes the provider connection).
- Explicit unsupported-field errors; no silent data loss. — implemented (`validate()` -> 400 `invalid_request_error` with the offending field name, never provider fallback; streams emit an explicit error event; non-stream responses map tool calls, usage, and stop reasons end to end).

Phase 3 acceptance is partial: fixture tests exist but have not been executed on a device/emulator (no Android toolchain on the dev host). Verify with `./gradlew connectedAndroidTest` or the CI/device matrix in Phase 5.

## Phase 4 — Observability and UI

- Real service state, live provider health, notifications, and no demo metrics. — implemented (HomeScreen polls `GatewayStats` every 2 s; request count, ready-keys count, recent activity, and route card (strategy + live chain count) are computed from real stores; `demoLogs`/`ProviderKey`/`KeyStatus` — which had never existed as declarations — are now properly defined; provider cards show health (status/latency/last error) from `ProviderHealthStore`; provider rows click through to a detail dialog).
- Category-based settings and provider detail screen. — implemented (settings grouped into Network / Routing / Appearance / Security & privacy sections; `ProviderDetailDialog` shows base URL, protocol, model, aliases, priority, masked key, health status/latency/error/check time, and per-provider request count, success rate, avg latency, and prompt/completion tokens; per-provider breakdown uses the new `UsageEntry` log).
- Activity filters/details, token/provider/model breakdown, and redacted diagnostics. — implemented (MonitoringDialog: Logs filter chips (All/Errors with counts) and tap-to-expand rows with endpoint, status meaning, latency, model, provider; Usage section shows all-time totals, recent success rate, avg latency, tokens, plus top-5 breakdowns by requests/provider, tokens/provider, tokens/model; Diagnostics section builds a redacted report — app version, Android SDK, device, gateway state, port/Wi-Fi mode, provider names with health status only, and last 10 log lines — copyable to clipboard; no keys, prompts, or responses are included).
- Complete English/Bangla resources and accessibility review. — implemented (158 keys extracted to `values/strings.xml` and translated to `values-bn/strings.xml`; icons labeled or explicitly decorative; contrast raised to WCAG AA for status colors (green/orange/red); `docs/ACCESSIBILITY_REVIEW.md` documents findings and limits).

Phase 4 acceptance is partial: the updated UI has not been compiled or rendered on a device (no Android toolchain on the dev host). `MainActivity.kt` previously referenced never-declared symbols and has been structurally corrected (declarations added, demo metrics removed); compile verification happens via CI or a device. Inner limitations: interpolated/runtime strings stay in English in the Bangla locale; FilterChips are 32 dp tall.

## Phase 5 — Tests and device matrix

- Unit tests for storage, security, routing, versions, adapters, backup, and URL policy. — implemented and passing in CI (`.github/workflows/android.yml` runs `gradle testDebugUnitTest` before building the debug APK). Covers `ProviderStore`, `GatewaySecurity`, `ChainStore`, `GatewaySettings`, `GatewayStats`, `UpdateManager`, protocol adapters, and `BackupManager` (65 `@Test` methods across 10 test classes). AndroidKeyStore behavior is exercised through a software-backed test provider (`TestAndroidKeyStore.kt`) with Robolectric shadows for `KeyGenParameterSpec.Builder`; API-key encrypt/decrypt, 5-failure IP block, rate limiting, and routing-chain disable persistence are all covered.
- Mock-provider integration tests for fallback, limits, timeouts, and streaming. — partially implemented via `ProviderRouterTest.kt` (MockWebServer): OpenAI/Anthropic `/v1/*` endpoints, 429 `Retry-After` parsing, 5xx/401 cooldown and disable, validation rejection with zero network calls, round-robin/priority/least-latency strategies, sticky sessions, SSE streaming pass-through. Limits (concurrency/semaphore) and explicit per-provider timeout tests are not yet covered.
- Android 8–15 matrix, Poco/Xiaomi battery/autostart, reboot, Wi-Fi, low storage, and update rollback. — **not executed** on real devices/emulators from this environment (no Android toolchain or devices available). See `docs/DEVICE_MATRIX.md` for the honest per-item status and what is required to run it.

Phase 5 acceptance is partial: automated JVM unit tests execute and pass in CI on every push to `main`; the device/emulator matrix remains manual and unexecuted.

## Phase 6 — Release hardening

- Release-only signing secrets; no fallback password. — implemented. `app/build.gradle.kts` no longer contains a `changeit` (or any) fallback: signing is applied only when `JH_SIGNING_PASSWORD` (GitHub Secret) and the stable keystore both exist; local builds without the secret fall back to AGP's default debug signing. CI restores the stable keystore from cache keyed `jh-getway-release-signing-v1`, uses the secret for key creation/signing, and the tag-only release step **refuses to build** if the secret is missing.
- APK package/signature/version/checksum verification. — implemented at two layers. CI: `scripts/verify-apk.sh` parses `applicationId`/`versionCode`/`versionName` from `app/build.gradle.kts` (cannot drift), checks them against `aapt dump badging`, validates the APK signature and prints signer certificates and SHA-256; it runs for the debug APK on every push and for the release APK on tags, failing the job on mismatch. In-app: `UpdateManager` now re-verifies every downloaded update (PK magic, >1 MB, SHA-256 vs `latest.json` when present, package name, strictly-newer `versionCode`, signing-certificate match with the installed app via `GET_SIGNATURES`/`signingInfo`) and re-verifies at install time.
- Shrink/minify policy — documented and deliberately OFF (`isMinifyEnabled = false` / `isShrinkResources = false`) with the re-enable criteria in `docs/RELEASE_CHECKLIST.md`; R8 cannot be validated without a device.
- Dependency/license review — `THIRD_PARTY_NOTICES.md` now records an entry-by-entry review (ship vs test-only vs build toolchain, declared licenses, *needs review* items flagged), and CI renders resolved `releaseRuntimeClasspath`/`debugUnitTestRuntimeClasspath` trees as the `dependency-tree` artifact on every push.
- README refresh — done (current features, build/signing/release/test instructions, layout, license links; stale "placeholder" status removed).
- Splash implementation — done per `docs/UI_SPLASH_ANIMATION_SPEC.md`: branded cold-launch splash in `SplashScreen.kt` (selected-theme background, JH GetWay rises from below center → controlled upper position → restrained settle to center, then JH Soft Corporation line, ~2.3 s, once per cold launch via `rememberSaveable`, reduced-motion aware via `ANIMATOR_DURATION_SCALE`, no fake progress, gateway composes behind it). Not yet run on a device.
- Release checklist — `docs/RELEASE_CHECKLIST.md` (signing secrets, verification steps, tag → verify → publish flow, rollback, leak contingency); CI publishes both APKs + `latest.json` on `v*` tags.

Phase 6 acceptance is partial: the hardening is implemented and CI-verified for build/test/verification steps, but the release APK has never actually been built (no tag pushed), the app-level signature verification has not run on a device, and the device matrix from Phase 5 remains unexecuted. The minify/shrink mode stays off until on-device verification exists.

## Current status

Existing foundation includes the gateway, encrypted providers, routing chain, backup, updater, app lock, themes, and protocol bridges. An item is not production-complete until its tests and acceptance criteria pass.
