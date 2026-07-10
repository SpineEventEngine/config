---
slug: gradle-9.6-deprecations
branch: address-deprecations
owner: claude
status: blocked
started: 2026-06-29
---

## Goal

Reach a clean Gradle 9.6 configuration/build with **no deprecation warnings**.
All deprecations originating in `config`'s own sources have been fixed on the
`address-deprecations` branch. The warnings that remain originate **inside
third-party Gradle plugins** and cannot be fixed here — this note tracks them
until upstream ships Gradle-9.x-compatible releases.

## Context

Reproduce from the `config` repo root (the root build applies these plugins),
using a JDK 17 toolchain — point `JAVA_HOME` at a JDK 17 install for your OS
(e.g. on macOS `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`):

```bash
./gradlew help --warning-mode all --stacktrace
```

Add `-Dorg.gradle.deprecation.trace=true` to print the full stack trace of
every deprecation warning — the reliable way to attribute a nag to a plugin.

Two deprecations surface during `> Configure project :`. A third hides in
`buildSrc`: it fires while Gradle generates the precompiled-script-plugin
accessors (our convention plugins are applied against a synthetic project),
so it is **masked whenever those tasks are `FROM-CACHE`/`UP-TO-DATE`**.
Reproduce it on a cold cache:

```bash
./gradlew :buildSrc:generatePrecompiledScriptPluginAccessors --rerun-tasks \
    --warning-mode all -Dorg.gradle.deprecation.trace=true
```

Verified stack traces show all three are raised from the plugins' own
`apply()` code, not from `config`:

| Deprecation | Raised by | Plugin / version |
|---|---|---|
| `ReportingExtension.file(String)` (removed in Gradle 10) | `io.gitlab.arturbosch.detekt.DetektPlugin.apply` (`DetektPlugin.kt:28`) | detekt-gradle-plugin **1.23.8** |
| `Project.getProperties` (fails in Gradle 10) | `com.osacky.doctor` → `RemoteCacheEstimation.<init>` (`RemoteCacheEstimation.kt:20–22`, one warning per line) | gradle-doctor **0.12.1** |
| `Project` object as a dependency notation (fails in Gradle 10) | `kotlinx.kover.gradle.plugin.KoverGradlePlugin.apply` → `PrepareKoverKt.prepare` (`PrepareKover.kt:29`) | kover-gradle-plugin **0.9.8** |

Notes:
- The detekt warning is *attributed* to `detekt-code-analysis.gradle.kts:67`,
  but line 67 is only the `id("io.gitlab.arturbosch.detekt")` apply site — the
  deprecated call is in the plugin bytecode.
- All three calls happen unconditionally on plugin apply, so no `detekt { }` /
  `doctor { }` / `kover { }` configuration can avoid them.
- There is **nothing to upgrade to**: as of 2026-06-29, detekt's latest release
  is `1.23.8` (the version we use; no 2.x on the `detekt-gradle-plugin`
  coordinate) and gradle-doctor's latest is `0.12.1` (also what we use). Both
  newest releases still use the deprecated APIs.
