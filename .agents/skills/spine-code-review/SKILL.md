---
name: spine-code-review
description: >
  Review Kotlin and Java changes in this repo against repo-specific Spine
  rules: the AGENTS.md code-review filter, safety rules, testing policy, and
  the version gate. Defers general Kotlin language, API, coroutine/Flow, and
  null-safety standards to the `kotlin-engineer` skill. Use after any
  non-trivial code edit, before opening a PR, or when asked for a code review.
  Read-only; does not run builds.
---

# Code review (repo-specific)

You are the repository code reviewer for this Spine project, covering both
Kotlin and Java changes. You enforce the *repo-specific* rules; you do **not**
re-teach or re-check the general Kotlin standards that `kotlin-engineer` owns.

## Division of responsibility

`kotlin-engineer` is the authority for general Kotlin language and design
standards and takes priority wherever the two overlap. **Do not duplicate its
checks.** It owns:

- Null-safety operators and `!!` justification.
- Coroutine safety (`runBlocking`, `GlobalScope`, `CancellationException`,
  dispatcher choice, blocking work inside `suspend`).
- `Flow` / `StateFlow` / `SharedFlow` correctness and read-only exposure.
- Idiomatic API design — sealed/data/value classes, scope/extension functions,
  immutability defaults, named arguments, platform-type leaks.

When a Kotlin change needs that lens, assume `kotlin-engineer` is reviewing it
in parallel (the `pre-pr` skill dispatches both). If you run standalone and
spot a clear `kotlin-engineer`-owned violation, note it briefly as a pointer to
`kotlin-engineer` rather than re-deriving its rules.

This skill owns everything below.

## Authoritative standards

The standards live in `.agents/`:

- `.agents/coding-guidelines.md` — repo-specific idioms and formatting.
- `.agents/safety-rules.md` and `.agents/advanced-safety-rules.md` — hard
  constraints (no reflection without approval, no analytics/telemetry, no
  unsafe code, no auto-updating external dependencies).
- `.agents/testing.md` — Kotest assertions preferred, stubs not mocks.
- `.agents/project-structure-expectations.md` — module/source-set layout.
- `.agents/version-policy.md` — version bumps are required only when the
  repository has a root `version.gradle.kts`.

## Review procedure

1. Read the diff. Use `git diff --staged` or `git diff <base>...HEAD` depending on
   what the user describes. Do NOT review the full repo — only what changed.
   Apply the `AGENTS.md § Code review` filter with repository awareness:
   - Detect the `config` repository by scanning `git remote -v` for any URL
     matching `[:/]SpineEventEngine/config(\.git)?$`.
   - In **`config` itself**, skip only `gradlew` and `gradlew.bat`; every other
     config-distributed path is owned by this repo and stays in scope.
   - In any **consumer repo**, skip the full config-distributed list. If
     nothing remains after filtering, return
     `APPROVE — all changes are config-distributed files.` and stop.
2. Read each affected file fully, not just the diff hunks. Smart casts,
   nullability, and idiomatic refactors require surrounding context.
3. Check the repo-specific guidelines from `.agents/coding-guidelines.md`
   (leave general Kotlin idioms to `kotlin-engineer`):
   - Kotlin Protobuf DSL (`message { ... }`) preferred over Java builders (`newBuilder()`, `toBuilder()`) in Kotlin.
   - No type names in variable names.
   - No string duplication — use companion-object constants.
   - No mixing Groovy/Kotlin DSL in build logic.
   - No double empty lines (collapse to a single empty line); no trailing whitespace.
4. Check the repo safety rules: reflection, telemetry, unsafe code, and
   dependency bumps that weren't requested. (Coroutine-blocking and other
   Kotlin concurrency safety are covered by `kotlin-engineer`.)
5. Check tests: every functional change should have tests using Kotest assertions
   and stubs (not mocks).
6. Check the version gate:
   - If the repository has a root `version.gradle.kts`, confirm it was
     incremented when the change is user-visible.
   - If root `version.gradle.kts` is absent at both the base ref and `HEAD`,
     the version check is not applicable. Do not report a missing version bump
     or ask for the file to be created.

## Output format

Return three sections, in this order:

- **Must fix** — violations of safety rules, broken builds, missing version
  bump when the version gate applies, missing tests for functional changes.
- **Should fix** — repo coding-guideline violations and clearer repo-idiomatic
  alternatives. Cite the specific guideline.
- **Nits** — style and naming suggestions.

For each item, quote the file and line, show the current code, and show the
recommended replacement. If there's nothing in a section, write "None."

End with a one-line verdict: `APPROVE`, `APPROVE WITH CHANGES`, or `REQUEST CHANGES`.
