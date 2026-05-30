---
name: raise-coverage
description: >
  Raise JVM test coverage for a Gradle module or source path: localize uncovered
  lines and branches from the local JaCoCo-format coverage report (produced by the
  Kover or JaCoCo frontend), then generate policy-compliant unit tests — stubs not
  mocks, Kotest for Kotlin, Google Truth for Java. Proposes a test-case list and
  waits for approval before writing any test, then re-runs the report to confirm
  the gap is closed. Use when asked to add missing tests, close coverage gaps, or
  raise a module's coverage.
---

# Raise test coverage

You localize untested code with JaCoCo and write the unit tests that close the
gap. Work on one Gradle module or path at a time, always propose the test-case
list and **wait for approval** before writing, and verify the gap is actually
closed afterward.

The authoritative standards live in `.agents/`:

- `.agents/testing.md` — stubs not mocks; Kotest assertions; cover API edge
  cases; scaffold `when`/sealed-class branches.
- `.agents/coding-guidelines.md` — Kotlin/Java idioms for the tests you write.
- `.agents/version-policy.md` — tests-only changes do not require a version bump.

Mechanical detail (report paths, XML parsing, gap rules) lives in
[`references/coverage-signals.md`](references/coverage-signals.md). Keep this file
about *what to do*; that one is *how to read the numbers*.

## Scope

- **Coverage comes from the local report.** The JaCoCo engine computes it in
  every Spine repo, exposed through one of two frontends: **Kover**
  (`koverXmlReport`) in consumer repos, or **raw JaCoCo**
  (`jacocoTestReport` / `jacocoRootReport`) in the `config` repo. Both emit
  JaCoCo-format XML — detect which per `references/coverage-signals.md`. Codecov
  triage is a deferred extension; do not depend on it.
- **Target human-written `src/main` code only.** Never write tests for generated
  code (any path containing `generated`, e.g. Protobuf output), `examples`, or
  existing test sources. These are excluded by `.codecov.yml` and by
  `JacocoConfig`'s human-produced filter — respect that boundary.
- **One module or path per run.**

## Inputs

`$ARGUMENTS` is one of:

- a Gradle module path — e.g. `:base`, `:core`;
- a source file or directory — e.g. `base/src/main/kotlin/io/spine/...`;
- `--triage` — read-only: produce a ranked gap report for the repo (or the named
  module) and stop, without proposing or writing tests.

If `$ARGUMENTS` is empty, ask which module or path to target (or offer
`--triage` to help choose).

## Workflow

1. **Resolve the target.**
   - A module/path → map it to its owning Gradle module (the project directory
     that contains it).
   - `--triage` → build the report(s) — per-module `koverXmlReport`, or the
     aggregate `jacocoRootReport` in `config` — rank modules/files by uncovered %,
     output the ranked list, and **stop**.

2. **Localize the gaps** (per `references/coverage-signals.md`):
   - Detect the coverage frontend and run its report task — Kover
     `:<module>:koverXmlReport` (consumer repos) or JaCoCo
     `:<module>:test :<module>:jacocoTestReport` (the `config` repo). The report
     task runs the tests first.
   - Parse the XML (JaCoCo-format either way) for uncovered lines (`ci == 0`) and
     partially covered branches (`mb > 0`). Prioritize methods whose `BRANCH`
     counter has `missed > 0`.
   - Drop any class under an excluded path (generated / examples / test).
   - Discard **non-actionable** gaps the engine cannot credit even with a perfect
     test (see `references/coverage-signals.md`): Kotlin `inline` / `inline
     reified` functions (their bytecode is inlined into each call site, so the
     definition lines stay `ci=0` regardless of tests) and unreachable guards
     (`require`/`check`/`error` paths the public API cannot trigger). Report these
     as non-actionable instead of proposing tests for them.

3. **Read before you write.**
   - Read the class(es) under test in full — public API, constructors, branch
     conditions, `when`/sealed exhaustiveness, error paths.
   - Read existing tests in the module to match structure, naming, fixtures, and
     the test source set/layout you will add to.
   - Read collaborators you will need to substitute, so you can write **stubs**
     (hand-written fakes), not mocks.

4. **Propose the test cases — then WAIT.**
   - For each target, list the concrete cases: the method/branch, the input, the
     expected outcome, and the stub(s) required. Map each case back to the
     uncovered line/branch it closes.
   - Present this list and **wait for the user's confirmation** before writing
     anything. (Under `--triage` you already stopped at step 1.)

5. **Generate the tests** (only after approval), per `.agents/testing.md`:
   - **Match the language of the code under test:**
     - Kotlin → a Kotlin test using JUnit Jupiter structure
       (`@Test` / `@Nested` / `@DisplayName`) with **Kotest assertions**
       (`shouldBe`, `shouldThrow`, `shouldContainExactlyInAnyOrder`, …).
     - Java → a Java test using JUnit Jupiter with **Google Truth**
       (`assertThat(...)`); Protobuf types use the Truth proto extension.
   - **Stubs, not mocks.** No mocking framework is on the classpath by design.
   - Cover API edge cases; add a case per `when`/sealed-class branch.
   - Place the test in the module's existing test source set, mirroring the
     package of the code under test. Reuse the file's copyright header.

6. **Verify.**
   - Re-run the same report task (`koverXmlReport` or `jacocoTestReport`).
   - Confirm the previously-listed uncovered `nr` lines/branches no longer appear
     as gaps, and the class's `LINE` / `BRANCH` `missed` counters dropped.
   - Confirm the module total does not regress against `.codecov.yml`.
   - If a test fails to compile or the gap is not closed, fix and re-run before
     reporting done.

## Report

Return four sections:

- **Gaps** — uncovered lines/branches found (file → lines/branches).
- **Proposed cases** — the awaited list from step 4.
- **Generated** — test files added, with the cases each covers.
- **Verification** — before/after coverage for the target, and confirmation that
  no `.codecov.yml` target regressed.

## Safety

- **Read-only until approval.** Do not write tests before the user confirms the
  step-4 list.
- **Never weaken a `.codecov.yml` target** or extend its `ignore` list to make a
  check pass.
- **Never add a mocking dependency** (Mockito, MockK, …) — write stubs.
- **No version bump.** Tests-only changes do not require one; do not invoke
  `/version-bumped` for a tests-only result. If you had to touch production code
  to make it testable, that is a separate change that needs its own review and a
  version bump.
