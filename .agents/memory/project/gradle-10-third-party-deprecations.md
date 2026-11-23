---
name: gradle-10-third-party-deprecations
description: Gradle 10/11 deprecation nags from Detekt, Gradle Doctor, Dokka, and Kover are third-party — don't chase them in `buildSrc`.
metadata:
  type: project
  since: 2026-07-02
---

The CI line "Deprecated Gradle features were used in this build, making it
incompatible with Gradle 10" comes from third-party plugins, not from
`buildSrc`. Status on 2026-11-23, measured in `core-jvm` on Gradle 9.9.1:

```bash
./gradlew assemble check dokkaGenerate --rerun-tasks --warning-mode all \
    -Dorg.gradle.deprecation.trace=true
```

The first stack frame outside `org.gradle.*` names the caller. `help` alone
misses the warnings raised while tasks run, such as Kover's `Task.getAnt()`.

These three become errors in Gradle 10:

- `ReportingExtension.file(String)`: Detekt 1.23.8 (`DetektPlugin.apply`).
  The fix ships only with Detekt 2.0 (alpha, new plugin ID `dev.detekt`);
  no further 1.23.x releases are planned. See [detekt#8452][detekt-8452].
- `Project.getProperties`: Gradle Doctor 0.12.1 (`RemoteCacheEstimation`).
  Still called on its `master`; [gradle-doctor#493][doctor-493] is open.
- `Task.getAnt()`: Kover 0.9.11 (`AbstractKoverReportTask`, when
  `koverXmlReport` or `koverVerify` runs). Fixed on Kover `main`
  ([kotlinx-kover#842][kover-842]), not released.

`Configuration.setVisible(boolean)` is deprecated since Gradle 9.8 and
removed in Gradle 11. Call sites counted in `core-jvm`:

- Dokka 2.2.0 (18): [dokka#4601][dokka-4601] is merged, but not in 2.3.0;
  the maintainers promise 2.3.1.
- Kover 0.9.11 (11): fixed on `main` ([kotlinx-kover#836][kover-836]),
  not released.
- Detekt 1.23.8 (4): [detekt#9577][detekt-9577] is merged to `main`
  (the 2.0 line) only.

The Protobuf plugin and KSP calls are gone at the pinned versions: Protobuf
plugin 0.10.1 ([protobuf-gradle-plugin#816][protobuf-816]) and KSP 2.3.14
([ksp#3234][ksp-3234]). KSP 2.3.13 is skipped for a concurrency regression
([ksp#3286][ksp-3286]). A repository that gets KSP through the CoreJvm
Compiler Gradle plugin, as `core-jvm` does, keeps the old KSP until it moves
to a CoreJvm Compiler built with the new pin.

**Why:** a past clean-up removed every deprecated usage from our own build
logic (Kotlin DSL delegated accessors, `PropertyDelegate`). The residual
nags mislead agents into "fixing" our scripts or bumping plugins to
pre-releases. Safety rules forbid auto-updating dependencies outside
a dedicated update task, and the `dependency-update` skill rejects
pre-releases.

**How to apply:** treat these warnings as known and out of our hands
at the versions currently pinned; do not chase them in `buildSrc`.
Re-check upstream during each `dependency-update` run: Detekt 2.0 final
(mind the plugin ID change and config migration), Gradle Doctor > 0.12.1,
Kover > 0.9.11, and Dokka 2.3.1. Remove each entry once resolved. All of
them must be resolved, or the plugins dropped, before Gradle 10 (Gradle 11
for `setVisible`).

[detekt-8452]: https://github.com/detekt/detekt/issues/8452
[doctor-493]: https://github.com/runningcode/gradle-doctor/issues/493
[kover-842]: https://github.com/Kotlin/kotlinx-kover/issues/842
[dokka-4601]: https://github.com/Kotlin/dokka/pull/4601
[kover-836]: https://github.com/Kotlin/kotlinx-kover/issues/836
[detekt-9577]: https://github.com/detekt/detekt/pull/9577
[protobuf-816]: https://github.com/google/protobuf-gradle-plugin/pull/816
[ksp-3234]: https://github.com/google/ksp/issues/3234
[ksp-3286]: https://github.com/google/ksp/issues/3286
