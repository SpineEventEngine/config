---
slug: raise-coverage
branch: coverage-tests-skill
owner: claude
status: draft
started: 2026-05-29
updated: 2026-05-30
---

## Goal

Stand up a reusable, Spine-native agent skill — **`raise-coverage`** — that raises
JVM test coverage by localizing uncovered lines/branches with JaCoCo and
generating policy-compliant unit tests. The skill lives in `config` and
propagates to all ~50 repos via `./config/pull`. Success = the skill and its
wrappers are authored, distribution is wired, and the full loop has been
dry-run on one `base-libraries` module locally (nothing committed).

## Context

Scoped in Claude Chat; the produced `SKILL.md` was lost and the two surviving
files (`coverage-signals.md`, `coverage-tests.md`) are **drafts** to be
rewritten, not shipped. Clarification produced eight decisions that narrow and
simplify the original draft.

### Decisions (locked — do not re-litigate)

| Decision | Choice |
|---|---|
| Skill name | **`raise-coverage`** (verb-noun, like `write-docs` / `bump-version`) |
| Workflow | localize → propose cases → **wait for approval** → generate → verify; plus read-only `--triage` |
| Coverage engine | **JaCoCo engine**, exposed per-repo via Kover (`koverXmlReport`, consumer repos) or raw JaCoCo (`config`); Codecov deferred |
| Test language | match prod: **Kotlin→Kotest, Java→Google Truth** (+proto) |
| Scratch dir | reuse existing `tmp/` → `tmp/base-libraries` (already gitignored via `/tmp`) |
| Done bar | full loop on one `base-libraries` module, **local, nothing committed** |
| Codex parity | include `agents/openai.yaml` |
| Branch/task | keep branch `coverage-tests-skill`; `git mv` task file → `raise-coverage.md` |

### Verified facts (baked into the deliverables)

- **Skill system**: source of truth is `.agents/skills/<name>/`; `.claude/skills`
  is a **symlink** to `../.agents/skills` (author once). `.claude/commands/<name>.md`
  is the slash-command wrapper. Action skills also ship `agents/openai.yaml`.
- **Distribution**: `migrate` (sourced by `pull`) copies the whole `.agents` +
  `.claude` tree, so a new skill auto-propagates — **except** a Hugo-only-repo
  prune block that strips JVM-specific skills. `raise-coverage` is JVM-specific
  and must be added to that block.
- **Test stack**: Kotest `6.1.11` (`io.kotest:kotest-assertions-core`), Google
  Truth `1.4.4` (`truth` + `truth-proto-extension`), JUnit `6.0.3`
  (`org.junit:junit-bom`), JaCoCo `0.8.14` (`Jacoco.kt`, already bumped in the
  working tree).
- **House test idiom**: JUnit Jupiter structure (`@Test` / `@Nested` /
  `@DisplayName` / `@TempDir`) + **Kotest matchers** (`shouldBe`, `shouldThrow`,
  `shouldContainExactlyInAnyOrder`). NOT pure Kotest specs. (Verified in
  `buildSrc/src/test/.../FileExtensionsTest.kt`.)
- **JaCoCo paths**: per-module
  `<module>/build/reports/jacoco/test/jacocoTestReport.xml` (Gradle default, not
  overridden); root aggregate
  `build/reports/jacoco/jacocoRootReport/jacocoRootReport.xml`. KMP modules keep
  exec data at `build/jacoco/jvmTest.exec`. `JacocoConfig.applyTo` throws on
  single-module projects.
- **Never test**: generated code (any path containing `generated`), `examples`,
  existing `test` sources. `.codecov.yml` scope is `src/main/**` only.
- **No version bump** for tests-only changes (contrast with other action skills,
  which end by invoking `/version-bumped`).

## Deliverables (file-by-file)

1. **`.agents/skills/raise-coverage/SKILL.md`** — frontmatter `name` +
   `description: >`. Sections: Goal/scope (JaCoCo-only; human `src/main` only) ·
   Inputs (`$ARGUMENTS` = `:module` | path | `--triage`) · Workflow (1 resolve
   target → 2 localize gaps w/ JaCoCo → 3 read code-under-test + existing tests +
   collaborators → 4 **propose test-case list and WAIT**; `--triage` stops here
   with the ranked report → 5 generate → 6 verify) · Test-generation rules (stubs
   not mocks; Kotlin = JUnit5 + Kotest, Java = JUnit5 + Truth; proto →
   truth-proto-extension; cover edge cases; scaffold `when`/sealed branches; skip
   generated/excluded paths) · Report format · Safety (never weaken a
   `.codecov.yml` target, never add a mocking dependency, read-only until
   approval, no version bump for tests-only).

2. **`.agents/skills/raise-coverage/references/coverage-signals.md`** — JaCoCo
   mechanics (rewritten from the draft, corrected to this repo): per-module vs
   `jacocoRootReport`; the real report paths above; XML structure and gap rules
   (`ci==0` uncovered line, `mb>0` partial branch); `xmllint`/Python extraction
   recipes; the generated-code exclusion; `.codecov.yml` scope; KMP
   source-set/exec-data variants. Ends with a short **"Future: Codecov triage
   tier"** appendix capturing the deferred two-tier design.

3. **`.agents/skills/raise-coverage/agents/openai.yaml`** — Codex parity
   (`interface.display_name` / `short_description` / `default_prompt`).

