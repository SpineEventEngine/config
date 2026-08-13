---
slug: pom-report-cross-project-resolution
branch: fix-pom-report-resolution
owner: claude
status: in-review
started: 2026-08-12
related-memories:
  - pom-report-per-project-collectors
---

## Goal

Make `generatePom` produce the **same** `docs/dependencies/pom.xml` regardless of
which tasks ran before it, and stop it from emitting false "The project uses
several versions of `X`" warnings for artifacts whose version conflict is already
settled by a `force(...)` directive.

Success looks like: `./gradlew generatePom --rerun-tasks` and
`./gradlew clean build` write byte-identical `pom.xml` files, and every
"several versions" warning names a genuinely unreconciled artifact.

## Context

Discovered on 2026-08-12 while forcing dependency versions in the `compiler`
repo (branch `bump-tool-base`), where the same `./gradlew` invocation reported
different version conflicts depending on how it was launched.

`docs/dependencies/pom.xml` is committed to every consumer repo and is expected
to be regenerated in each PR, so a non-deterministic generator produces
spurious diffs and hides real ones.

Only `pom.xml` is affected. `docs/dependencies/dependencies.md` comes from
`LicenseReporter.mergeAllReports` and uses a different code path.

### Symptom

In the `compiler` repo, two invocations disagree about which artifacts conflict:

```bash
./gradlew clean build
```

reports exactly one conflict — a real one, `spine-time` genuinely resolved to
two versions across modules:

```
The project uses several versions of `io.spine:spine-time` dependency.
module: api,    configuration: implementation, version: 2.0.0-SNAPSHOT.250
module: params, configuration: implementation, version: 2.0.0-SNAPSHOT.244
```

while

```bash
./gradlew generatePom --rerun-tasks
```

reports two entirely different conflicts, **both false positives**:

```
The project uses several versions of `org.jetbrains.kotlin:kotlin-build-tools-impl` dependency.
module: compiler, configuration: kotlinBuildToolsApiClasspath, version: 2.3.21
module: api,      configuration: kotlinBuildToolsApiClasspath, version: null

The project uses several versions of `io.spine:spine-validation-jvm-runtime` dependency.
module: api,     configuration: implementation, version: 2.0.0-SNAPSHOT.460
module: backend, configuration: implementation, version: 2.0.0-SNAPSHOT.446
```

`spine-validation-jvm-runtime` is already forced to `.460` in the `compiler`
root `build.gradle.kts`, and `dependencyInsight` confirms it resolves to `.460`
on `compileClasspath`, `runtimeClasspath`, `testCompileClasspath`, and
`testRuntimeClasspath` of `:backend`. The `.446` in the report is the version
the CoreJvm Compiler plugin *declares*, never the one used.

### Root cause

1. `PomGenerator.applyTo` registers `generatePom` on the **root** project
   ([`PomGenerator.kt:86`][pom-generator]), with the report written from
   a `doLast` action.
2. That action reaches into every subproject:
   `collectScopedDependencies` iterates `subprojects` and calls
   `subproject.resolvedVersions()` ([`DependencyWriter.kt:150`][dependency-writer]),
   which touches `configuration.incoming.resolutionResult.allComponents` for
   *every resolvable configuration* of *every subproject*
   ([`DependencyWriter.kt:208`, `:218-220`][dependency-writer]).
3. Gradle 9.6 forbids resolving another project's configuration from a task
   action that does not hold that project's lock:

   ```
   org.gradle.api.internal.artifacts.configurations.DefaultConfiguration$IllegalResolutionException:
   Resolution of the configuration ':api:compileClasspath' was attempted
   without an exclusive lock. This is unsafe and not allowed.
   ```

4. The `catch (e: Exception)` at [`DependencyWriter.kt:209`][dependency-writer]
   swallows this and logs at `info`, which is invisible at the default log
   level. The configuration contributes no versions.
5. `depsFromAllConfigurations` then falls back to the **declared** version via
   `?: dependency.version` ([`DependencyWriter.kt:176-177`][dependency-writer]),
   silently defeating the whole point of the "report the resolved version"
   behaviour introduced in `56a72c23`.

The reason a full build looks correct is incidental: by the time root `build`
finalizes into `generatePom`, each subproject has already resolved its own
classpaths through its own tasks, so the cached resolution result is returned
without a fresh resolve. Nothing in the task graph guarantees this — it is a
side effect of the invocation, which is exactly why the output is unstable.

Confirm the scale of the degradation with:

```bash
./gradlew generatePom --rerun-tasks --info 2>&1 | grep -c "Skipping configuration"
```

Roughly 40 configurations per subproject are skipped in the standalone run.

## Plan

- [x] Reproduce in `config` itself (or a consumer repo) and capture the
      before/after `pom.xml` for a regression fixture.
      - Reproduced twice: in a scratch multi-project build (exact
        `IllegalResolutionException`), and in `compiler` itself (both false
        warnings, verbatim). The regression fixture is `PomGeneratorIgTest`,
        which drives a real multi-project build via Gradle TestKit.
