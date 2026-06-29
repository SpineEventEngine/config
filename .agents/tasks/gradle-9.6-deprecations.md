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
`address-deprecations` branch. The two warnings that remain originate **inside
third-party Gradle plugins** and cannot be fixed here — this note tracks them
until upstream ships Gradle-9.x-compatible releases.

## Context

Reproduce from the `config` repo root (the root build applies these plugins):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew help --warning-mode all --stacktrace
```

Two deprecations surface during `> Configure project :`. Verified stack traces
show both are raised from the plugins' own `apply()` code, not from `config`:

| Deprecation | Raised by | Plugin / version |
|---|---|---|
| `ReportingExtension.file(String)` (removed in Gradle 10) | `io.gitlab.arturbosch.detekt.DetektPlugin.apply` (`DetektPlugin.kt:28`) | detekt-gradle-plugin **1.23.8** |
| `Project.getProperties` (fails in Gradle 10) | `com.osacky.doctor` → `RemoteCacheEstimation.<init>` (`RemoteCacheEstimation.kt:20`) | gradle-doctor **0.12.1** |

Notes:
- The detekt warning is *attributed* to `detekt-code-analysis.gradle.kts:67`,
  but line 67 is only the `id("io.gitlab.arturbosch.detekt")` apply site — the
  deprecated call is in the plugin bytecode.
- Both calls happen unconditionally on plugin apply, so no `detekt { }` /
  `doctor { }` configuration can avoid them.
- There is **nothing to upgrade to**: as of 2026-06-29, detekt's latest release
  is `1.23.8` (the version we use; no 2.x on the `detekt-gradle-plugin`
  coordinate) and gradle-doctor's latest is `0.12.1` (also what we use). Both
  newest releases still use the deprecated APIs.
- Confirmed there is **no** `ReportingExtension.file` / `Project.getProperties`
  call anywhere in `buildSrc` (the `System.getProperties()` in
  `write-manifest.gradle.kts:46` is Java's, unrelated).

Decision: do **not** suppress globally via `org.gradle.warning.mode=none` — that
would also hide `config`'s own future deprecations, defeating the purpose of
this branch. Tolerate the two warnings (non-fatal on 9.6) and bump when fixed.

## Plan

- [ ] Watch upstream for Gradle-9.x-compatible releases:
  - [ ] detekt — Gradle 9 / `ReportingExtension` support (likely detekt 2.x).
        Releases: https://github.com/detekt/detekt/releases
  - [ ] gradle-doctor — `Project.getProperties` removal.
        Releases: https://github.com/runningcode/gradle-doctor/releases
- [ ] When a fixed detekt release exists: bump `detektVersion` in
      `buildSrc/build.gradle.kts` via the `dependency-update` flow, re-run the
      reproduce command, confirm the `ReportingExtension.file` warning is gone.
- [ ] When a fixed gradle-doctor release exists: bump `GradleDoctor.version` in
      `buildSrc/src/main/kotlin/io/spine/dependency/build/GradleDoctor.kt` via
      the `dependency-update` flow, re-run, confirm the `getProperties` warning
      is gone.
- [ ] Once both clear, flip this task to `done` and delete it.

## Log

- 2026-06-29 — Fixed all `config`-owned Gradle 9.6 deprecations on this branch
  (`ProjectMetadata` `PropertyDelegate`; `registering`/`getting` delegate forms
  in `jvm-module`, `kmp-module`, `uber-jar-module`, `write-manifest`).
- 2026-06-29 — Investigated the two remaining warnings; traced both to detekt
  1.23.8 and gradle-doctor 0.12.1 internals. Both plugins already at latest
  available; no fix exists yet. Status set to `blocked` (on upstream).
- NOTE: this tracker is blocked on external releases and is expected to outlive
  the `address-deprecations` branch merge — do not delete it on merge; keep it
  (or promote the fact to `.agents/memory/`) until both bumps land.