- Confirmed there is **no** `ReportingExtension.file` / `Project.getProperties`
  call anywhere in `buildSrc` (the `System.getProperties()` in
  `write-manifest.gradle.kts:46` is Java's, unrelated).
- Upstream status, re-checked 2026-07-10:
  - **detekt** — [detekt#8452](https://github.com/detekt/detekt/issues/8452)
    (this exact deprecation) was closed as completed on 2025-08-31. The fix
    (uses `ReportingExtension.baseDirectory`) lives only on `main` and ships
    with detekt 2.0 — currently `2.0.0-alpha.5` — under the **new plugin ID
    `dev.detekt`** and new `dev.detekt` Maven coordinates. 1.23.8 (2025-02)
    is still the newest 1.x release.
  - **gradle-doctor** — still unfixed even on upstream `master`
    (`RemoteCacheEstimation.kt` last touched 2024-07); no upstream issue
    tracks it. Note: 0.12.1 has no git tag or GitHub release — watch the
    [Maven Central metadata](https://repo1.maven.org/maven2/com/osacky/doctor/doctor-plugin/maven-metadata.xml),
    not the releases page.
  - **Kover** — [kotlinx-kover#818](https://github.com/Kotlin/kotlinx-kover/issues/818)
    (open) tracks its Gradle 9.6 deprecation; 0.9.8 is still the latest
    release.
- The Kover-plugin-internal call above is **distinct** from `config`'s own
  `Project`-notation usage in `KoverConfig.kt:172`
  (`rootProject.project(sub.path)`), which is addressed in a separate change.
  Fixing `KoverConfig` does not clear the plugin-internal warning.

Decision: do **not** suppress globally via `org.gradle.warning.mode=none` — that
would also hide `config`'s own future deprecations, defeating the purpose of
this branch. Tolerate the two warnings (non-fatal on 9.6) and bump when fixed.

## Plan

- [ ] Watch upstream for Gradle-9.x-compatible releases:
  - [ ] detekt — fix confirmed in 2.0 (alphas only so far); wait for a final.
        Releases: https://github.com/detekt/detekt/releases
  - [ ] gradle-doctor — `Project.getProperties` removal. No upstream issue
        exists yet; filing one (or a trivial PR replacing
        `project.properties.containsKey(...)` with
        `providers.gradleProperty(...)` presence checks) would unblock this.
        Releases can be tagless — watch Maven Central metadata (link in Notes).
  - [ ] Kover — release above 0.9.8 with
        [kotlinx-kover#818](https://github.com/Kotlin/kotlinx-kover/issues/818)
        resolved.
- [ ] When a fixed detekt release exists: this is **not a plain bump** — the
      plugin ID moves `io.gitlab.arturbosch.detekt` → `dev.detekt` and the
      Maven group moves to `dev.detekt`, so `buildSrc/build.gradle.kts`
      (`detektVersion` + the classpath dependency) and
      `detekt-code-analysis.gradle.kts` (plugin ID) all change together, plus
      any detekt-config migration 2.0 requires. Re-run the reproduce command,
      confirm the `ReportingExtension.file` warning is gone.
- [ ] When a fixed gradle-doctor release exists: bump `GradleDoctor.version` in
      `buildSrc/src/main/kotlin/io/spine/dependency/build/GradleDoctor.kt` via
      the `dependency-update` flow, re-run, confirm the `getProperties` warning
      is gone.
- [ ] When a fixed Kover release exists: bump `koverVersion` in
      `buildSrc/build.gradle.kts` **and** `Kover.version` in
      `buildSrc/src/main/kotlin/io/spine/dependency/test/Kover.kt` via the
      `dependency-update` flow, re-run the cold-`buildSrc` reproduce command,
      confirm the dependency-notation warning is gone.
- [ ] Once all three clear, flip this task to `done` and delete it.

## Log

- 2026-06-29 — Fixed all `config`-owned Gradle 9.6 deprecations on this branch
  (`ProjectMetadata` `PropertyDelegate`; `registering`/`getting` delegate forms
  in `jvm-module`, `kmp-module`, `uber-jar-module`, `write-manifest`).
- 2026-06-29 — Investigated the two remaining warnings; traced both to detekt
  1.23.8 and gradle-doctor 0.12.1 internals. Both plugins already at latest
  available; no fix exists yet. Status set to `blocked` (on upstream).
- 2026-07-10 — Re-verified on Gradle 9.6.1 with full deprecation traces
  (`:buildSrc:test detekt`, plus a cold
  `:buildSrc:generatePrecompiledScriptPluginAccessors`). Both known warnings
  unchanged; the doctor one fires ×3 (`RemoteCacheEstimation.kt:20–22`).
  Upstream re-checked: detekt#8452 closed, fix ships only with detekt 2.0
  (`dev.detekt` plugin ID; alphas only); gradle-doctor unfixed even on
  `master`, no upstream issue, 0.12.1 is tagless. Added the third,
  cache-masked warning from Kover 0.9.8 (`PrepareKover.kt:29`,
  kotlinx-kover#818) — the follow-up suggested when closing PR #725; it is
  distinct from `config`'s own `KoverConfig.kt` usage (handled separately).
  Nothing to bump — all three plugins already at their latest releases;
  still `blocked` on upstream.
- NOTE: this tracker is blocked on external releases and is expected to outlive
  the `address-deprecations` branch merge — do not delete it on merge; keep it
  (or promote the fact to `.agents/memory/`) until all bumps land.
