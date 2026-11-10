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

- UUID provider IDs and complete CRUD.
- Edit/replace/delete/test provider credentials with cleanup.
- Model registry, aliases, capabilities, and deterministic `/v1/models`.
- Persistent sticky-session TTL, selectable strategies, and route simulation.

## Phase 3 — Protocol conformance

- Canonical internal request/response model.
- OpenAI Chat, Anthropic Messages, and Responses fixtures.
- Tool calls/results, JSON mode, multimodal policy, stop reasons, usage, SSE errors, and cancellation.
- Explicit unsupported-field errors; no silent data loss.

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
