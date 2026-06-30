---
slug: pom-resolved-versions
branch: master
owner: claude
status: in-review
started: 2026-06-30
issue: https://github.com/SpineEventEngine/config/issues/440
---

## Goal

The aggregated dependency report (`docs/dependencies/pom.xml` in consumer repos,
produced by `generatePom`) must report the **resolved** version of each
dependency — the version Gradle actually puts on the classpath — not the version
**declared** in the build script.

## Context

Issue #440. `Project.depsFromAllConfigurations()` in
`buildSrc/src/main/kotlin/io/spine/gradle/report/pom/DependencyWriter.kt` reads
`configuration.dependencies` (declared notations) and only patches in explicit
`force(...)` via `forcedVersionOf()`. It ignores conflict resolution, BOM/platform
constraints, and substitutions. Observed in the `text` module: the same artifact
`io.spine.validation:spine-validation-java-runtime` requested at `…SNAPSHOT.61`
and `…SNAPSHOT.40` logs a spurious "several versions" warning, and `deduplicate()`
(newest-declared wins) can print a version on no classpath when a `force` selects
the older one.

Issue author's directive: *"`DependencyWriter` is meant to examine already
resolved configurations, and only then deduplicate, if needed."*

## Plan

- [ ] Add `Project.resolvedVersions(): Map<String,String>` keyed by `"group:name"`,
      built from `configurations.filter { it.isCanBeResolved }` →
      `incoming.resolutionResult.allComponents` → `moduleVersion`. Newest-wins per
      module via `io.spine.gradle.VersionComparator`. Per-configuration `try/catch`
      so an unresolvable configuration is skipped (logged with the exception, not
      swallowed) and the report never breaks the build.
- [ ] Rework `depsFromAllConfigurations(resolvedVersions)`: still iterate declared
      external deps (scope), but use
      `resolvedVersions["$group:$name"] ?: dependency.version` for the version.
- [ ] Delete `forcedVersionOf()` (subsumed by the resolution result).
- [ ] Test seam: `fun Project.dependencies()` (resolves own configs) +
      `internal fun Project.dependencies(resolvedVersions: Map<String,String>)`
      (caller-supplied), both delegating to a private
      `collectScopedDependencies(resolvedVersionsOf: (Project) -> Map<String,String>)`.
- [ ] Update KDoc (class header + `Project.dependencies()`) to state versions are
      resolved.
- [ ] `DependencyWriterSpec`: add resolved-path tests — older-resolved-wins,
      two-in-one-configuration collapse, declared fallback when absent from the map.
      Keep the existing 19 tests green.
- [ ] Verify: `:buildSrc:test`, `:buildSrc:detekt`, stash-check regression proof,
      `spine-code-review` + `kotlin-engineer` reviewers. Update copyright year if the
      tooling flags it.

## Log

- 2026-06-30 — drafted from issue #440; APIs verified against Gradle 9.3.0
  (`incoming.resolutionResult.allComponents`, `ResolvedComponentResult.moduleVersion`,
  `Configuration.isCanBeResolved`). Plan approved; implementing.
- 2026-06-30 — implemented in `DependencyWriter.kt` (resolved-version map +
  substitution, `forcedVersionOf` removed, HOF test seam) and 3 new
  `DependencyWriterSpec` cases. `:buildSrc:test` green — `DependencyWriterSpec`
  23 tests / 0 failures (JDK 17 / Gradle 9.6.1). detekt is not applied to
  `buildSrc` in this repo, so its sources are not statically analysed here; code
  written detekt-clean regardless. Stash-check: neutralising the substitution makes
  the "older resolved version wins" test fail, proving it pins the regression.
  Real-resolution path verified once with a throwaway local-file-repo test (stub
  POMs, `force` to the older version) — `dependencies()` reported the resolved
  version; test then deleted. Unrelated `.idea/{misc,kotlinc}.xml` churn reverted.
  Reviewers (`kotlin-engineer`, `spine-code-review`) running.
- 2026-06-30 — both reviewers APPROVE. Applied their findings: (a) reworded the
  non-resolving `[resolvedVersionsOf]` KDoc link to backticked prose; (b) extracted
  a single `moduleKey(group, name)` helper used by `depsFromAllConfigurations`,
  `resolvedVersions`, and `deduplicate` so the three key derivations cannot drift;
  (c) rewrote `resolvedVersions()` declaratively (`flatMap`/`mapNotNull`/`groupBy`/
  `maxOfWith`). Promoted the real-resolution check to a permanent
  `DependencyWriterSpec` case (`read the version from a resolved configuration`,
  local metadata-only POM repo + `force`) — closes the resolution-success coverage
  gap. Final: `DependencyWriterSpec` **24 tests / 0 failures**; full `:buildSrc:test`
  green. Stash-check repeated: both the injected-map and the real-resolution tests
  fail against the neutralised substitution, proving both pin the regression. Change
  set: `DependencyWriter.kt` + `DependencyWriterSpec.kt`. Staged for the user to
  commit (not committed, per repo safety rules).
- 2026-06-30 — committed on branch `fix-440-resolved-pom-versions`. Ran the `pre-pr`
  gate: `:buildSrc:build` PASS (root has no `build`/`dokkaGenerate` task — all code
  lives in `buildSrc`; Dokka not applied, so its run is N/A); version gate N/A.
  Re-ran the three reviewers on the committed diff — `spine-code-review`,
  `kotlin-engineer`, `review-docs` all APPROVE. Applied their minor nits and a user
  request: dropped the `#440` references from the test KDoc/companion comment, named
  the trailing `ModuleDependency(... factualVersion = version)` argument, and
  documented why the test uses the core `Action` repository overload instead of the
  `kotlin-dsl` `maven { }` form (absent from the buildSrc test classpath). Rebuilt:
  24 tests / 0 failures. Opened PR #720.
- 2026-06-30 — CI `Run detekt` failed: the root `./gradlew detekt` task (never run
  locally; `:buildSrc:detekt` does not exist) analyses `buildSrc` and flagged
  `DependencyWriter.kt` for `TooManyFunctions` (11 top-level, threshold 11). Demoted
  the single-use `resolvedComponentsOf` helper to a **local function** inside
  `resolvedVersions` — keeps the named helper, the imports, and `moduleKey`, and drops
  the file to 10 top-level functions. `./gradlew detekt` now green; `:buildSrc:build`
  green, 24 tests / 0 failures. Lesson: run the root `detekt` task locally, not just
  `:buildSrc:test`.
