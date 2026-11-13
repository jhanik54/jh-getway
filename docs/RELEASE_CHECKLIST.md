# Release checklist — Phase 6

Covers signing, verification, minify policy, dependency/license review and the tag → publish flow. Complements [`docs/DEVICE_MATRIX.md`](DEVICE_MATRIX.md) (on-device verification, **not yet executed**) and [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).

> Current version: `versionCode 38` / `versionName 0.7.1` in `app/build.gradle.kts`. Do not change either without the owner's approval.

## 1. Signing secrets

- [ ] `JH_SIGNING_PASSWORD` exists in **GitHub Secrets** (never in the repo): `gh secret list`
- [ ] `app/build.gradle.kts` contains **no fallback password** — signing is applied only when the secret and the keystore both exist; otherwise local builds use AGP's default debug signing and the CI release step fails closed
- [ ] Keystore lives at `~/.android/jh-getway-release.keystore` locally, and is restored from the `jh-getway-release-signing-v1` Actions cache in CI. It is **not** committed and must never be rotate — rotation breaks no-uninstall updates (users would have to uninstall, losing settings)
- [ ] Forks/PRs without the secret build fine with default debug signing but cannot publish a release (the release step refuses to run unsigned)

## 2. Version and pre-release verification

- [ ] `main` CI green: unit tests (`gradle testDebugUnitTest`), `assembleDebug`, and the `Verify debug APK` step
- [ ] On-device pass per `docs/DEVICE_MATRIX.md` (status: **not executed** — releases are not device-verified as of this writing; do not claim otherwise)
- [ ] Crash-free launch, gateway start/stop, provider add/test, update check → download → install on the same device

## 3. Tag and publish (automatic on `v*` push)

1. `git tag -a v0.5.3 -m "release notes"` (keep tag versionName-compatible)
2. `git push origin v0.5.3`
3. CI on the tag:
   - `gradle testDebugUnitTest` → `assembleDebug` → `scripts/verify-apk.sh app-debug.apk`
   - `gradle assembleRelease` (requires `JH_SIGNING_PASSWORD`; refuses to build otherwise) → `scripts/verify-apk.sh app-release.apk`
   - `latest.json` (package, versionCode, versionName, apkUrl→app-debug.apk, sha256) is generated
   - Release is published with **both** APKs and `latest.json`
4. Post-publish verification:
   - [ ] `apksigner verify --print-certs app-release.apk` and compare the certificate fingerprint against the previous release — must match (same stable key)
   - [ ] `sha256sum app-debug.apk` equals the `sha256` in `latest.json`
   - [ ] In-app update flow finds the release (Help → Check for update) and installs it on a test device

`scripts/verify-apk.sh` checks, for each APK: package name = `com.jh.gateway`, `versionCode`/`versionName` equal to `app/build.gradle.kts` (parsed, cannot drift), v1/v2/v3 signature validity + certificates, and prints SHA-256. It exits non-zero on any mismatch, failing the CI job.

## 4. Signature & integrity verification in the app

The in-app updater (`UpdateManager.kt`) verifies every downloaded update before install:

- [ ] SHA-256 matches `latest.json` (when published)
- [ ] File is a ZIP with `PK` magic and > 1 MB
- [ ] `packageName` equals the running app's package
- [ ] `versionCode` is strictly greater than the installed one
- [ ] Signing certificate(s) match the installed app's certificate (SHA-256 of the cert; API 26/27 use `GET_SIGNATURES`, API 28+ uses `signingInfo`)
- [ ] The same verification is re-run at **install** time (file may be swapped after download)

## 5. Shrink/minify policy

**Current policy: R8/minify and resource shrinking are OFF** (`isMinifyEnabled = false`, `isShrinkResources = false` for `release`).

Why: this development environment has no Android toolchain or device, and an unverified minified build can fail silently at runtime (reflection over `org.json`, FileProvider, Kotlinx serialization, Ktor CIO). Shipping an unverified minified APK would be worse than a larger unminified one.

When a device and Gradle verification become available:

1. Enable `minifyEnabled`/`shrinkResources` in `app/build.gradle.kts` (`release`)
2. Add/keep rules: Compose (`androidx.compose` keeps are handled by AGP), Kotlinx serialization (`@Serializable` models keep constructors), Ktor CIO engine classes, `org.json` (used via platform), `androidx.core.content.FileProvider`
3. Run the full `docs/DEVICE_MATRIX.md` pass on the minified APK
4. Re-enable this checklist's minify gate only after the matrix passes

## 6. Dependency / license review

- [ ] Download the CI `dependency-tree` artifact (rendered on every push: full `releaseRuntimeClasspath` and `debugUnitTestRuntimeClasspath`)
- [ ] Every resolved entry maps to a verified row in `THIRD_PARTY_NOTICES.md`, or the entry is added with its declared license, or the dependency is dropped
- [ ] No `needs review` row remains unresolved at release time; unknown/conflicting terms go to manual legal review
- [ ] Test-only dependencies never ship in the APK; if that ever changes, re-review them

## 7. Rollback notes

- Android refuses `INSTALL_FAILED_VERSION_DOWNGRADE`: installing an older `versionCode` over a newer one fails; users must uninstall (losing settings) or sideload with `adb install -r -d`
- Releasing a bad build: **do not** re-use the tag. Fix-forward with a new `versionCode` bump (owner approval required) and a new tag
- Signing certificate change = different update channel for all installed copies

## 8. Key leak contingency

If `JH_SIGNING_PASSWORD` or the keystore leaks: rotate the keypair, bump `versionCode`, accept that existing users must uninstall/reinstall, and document it in the release notes.
