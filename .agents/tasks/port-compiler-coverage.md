---
slug: port-compiler-coverage
branch: port-compiler-coverage
owner: claude
status: in-review
started: 2026-07-06
related-memories:
  - porting-buildsrc-from-consumer-repos
  - config-build-verification
---

## Goal

Upstream the Spine Compiler coverage helpers introduced in
[validation#317][pr-317] into `config`'s `buildSrc`, so every consumer
repo receives them via `./config/pull` and can credit forked-compiler
codegen execution to its root Kover report.

## Context

The `launch*SpineCompiler` tasks fork a JVM that Kover/JaCoCo do not
instrument, so codegen plugins report ~0% coverage in consumer repos.
[validation#317][pr-317] fixed this in `validation` (root coverage
12% → 86%) and its description names upstreaming the `buildSrc` helpers
to `config` as the follow-up. Only `buildSrc` files port; validation's
root `build.gradle.kts` wiring
(`subprojects { enableSpineCompilerCoverage() }`) stays consumer-owned.

## Plan

- [x] Fetch the four changed `buildSrc` files from the PR branch
      `credit-codegen-coverage` and diff against `config` HEAD
      (confirmed purely additive; no reverse drift).
- [x] `io/spine/dependency/test/Jacoco.kt` — add the `agent` coordinate
      constant.
- [x] `io/spine/gradle/testing/SpineCompilerCoverage.kt` — new file:
      `enableSpineCompilerCoverage()`, cacheable per-task exec output.
- [x] `io/spine/gradle/testing/TestKitCoverage.kt` — replace the inline
      agent coordinate with `Jacoco.agent`.
- [x] `io/spine/gradle/report/coverage/KoverConfig.kt` — feed compiler
      exec files into the root report's `additionalBinaryReports`.
- [x] Verify: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew
      :buildSrc:build detekt`.
- [x] Review diff with `spine-code-review`, `kotlin-engineer`,
      `dependency-audit`, and `review-docs`.
- [x] Commit on branch `port-compiler-coverage`, run the pre-PR gate,
      open the PR.

## Log

- 2026-07-06 — drafted and started; user prompt authorizes the port
  (edits only, no commits).
- 2026-07-06 — KDoc generalized while porting: validation-specific
  references (`java`/`context` modules, `JavaValidationPlugin`) removed,
  since `config` distributes these files to all consumer repos.
- 2026-07-06 — detekt (which covers `buildSrc` only here in `config`)
  flagged `TooManyFunctions` in `KoverConfig.kt` after the port; merged
  the duplicate `testKitExecFiles`/`compilerExecFiles` helpers into one
  `execFiles(project, dirName)`. Build + detekt green afterwards.
- 2026-07-06 — all four reviewers APPROVE. Applied their improvements
  on top of the verbatim port: reuse `consumesCoverageBinaryReports()`
  from `SiblingCoverage.kt` (promoted to `internal`) instead of a
  duplicate predicate; early-return guard makes
  `enableSpineCompilerCoverage()` truly idempotent (a second call would
  have double-loaded the agent); `dependsOn` comment no longer reads as
  ordering-only; KDoc polish (grammar, `testFixtures` spelling,
  `[Jacoco.agent]` links in `TestKitCoverage.kt`).
- 2026-07-06 — work complete; committed on branch
  `port-compiler-coverage` (two commits), pre-PR gate re-run green (all
  four reviewers APPROVE), PR opened for human review.

[pr-317]: https://github.com/SpineEventEngine/validation/pull/317
