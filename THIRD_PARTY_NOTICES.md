# Third-party notices

JH GetWay uses third-party software. This inventory records the review status of every direct dependency declared in `app/build.gradle.kts` (plus the build toolchain). It is not a replacement for the authors' license texts.

Review method and limits: licenses below are the licenses declared by each upstream project. A resolved-dependency audit (`gradle app:dependencies`) could not be run on the development host (no Android toolchain); CI renders the resolved `releaseRuntimeClasspath` and `testDebugUnitTestRuntimeClasspath` trees as the `dependency-tree` artifact on every push for audit. Anything marked *needs review* must be checked against that artifact before a distribution is declared compliant — direct review here covers only the entries listed as *verified (declared)*.

License texts: ship the verbatim texts from the upstream distributions in the release bundle if redistribution requires it; do not remove or replace the original notices of these components.

## Ships inside the APK (`implementation`)

| Component | Declared version | Use | License (upstream-declared) | Review status |
|---|---:|---|---|---|
| AndroidX Core KTX | 1.15.0 | Android utilities | Apache-2.0 | Verified (declared) |
| AndroidX Activity Compose | 1.10.0 | Compose activity integration | Apache-2.0 | Verified (declared) |
| AndroidX Lifecycle Runtime KTX | 2.8.7 | Lifecycle integration | Apache-2.0 | Verified (declared) |
| Jetpack Compose UI/UI Tooling Preview/Material3/Material Icons Extended (BOM) | 2024.12.01 | UI | Apache-2.0 | Verified (declared) |
| AndroidX Navigation Compose | 2.8.5 | Navigation | Apache-2.0 | Verified (declared) |
| Ktor Server Core/CIO/Content Negotiation | 3.0.2 | Local gateway server | Apache-2.0 | Verified (declared) |
| Ktor Serialization Kotlinx JSON | 3.0.2 | Serialization | Apache-2.0 | Verified (declared) |
| kotlinx.serialization JSON | 1.7.3 | JSON support | Apache-2.0 | Verified (declared) |
| kotlinx-coroutines (transitive) | via Ktor/Compose | Async runtime | Apache-2.0 | Needs review against resolved tree |
| Kotlin standard library (transitive) | via plugins | Language runtime | Apache-2.0 | Needs review against resolved tree |
| kotlinx-io / atomicfu (transitive via Ktor) | via Ktor 3 | I/O and atomics | Apache-2.0 | Needs review against resolved tree |

## Test-only (never distributed in the APK)

| Component | Declared version | Use | License (upstream-declared) | Review status |
|---|---:|---|---|---|
| JUnit 4 | 4.13.2 | Unit test framework | Eclipse Public License 1.0 | Verified (declared) |
| hamcrest-core (transitive) | 1.3 | Assertion matchers | BSD 3-Clause | Needs review against resolved tree |
| Robolectric | 4.14.1 | Android framework simulation for JVM tests | Apache-2.0 | Verified (declared) |
| androidx.test core/ext:junit/runner | 1.6.1 / 1.2.1 / 1.6.2 | Instrumented tests (not executed in CI) | Apache-2.0 | Verified (declared) |
| OkHttp MockWebServer | 4.12.0 | Mock provider HTTP for tests | Apache-2.0 | Verified (declared) |

## Build toolchain (does not ship in the APK)

| Component | Use | License (upstream-declared) | Review status |
|---|---|---|---|
| Android Gradle Plugin | Build system | Android Software Development Kit License | Verified (declared) |
| Kotlin Gradle Plugin (Android + Compose plugin) | Build system | Apache-2.0 | Verified (declared) |
| Gradle 8.10 (CI) | Build system | Apache-2.0 | Verified (declared) |

## Rules

- Do not remove or replace the original notices of these components.
- Unknown or conflicting license terms require manual legal review; do not ship while any row is unresolved.
- Before each release, download the CI `dependency-tree` artifact and confirm every resolved entry maps to a verified row above (or add it with its license, or drop the dependency).
