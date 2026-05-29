---
slug: improve-test-coverage
branch: coverage-tooling
owner: claude
status: draft
started: 2026-05-29
---

## Goal

Stand up a reusable, Spine-native agent skill — `coverage-tests` — that raises
JVM test coverage across the SDK by triaging Codecov, localizing uncovered
lines/branches with JaCoCo, and generating policy-compliant unit tests. The
skill lives in `config` and propagates to all repos via `./config/pull`.
Success = the skill is installed, the open items below are resolved, and it has
been dry-run on at least one pilot module.

## Context

Decisions captured this session (carry these forward — do not re-litigate):

- **Codebase is genuinely mixed Java + Kotlin.** Coverage analysis stays
  language-agnostic (JaCoCo measures bytecode); only test *generation* branches
  per language.
- **Two-tier coverage signal:** Codecov for triage (which modules/files are low
  or declining), JaCoCo locally for precise per-line/branch gaps and
  post-generation verification.
- **Placement:** authored directly in `config/.agents/skills/` so it reaches all
  ~50 repos; not piloted in a single repo first.
- **Author preferences:** Kotlin is the primary language; reader has a software
  development background (write at that level).
- **Test policy (from `.agents/testing.md`, authoritative):** stubs not mocks;
  prefer Kotest assertions; cover API edge cases; scaffold `when`/sealed-class
  branches. No mocking framework is on the classpath by design.
- **Verified stack:** JUnit (Jupiter 6.0.3) platform; Google Truth 1.4.4
  (incl. proto-extension) for Java; Kotest 6.1.11 for Kotlin; JaCoCo 0.8.13 via
  `io.spine.gradle.report.coverage.JacocoConfig`; shared `.codecov.yml`.

Why a new skill rather than adopting an external one wholesale: `config` already
ships a native skill system (`.agents/skills/<name>/SKILL.md` as source of
truth, wrapped by `.claude/commands/*.md`). The external skills are used only as
content donors (structure, taxonomy, Kotest idioms).

### Deliverables already produced (pending placement)

- `.agents/skills/coverage-tests/SKILL.md`
- `.agents/skills/coverage-tests/references/coverage-signals.md`
- `.claude/commands/coverage-tests.md`
- install README with the `_TOC.md` patch

### Tools & references observed (with relevance)

Candidate agent skills evaluated:

- <a href="https://github.com/clear-solutions/unit-tests-skills">clear-solutions/unit-tests-skills</a>
  — chosen structural base. Clean analyze→generate split, editable per-language
  rule files, existing-test-awareness, dependency-class reading. Java-flavored
  (AssertJ/Mockito) — swapped for Truth + stubs.
- <a href="https://awesomeskill.ai/skill/affaan-m-everything-claude-code-kotlin-testing">kotlin-testing (affaan-m)</a>
  — Kotest/MockK/Kover. Borrow Kotest assertion idioms only; ignore its Kover
  and MockK assumptions (we use JaCoCo and stubs).
- <a href="https://github.com/skydoves/android-testing-skills">skydoves/android-testing-skills</a>
  — JVM unit-test patterns (JUnit, coroutine `runTest`, Turbine) are reusable;
  Android/instrumented/Compose parts are irrelevant to Spine.
- <a href="https://github.com/alirezarezvani/claude-skills">alirezarezvani/claude-skills (tdd-guide)</a>
  — source of the coverage gap-analysis taxonomy (line/branch/uncovered-edge).
- <a href="https://claudskills.com/skills/analyzing-test-quality/">Analyzing Test Quality (ClaudSkills)</a>
  — framework-agnostic test-quality vocabulary; complementary to generation.

Coverage tooling:

- <a href="https://app.codecov.io/gh/SpineEventEngine">Codecov dashboard (SpineEventEngine)</a>
  — triage tier UI; trends, per-PR patch coverage, component breakdowns.
- <a href="https://docs.codecov.com/reference/repos_report_retrieve">Codecov v2 — commit report (line-by-line)</a>
  and <a href="https://docs.codecov.com/reference/repos_totals_retrieve">totals (per-file)</a>
  and <a href="https://docs.codecov.com/reference/repos_file_report_retrieve">file report</a>
  — the API endpoints the triage step calls (filter by `path`/`flag`/`component_id`).
- <a href="https://www.eclemma.org/jacoco/">JaCoCo</a>
  — localization + verification tier; XML report parsed for `ci==0` lines and
  `mb>0` partial branches.
- <a href="https://kotest.io/docs/assertions/assertions.html">Kotest assertions</a>
  — preferred assertion style per `testing.md`.

Spine internals:

- <a href="https://github.com/SpineEventEngine/config">SpineEventEngine/config</a>
  — the submodule that distributes `.agents/`, `.claude/`, `buildSrc`, and
  `.codecov.yml` to consuming repos via `./config/pull`.

## Plan

Done this session:

- [x] Research and shortlist agent skills for coverage analysis + test generation.
- [x] Capture decisions (mixed Java+Kotlin; Codecov triage + JaCoCo local;
      author in `config` for all repos).
- [x] Verify test/coverage stack and `.agents`/`.claude` conventions from `config`.
- [x] Author `SKILL.md`, `references/coverage-signals.md`, command wrapper, and
      install README.

Open items to resolve before first real run (these are the former
"Notes / things to confirm" — resolve and tick each):

- [ ] **Java assertion default.** Confirm: keep Google Truth (+ proto-extension)
      as the Java fallback where Kotest is not idiomatic, OR force Kotest
      assertions even in Java tests. Update SKILL.md step 4 to match the choice.
- [ ] **Codecov access.** Provision `CODECOV_API_TOKEN` for the triage tier, or
      confirm the dashboard / PR-comment fallback is acceptable. Decide whether
      to add scoped `Bash(curl:*)` to `.claude/settings.json` (already in the
      command's `allowed-tools`).
- [ ] **JaCoCo report path.** Confirm the actual XML path produced by
      `JacocoConfig` (Gradle default is
      `build/reports/jacoco/test/jacocoTestReport.xml`). If stable, hardcode it
      in `references/coverage-signals.md`; otherwise keep the `find` fallback.
- [ ] **Codecov trends endpoint.** Confirm the exact v2 trends path against
      <a href="https://docs.codecov.com/reference">docs.codecov.com/reference</a>,
      or accept deriving trend from the `commits` endpoint for now.

Installation + continuation:

- [ ] Place the four files in `config` at their destinations (see install README).
- [ ] Add TOC entry to `.agents/_TOC.md`:
      `22. [Coverage analysis & test generation](skills/coverage-tests/SKILL.md)`.
- [ ] Dry-run on a pilot module (e.g. `base-libraries`): run triage → JaCoCo →
      review the proposed test-case list; tune the step-3 taxonomy against real output.
- [ ] Propagate via `./config/pull`; spot-check a second repo (e.g. `core-jvm`).
- [ ] On merge: flip `status: done`, then delete this task file per the
      `.agents/tasks/` lifecycle.

## Log

- 2026-05-29 — Shortlisted candidate skills; selected clear-solutions as the
  structural base with Kotest/taxonomy donors.
- 2026-05-29 — Recorded decisions: mixed Java+Kotlin, Codecov triage + JaCoCo
  local, author in `config/.agents/skills` for all repos.
- 2026-05-29 — Verified stack (JUnit5 / Truth+proto / Kotest / JaCoCo 0.8.13)
  and `.agents`/`.claude` conventions; authored the skill, reference, command,
  and install README. Status `draft` — awaiting approval and the four open
  confirmations above.