4. **`.claude/commands/raise-coverage.md`** — thin slash-command wrapper.
   `allowed-tools: Read, Edit, Write, Grep, Glob, Bash(./gradlew:*),
   Bash(git status:*), Bash(find:*)` (dropped `curl`/`WebFetch` — Codecov
   deferred). Body points at the skill, states the order, honors `testing.md` +
   `coding-guidelines.md`, notes no version bump for tests-only.

5. **`.agents/_TOC.md`** — add `23. [Raise test coverage](skills/raise-coverage/SKILL.md)`.

6. **`migrate`** — add `raise-coverage` to the Hugo-only prune block:
   `rm -rf ../.agents/skills/raise-coverage`,
   `rm -rf ../.claude/skills/raise-coverage`,
   `rm -f ../.claude/commands/raise-coverage.md` (mirrors existing JVM-skill entries).

7. **`.agents/tasks/raise-coverage.md`** — this file (`git mv` from
   `improve-test-coverage.md`).

> Dropped from the original plan: the standalone "install README". We author
> directly in `config`, so there is no separate install step — placement is
> documented here.

## Reusable test harness

```bash
# from the config repo root
mkdir -p tmp
git clone --recurse-submodules https://github.com/SpineEventEngine/base-libraries tmp/base-libraries
cd tmp/base-libraries && git submodule update --init --recursive

# lay down the published .agents/.claude baseline
./config/pull

# overlay the in-development skill on top of the baseline
cp -R <config>/.agents/skills/raise-coverage  .agents/skills/
cp    <config>/.claude/commands/raise-coverage.md  .claude/commands/
#   (.claude/skills → ../.agents/skills symlink resolves the skill automatically)
```

`pull` fetches the **published** config from master, so it won't contain
`raise-coverage` yet — the overlay-copy injects the in-dev version. Then execute
the skill's procedure against one module and verify.

## Plan

- [x] Housekeeping: `git mv` task file → `raise-coverage.md`; `TaskCreate` to track.
- [x] Author `SKILL.md`, `references/coverage-signals.md`, `agents/openai.yaml`,
      and the `.claude/commands/raise-coverage.md` wrapper.
- [x] Wire-up: add `_TOC.md` entry; add `raise-coverage` to the `migrate` prune block.
- [x] Harness + pilot: cloned `base-libraries` into `tmp/`, ran `./config/pull`,
      overlaid the skill. Localized gaps via Kover (`koverXmlReport`), then closed
      `EnvironmentType.equals()`/`hashCode()` with a Java+Truth test — the gap went
      to zero (4 tests green, nothing committed). Hardened `SKILL.md` +
      `coverage-signals.md` for the Kover frontend and for non-actionable
      (inline / unreachable) gaps surfaced by the pilot.
- [~] Review: `review-docs` over the new Markdown; sanity-check the `migrate`
      edit; confirm nothing staged in `tmp/base-libraries`.
- [ ] On merge: flip `status: done` and delete this task file per the
      `.agents/tasks/` lifecycle.

## Verification (the done bar)

- Files: `_TOC.md` resolves; `.claude/skills/raise-coverage/SKILL.md` resolves
  through the symlink; `openai.yaml` parses.
- Distribution: confirm the `migrate` prune block lists `raise-coverage` so
  Hugo-only repos won't receive it.
- End-to-end ✅: in `tmp/base-libraries`, `:environment:koverXmlReport` ran;
  `EnvironmentTypeTest` (Java + Truth, 4 tests) compiles and passes; re-parsing
  `build/reports/kover/report.xml` shows `EnvironmentType` `missedLINE`/
  `missedBRANCH` → 0 (was 2 / 1). **Nothing committed to base-libraries.**

## Risks / notes

- **Build feasibility**: `base-libraries` must build here (JDK + network; first
  build slow). Mitigation: build only the pilot module's test+report tasks. If it
  can't build, fall back to the analysis dry-run for the pilot and flag it — the
  four authored files still ship.
- **Pre-existing working-tree changes**: `Jacoco.kt` (→`0.8.14`) and the old task
  file are already modified. Leave `Jacoco.kt` alone (align docs to 0.8.14); only
  `git mv`/rewrite the task file.
- **No commits/pushes** anywhere unless explicitly authorized.

## Log

- 2026-05-29 — Shortlisted candidate skills; selected `clear-solutions` as the
  structural base with Kotest/taxonomy donors.
- 2026-05-29 — Recorded original decisions (mixed Java+Kotlin, Codecov + JaCoCo,
  author in `config` for all repos); drafted SKILL/reference/command/install.
- 2026-05-30 — Re-scoped with the user. Renamed skill `coverage-tests` →
  **`raise-coverage`**; **deferred Codecov** (JaCoCo-only v1); confirmed
  Kotlin→Kotest / Java→Truth; chose `tmp/` scratch dir; set the done bar to a
  local full-loop on a `base-libraries` module; added `openai.yaml` + `migrate`
  prune-block deliverables. Verified the test stack and JaCoCo report paths from
  `buildSrc`. Rewrote this task file from the plan; awaiting review.
- 2026-05-30 — Built the four files + wire-up; ran the `base-libraries` pilot.
  Findings that reshaped the skill: (1) consumer repos expose coverage through
  **Kover** (`koverXmlReport`, JaCoCo engine, JaCoCo-format XML) — not the
  per-module `jacocoTestReport` the draft assumed — so the skill is now
  frontend-aware (Kover or raw JaCoCo). (2) `inline`/`reified` functions and
  unreachable guards read as uncovered but are **non-actionable** (a passing test
  for `parse<T>` left `Parse.kt` `ci=0`), so the skill now filters them out.
  Demonstrated true closure on `EnvironmentType.equals()`/`hashCode()`
  (Java + Truth). `review-docs` running.
