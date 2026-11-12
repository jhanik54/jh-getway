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

- Real service state, live provider health, notifications, and no demo metrics.
- Category-based settings and provider detail screen.
- Activity filters/details, token/provider/model breakdown, and redacted diagnostics.
- Complete English/Bangla resources and accessibility review.

## Phase 5 — Tests and device matrix

- Unit tests for storage, security, routing, versions, adapters, backup, and URL policy.
- Mock-provider integration tests for fallback, limits, timeouts, and streaming.
- Android 8–15 matrix, Poco/Xiaomi battery/autostart, reboot, Wi-Fi, low storage, and update rollback.

## Phase 6 — Release hardening

- Release-only signing secrets; no fallback password.
- APK package/signature/version/checksum verification.
- Shrink/minify policy, dependency/license review, README refresh, splash implementation, and release checklist.

## Current status

Existing foundation includes the gateway, encrypted providers, routing chain, backup, updater, app lock, themes, and protocol bridges. An item is not production-complete until its tests and acceptance criteria pass.
