# Third-party notices

JH GetWay uses third-party software. This inventory records the review status of every direct dependency declared in `app/build.gradle.kts`, plus the transitive modules resolved for the shipping runtime classpath and for the test classpath.

It is not a replacement for the authors' license texts. Do not remove or replace the original notices of these components.

## Review method and status

- **Direct dependencies** are declared in `app/build.gradle.kts`; their licenses are the ones declared upstream by each project.
- **Transitive modules** are taken from the CI-resolved trees. CI renders both classpaths on every push as the `dependency-tree` artifact (`dependency-tree.txt` = `releaseRuntimeClasspath`, `dependency-tree-test.txt` = `debugUnitTestRuntimeClasspath`); the snapshot below was taken from run `43259057489`.
- The resolved release classpath contains **209 module coordinates** (including version constraints and superseded versions Gradle prints alongside the selected one). Every group resolves to **Apache-2.0** except **SLF4J (MIT)**, **Typesafe Config (Apache-2.0)**, **Jansi (Apache-2.0)** and the JUnit/Hamcrest pair (EPL-1.0 / BSD-3-Clause) which is test-only.
- **Still open:** a legal reviewer must confirm the obligations for redistributed MIT/BSD/EPL components (notice-file inclusion) and whether shipping the full `androidx.compose.material:material-icons-extended` tree is acceptable for the distribution model. Rows marked *needs review* below are the unresolved ones.
- Before each release, download the `dependency-tree` artifact and confirm every resolved entry maps to a verified row here (or add it with its license, or drop the dependency).

## Ships inside the APK (`implementation` → `releaseRuntimeClasspath`)

| Component | Declared version | Use | License (upstream-declared) | Review status |
|---|---:|---|---|---|
| AndroidX Core KTX | 1.15.0 | Android utilities | Apache-2.0 | Verified (declared) |
| AndroidX Activity Compose | 1.10.0 | Compose activity integration | Apache-2.0 | Verified (declared) |
| AndroidX Lifecycle Runtime KTX | 2.8.7 | Lifecycle integration | Apache-2.0 | Verified (declared) |
| Jetpack Compose UI / UI Tooling Preview / Material3 / Material Icons Extended (BOM) | BOM 2024.12.01 (Compose 1.7.6, Material3 1.3.1) | UI | Apache-2.0 | Verified (declared) |
| AndroidX Navigation Compose | 2.8.5 | Navigation | Apache-2.0 | Verified (declared) |
| Ktor Server Core / CIO / Content Negotiation | 3.0.2 | Local gateway server | Apache-2.0 | Verified (declared) |
| Ktor Serialization Kotlinx JSON | 3.0.2 | Serialization | Apache-2.0 | Verified (declared) |
| kotlinx.serialization JSON | 1.7.3 | JSON support | Apache-2.0 | Verified (declared) |
| kotlinx-coroutines (core/android) | 1.9.0 (via Ktor, Compose) | Async runtime | Apache-2.0 | Verified (resolved) |
| kotlinx-io (core/bytestring) | 0.5.4 (via Ktor 3) | I/O primitives | Apache-2.0 | Verified (resolved) |
| Kotlin stdlib / stdlib-common / reflect | 2.0.21 | Language runtime | Apache-2.0 | Verified (resolved) |
| org.jetbrains:annotations | 23.0.0 | JetBrains annotations | Apache-2.0 | Verified (resolved) |
| SLF4J API | 2.0.16 (via Ktor) | Logging facade | MIT | Verified (resolved) — notice inclusion required |
| Typesafe Config | 1.4.3 (via Ktor) | Configuration | Apache-2.0 | Verified (resolved) |
| Jansi | 2.4.1 (via Gradle tooling) | Terminal ANSI | Apache-2.0 | Verified (resolved) |
| Guava listenablefuture | 1.0 (via Ktor/AndroidX) | Future abstraction | Apache-2.0 | Verified (resolved) |
| Other `androidx.*` transitives (annotation, collection, lifecycle-*, savedstate, start-up, tracing, emoji2, profileinstaller, versionedparcelable, interpolator, concurrent-futures, arch.core, autofill, customview-poolingcontainer, graphics-path) | see `dependency-tree.txt` | UI/runtime support | Apache-2.0 | Verified (resolved) |

## Test-only (`debugUnitTestRuntimeClasspath` — never distributed in the APK)

| Component | Declared version | Use | License (upstream-declared) | Review status |
|---|---:|---|---|---|
| JUnit 4 | 4.13.2 | Unit test framework | Eclipse Public License 1.0 | Verified (declared) |
| Hamcrest Core | 1.3 (transitive) | Assertion matchers | BSD 3-Clause | Verified (resolved) |
| Robolectric | 4.14.1 | Android framework simulation for JVM tests | Apache-2.0 | Verified (declared) |
| androidx.test core / ext:junit / runner | 1.6.1 / 1.2.1 / 1.6.2 | Instrumented tests (not executed in CI) | Apache-2.0 | Verified (declared) |
| OkHttp MockWebServer (+ OkHttp, Okio) | 4.12.0 | Mock provider HTTP for tests | Apache-2.0 | Verified (declared) |
| Robolectric transitives (Guava, ASM, AutoValue, Error Prone, SnakeYAML, ICU4J, Conscrypt, BouncyCastle, Jansi, TestParameterInjector, sqlite4java) | see `dependency-tree-test.txt` | Test runtime only | Apache-2.0 / BSD / MIT / EPL-2.0 as declared upstream | Verified (resolved) — not distributed |

## Build toolchain (does not ship in the APK)

| Component | Use | License (upstream-declared) | Review status |
|---|---|---|---|
| Android Gradle Plugin 8.7.3 | Build system | Android Software Development Kit License | Verified (declared) |
| Kotlin Gradle Plugin 2.0.21 (Android + Compose plugin) | Build system | Apache-2.0 | Verified (declared) |
| Gradle 8.10 (CI) | Build system | Apache-2.0 | Verified (declared) |

## Rules

- Do not remove or replace the original notices of these components.
- Unknown or conflicting license terms require manual legal review; do not ship while any row is unresolved.
- Redistribution obligations for MIT/BSD/EPL components (text inclusion) must be confirmed by the owner before a public distribution; this is tracked in `docs/RELEASE_CHECKLIST.md`.
