# Improvement backlog

Working list of what is worth adding or improving next, ordered by value per unit of risk. Items marked **done** are already in `main`; the rest are proposals with the reasoning, so the owner can pick.

## Done in this pass (audit fixes)

| Area | Problem found | Fix |
|---|---|---|
| Gateway server | `GET /v1/models` and `GET /v1/gateway/logs` took a concurrency permit but never released it — after a handful of authorized GETs the gateway wedged and answered 429 to everything | Permits are released in a `finally` block on every route |
| Routing | A provider-specific rejection (e.g. an Anthropic provider that cannot express `response_format`) failed the whole request even when the next candidate could serve it | The provider is skipped; the validation error is only returned if every candidate rejects the body |
| Protocol | Anthropic/Responses clients could send fields the endpoint does not support and the field was silently dropped on translation | `ProtocolAdapter.toOpenAi` rejects unsupported fields up front with an explicit 400 |
| Streaming | The same validation error aborted the SSE stream with a generic message | Every candidate is tried; an explicit SSE error event is emitted only if all fail |
| Settings | Changing the port, Wi-Fi mode or concurrency limit did not affect the running server | The gateway is restarted with the new settings (`ACTION_RESTART`) |
| Key storage | Replacing a backup left the old providers' encrypted API keys in preferences | The stale key entries are removed together with the metadata |
| Key storage | If the AndroidKeyStore key was invalidated (e.g. after a screen-lock change) the gateway key became empty and the gateway was permanently unusable | A fresh key is issued, and the providers screen shows a warning that keys must be re-entered |
| Health notifications | One provider's failure silenced the warning for every other provider for five minutes | The notification throttle is per provider |
| Statistics | The sticky-route map grew without bound with every distinct conversation id | The map is capped and pruned after each stored route |
| Performance | Every request decrypted the provider API key through the AndroidKeyStore and re-read the provider list twice | Decrypted keys are cached in memory (invalidated on change/remove) and the provider list is read once |
| Internationalization | About 50 user-facing strings were hardcoded in English and never appeared in the Bangla locale | All of them are string resources with Bangla translations (221 keys in each locale) |
| Update flow | The downloaded APK was named after the version name (`0.6.0`), so the recovery scan parsed it as version `0` and deleted the pending update | The file carries the version code; two regression tests cover it |
| Update flow | The version code fell back to the first number of a dotted version name (`0.6.0` → `0`) | The release tag is used first, then the version name |

## Proposed next

### High value, low risk

1. **Per-request cost and token dashboard with a time series** — the stats store keeps only counters and a flat log list. A daily/hourly aggregation would make the usage tab actually useful over time instead of showing lifetime totals only.
2. **Health check with a real ping** — `ProviderModelFetcher` lists models; a lightweight "does this provider answer a minimal request" action would distinguish a wrong key from a wrong model.
3. **Provider-level routing weights in the chain UI** — the chain stores a tier and an order, but the UI cannot show which provider handles which tier or preview the resulting route per request. `POST /v1/gateway/simulate` already computes it; surface it in the UI.
4. **Copy the gateway key with a QR code** — the key is long and often typed by hand; a QR code makes it usable from a desktop app in seconds.
5. **Export the request log as CSV/JSON** — the diagnostics report is already redacted; a machine-readable export makes bug reports much easier.

### Medium value

6. **Per-provider request timeout override** — the settings only expose a global timeout; one slow provider can stall a chain that would otherwise fall back quickly.
7. **Retry with a different provider on a mid-stream failure** — currently the first byte decides the provider; a stream that dies after the first token cannot be replayed on another provider.
8. **Token price estimation** — usage is counted but not costed; a small per-model price table would turn the usage tab into a cost view.
9. **Encrypted settings export** — the backup only covers providers. Settings, chain order and routing strategy would make recovery complete.
10. **Dark-theme-aware screens** — several screens hardcode `Color.White` and `Navy` instead of using the theme, so the dark theme is only partially applied.

### Lower value, higher effort

11. **Emulator job in CI** — `connectedAndroidTest` on API 26 and 35 would let the instrumented protocol fixtures run automatically and would unblock the device matrix partially.
12. **On-device screenshot capture in the device matrix** — visual proof for the splash and the UI screens.
13. **R8 / resource shrinking** — only worth enabling once the device matrix can validate it; the keep rules are listed in `docs/RELEASE_CHECKLIST.md`.
14. **Widget for gateway status** — a home-screen widget showing the running state, request count and the local address.

## Deliberately not planned

- **Rotating the signing key.** It would break every existing install (the app must be uninstalled, losing settings). The current key is stable on purpose.
- **Minifying before on-device verification.** R8 could silently break reflection-heavy paths; shipping an unverified minified build is worse than a larger one.
- **Cloud sync.** The app is a local, on-device gateway; adding a backend would change its threat model entirely.
