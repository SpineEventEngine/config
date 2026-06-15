---
slug: pom-report-scope-merge
branch: group-of-fixes
owner: claude
status: in-review
started: 2026-06-11
---

## Goal

The aggregated dependency report (`docs/dependencies/pom.xml` in consumer
repos) must never mark a production dependency as `<scope>test</scope>`
just because a test-only module also uses it. When the retained version of
an artifact comes from several configurations, the higher-ranked Maven scope
wins (`compile` over `provided` over `runtime` over `test`).

## Context

Observed in `SpineEventEngine/core-jvm-compiler` PR #94: after adding the
test-only `annotation-tests` module, `io.spine:spine-base` — an `api`
dependency of production modules — flipped to `test` scope in
`docs/dependencies/pom.xml`. Root cause: `deduplicate()` in
`buildSrc/src/main/kotlin/io/spine/gradle/report/pom/DependencyWriter.kt`
collapsed same-GAV entries with `distinctBy { it.gav }`, keeping the
first-encountered occurrence — whose configuration then dictated the scope.

## Plan

- [x] Rework `deduplicate()`: group by `group:name`, retain the newest
      version, and among the usages of that version pick the occurrence
      with the widest scope, ranked by the existing
      `ScopedDependency.dependencyPriority()` (compile < runtime < test <
      other). No new API surface; the "several versions" log keeps its
      old semantics via per-group `distinctBy { it.gav }`.
- [x] Document in `dependencyPriority()` KDoc that the same ordering
      drives scope selection for duplicated dependencies.
- [x] Add `DependencyWriterSpec` (JUnit 5 + Kotest, `ProjectBuilder`):
      compile-over-test, runtime-over-test, compile-over-runtime,
      newest-version-with-widest-scope, scope-taken-from-newest-version-
      usages-only, test-only-stays-test, known-scope-over-unknown-config,
      undefined-scope-omitted, and the end-to-end `pom.xml` regression.
- [x] Update copyright years; run `:buildSrc:test`; run review agents.
- [x] Replace the lexicographic newest-version pick in `deduplicate()` with
      a version-aware comparison: new `internal object VersionComparator`
      (numeric segments compared as numbers, release > pre-release,
      SemVer-flavored, no new dependency) in the same package, used via
      `maxWith(compareBy(VersionComparator) { it.version ?: "" })`.
      Scope selection among the newest version's usages stays as is.
- [x] Cover the comparator directly in `VersionComparatorSpec` (7 tests)
      and end-to-end in `DependencyWriterSpec` (9.2.0 vs 10.0.0,
      SNAPSHOT.99 vs SNAPSHOT.100, release vs snapshot, widest scope
      taken from the numerically newest version).
- [x] Follow-up: rank `provided` between `compile` and `runtime` in
      `dependencyPriority()` — the conventional Maven scope order
      (compile < provided < runtime < test < system < undefined,
      as an exhaustive `when` over the scope enum) — so
      `compileOnly`/`annotationProcessor` usages are no longer reported
      as `test`, and the former `provided`-vs-undefined tie no longer
      depends on traversal order. The single ranking is kept for both
      scope selection and layout, so `provided` entries move above
      `runtime` in the regenerated `docs/dependencies/pom.xml` of
      consumer repos (one-time, toward the Maven convention).

## Log

- 2026-06-11 — drafted from the user's directive; executing autonomously.
- 2026-06-11 — implemented and verified: full `:buildSrc:test` green,
  `DependencyWriterSpec` 9 tests / 0 failures. Stash-check against the
  old logic: the order-dependence tests fail as expected, proving the
  spec captures the regression. `spine-code-review` and `kotlin-engineer`
  reviewers: APPROVE. Known deferred item: version comparison is
  lexicographic (pre-existing), e.g. `9.2.0` outranks `10.0.0` — separate
  follow-up.
- 2026-06-11 — review-fix pass applied (`review-docs` included): non-null
  `maxBy`/`minBy` pipeline, `val` test fixture, spec constants, KDoc
  wording aligned with the actual ranking, doc drive-bys.
- 2026-06-11 — `VersionComparator` follow-up completed across the two
  parallel sessions: comparator wired into `deduplicate()`, covered by
  `VersionComparatorSpec` (7 tests) and end-to-end cases. Final state:
  51 buildSrc tests / 0 failures, `detekt` clean
  (`./gradlew :buildSrc:test detekt`). The `provided`-vs-`test` ranking
  gap flagged as a follow-up chip; build-verification lesson recorded in
  memory `config-build-verification`. Status: in-review; change set
  staged for the user to commit.
- 2026-06-11 — `provided` follow-up done: re-ranked into the conventional
  Maven order in `dependencyPriority()` (with a `MagicNumber` suppression
  per `coding.md`); KDoc aligned in `ScopedDependency.kt` and
  `DependencyWriter.kt`; 6 new `DependencyWriterSpec` cases — 57 buildSrc
  tests, 0 failures, `detekt` clean. Old-ranking check: the 4
  behavior-pinning tests fail against the previously staged state, as
  expected. Layout effect accepted: consumer `pom.xml` reports list
  `provided` before `runtime` on next regeneration; pinned end-to-end by
  the new layout-order test. `spine-code-review` and `review-docs`:
  APPROVE; their non-blocking items applied (exhaustive `when` over the
  scope enum, end-to-end layout test, doc wording alignments).
