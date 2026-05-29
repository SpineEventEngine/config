---
slug: spine-task-group-constant
branch: gradle-review-skill
owner: claude
status: draft
started: 2026-05-29
related-memories: []
---

## Goal

Replace the bare string literal `"spine"` (the Gradle task group used
by every custom task in this organisation) with a shared constant in
two locations:

1. **In `config`'s `buildSrc/`** — so all build files in `config` and
   all consumer projects that apply `config` reference the same
   symbol instead of repeating the literal.
2. **In `tool-base`** — so the production code of every Spine SDK
   Gradle plugin references the same symbol when it registers or
   configures tasks.

Once both constants exist, `gradle-review` reports a remaining bare
literal `"spine"` as a Nit and recommends the relevant constant as
the replacement.

## Context

- The Spine convention "every custom task has `group = "spine"`" is
  documented in
  [`.agents/skills/gradle-review/spine-task-conventions.md`](../skills/gradle-review/spine-task-conventions.md).
- The `gradle-review` skill (see
  [`../skills/gradle-review/SKILL.md`](../skills/gradle-review/SKILL.md))
  enforces the rule, and lists the constant migration as a Nit until
  the symbol exists.
- Two separate codebases are involved because of dependency direction:
  `buildSrc/` in `config` is on the build classpath of every consumer
  project's `build.gradle.kts`, while `tool-base` is consumed at
  runtime by SDK plugins. A single source-of-truth in `tool-base` and
  a re-export from `buildSrc/` would couple the two — instead each
  side declares its own constant and both keep the same value
  (`"spine"`). The `gradle-review` skill cross-checks both.

## Plan

### A. `config/buildSrc` constant

- [ ] Add `const val spineTaskGroup = "spine"` (final naming TBD —
      see "Open questions" below) in a small Kotlin file under
      `buildSrc/src/main/kotlin/io/spine/gradle/` (e.g.
      `SpineTaskGroup.kt`). Include copyright header and KDoc that
      links back to
      `.agents/skills/gradle-review/spine-task-conventions.md`.
- [ ] Migrate every `group = "spine"` usage in `buildSrc/**/*.kt` and
      `buildSrc/**/*.gradle.kts` to the constant.
- [ ] Migrate every `group = "spine"` usage in the project's
      `build.gradle.kts` and `settings.gradle.kts` (the constant is
      visible from build files thanks to `buildSrc/`).
- [ ] Spot-check with `rg -n '"spine"' --type kt` (covers both
      `*.kt` and `*.kts` in standard ripgrep) — only the constant
      declaration and any deliberate exceptions should remain.

### B. `tool-base` constant + GitHub issue

- [x] Open the tracking issue under `tool-base` — [tool-base#171][tool-base-171].
- [ ] (Remaining migration is tracked by that issue, not this branch.)

[tool-base-171]: https://github.com/SpineEventEngine/tool-base/issues/171

## Open questions

- **Naming.** Options:
  - `SpineTaskGroup` (object) with `const val name = "spine"`.
  - Top-level `const val spineTaskGroup = "spine"`.
  - Companion-object member on an existing Spine-wide configuration
    object.
  Pick one once the migration begins; consistency with the
  `tool-base` constant is more important than the specific shape.
- **Location inside `buildSrc/`.** Either a new file under
  `buildSrc/src/main/kotlin/io/spine/gradle/` or a member of an
  existing helper (`BuildSettings.kt` is a candidate).

## Log

- 2026-05-29 — drafted alongside the `gradle-review` skill, awaiting
  approval to start migration.