- [x] Make version collection lock-safe. ~~Preferred: resolve at **configuration
      time** into a task input~~ — **disproved empirically**: the
      `rootComponent` `Provider` is lazy, so its first `.get()` from the root
      task's `doLast` still resolves cross-project on the root task's thread
      and fails with the same `IllegalResolutionException`. Implemented the
      alternative instead: a per-project `collectResolvedVersions` task
      (`ResolvedVersions.kt`) resolves only the configurations of its own
      project — lock-safe by construction — and writes `group:name=version`
      lines under `build/pom/`; `generatePom` depends on the collectors and
      merges their outputs. Mirrors the `LicenseReporter` per-project + merge-task structure.
- [x] ~~Narrow the set of configurations consulted~~ — **rejected, on
      purpose**: declared dependencies are collected from *all* configurations,
      so dropping resolution of plugin-owned ones would fall back to declared
      versions exactly where the false positives live. The
      `kotlin-build-tools-impl` warning above is the counterexample: the
      artifact sits only on `kotlinBuildToolsApiClasspath` (declared `2.3.21`
      in one module, version-less in another), so with resolution narrowed to
      the four source-set classpaths the report would again warn and emit
      version-less entries. The full `isCanBeResolved` scope is kept; the cost
      concern is addressed by the collectors running in parallel, one per project.
- [x] Stop the silent degradation: the catch is **removed entirely**. Probed
      empirically on Gradle 9.6.1: reading a resolution graph is lenient — an
      unresolvable module, a `failOnVersionConflict()` casualty, and even a
      crashing `eachDependency` rule all become `UnresolvedDependencyResult`
      edges and contribute no version; none of them throws from
      `allComponents`. So there is no expected exception to catch: the report
      cannot break the build by Gradle's own design, and anything actually
      thrown (such as the lock error this task fixes) is a bug that now fails
      the collector loudly instead of being swallowed.
- [x] Decide what the declared-version fallback should mean once resolution is
      reliable: kept, and documented as legitimate — with per-project collectors
      the lock failure cannot occur, so a module absent from the resolved map
      really is on no resolvable configuration (e.g., BOM-managed), and the
      declared version is what the build uses.
- [x] Extend the specs: `PomGeneratorIgTest` runs `generatePom` via TestKit
      (parallel execution on) over a root + two subprojects with a local
      metadata-only Maven repo. Covers: a `force(...)`-pinned artifact reported
      at the forced version with no warning; a genuine cross-module conflict
      still warned and reported at the newest version; standalone
      `generatePom` and `clean build` writing identical files.
      `DependencyWriterSpec` keeps all cases via spec-local helpers over the
      new injection point.
- [x] Verify determinism: covered by `PomGeneratorIgTest` and confirmed on
      `compiler` — see the log entry below.
- [x] Re-check the `compiler` repo: the `spine-validation-jvm-runtime` and
      `kotlin-build-tools-impl` false warnings are gone. The `spine-time`
      conflict no longer exists on `bump-tool-base` (all modules resolve
      `2.0.0-SNAPSHOT.250` now — reconciled after this task was drafted), so no
      warning is the correct report; genuine-conflict reporting is locked by
      the functional test instead.

## Log

- 2026-08-12 — drafted from findings in the `compiler` repo (branch
  `bump-tool-base`). Not started; branch not yet created.
- 2026-08-13 — reproduced both candidate designs in a scratch build on
  Gradle 9.6.1: direct `doLast` resolution and the captured-`Provider` variant
  both fail with `IllegalResolutionException`; a per-project collector task
  works and observes `force(...)` per module. Implemented the collector design.
- 2026-08-13 — verified on `compiler` (clean tree, fixed `buildSrc` overlaid
  temporarily, then restored): standalone `generatePom -x assemble
  --rerun-tasks` previously emitted both false warnings and wrote a `pom.xml`
  missing a dozen versions and a whole artifact (`detekt-cli`); with the fix it
  emits no warnings and writes a file **byte-identical to the committed
  `pom.xml`** produced by a full build. `:buildSrc:build detekt` passes.
- 2026-08-13 — four review agents ran (`spine-code-review`, `kotlin-engineer`,
  `gradle-review`, `review-docs`). Applied: `group = SpineTaskGroup.name` on
  the collector task; tests driving a whole-configuration resolution failure
  (unit + TestKit, via `failOnVersionConflict()`); KDoc link fixes; a comment
  explaining the deliberate absence of input/output wiring. Writing the
  requested resolution-failure test disproved the reviewers' (and the plan's)
  premise that such a failure throws: the graph API is lenient (see the
  reworked "silent degradation" item above), so the catch was removed rather
  than narrowed. Deliberately not applied: typed `CommandLineArgumentProvider`
  (optional per reviewer; the main runtime classpath is already tracked via
  the test task's own classpath) and the `Project.dependencies()` rename
  (pre-existing public name).

[pom-generator]: ../../buildSrc/src/main/kotlin/io/spine/gradle/report/pom/PomGenerator.kt
[dependency-writer]: ../../buildSrc/src/main/kotlin/io/spine/gradle/report/pom/DependencyWriter.kt
