---
slug: retire-buildsrc-orphans
branch: retire-buildsrc-orphans
owner: claude
status: in-review
started: 2026-11-11
---

## Goal

`./config/pull` removes from a consumer's `buildSrc` the files that `config` deleted in the
last two years, unless the consumer's own code still uses them, so that a forgotten `rm -f`
line in `migrate` can no longer leave stale sources behind.

## Context

- `migrate` copies `buildSrc` with `cp -R buildSrc ..`, which never deletes. Retired files
  were removed only through hand-written `rm -f` lists, and `JacocoConfig.kt` with its six
  helpers (`30b50335`, 2026-06-06) never got an `rm -f` line: consumers kept them for five
  months, and
  core-jvm's CI warned about them. config#841 fixes that case; this task generalizes it.
- Stacked on #841 (`fix-stale-buildsrc-warnings`); retarget to `master` when #841 merges.
- Scope set by the owner: only files deleted in the last two years.

## Plan

- [x] `scripts/remove-retired-buildsrc.sh` (with `--dry-run`): retired files from
      `git log --since="2 years ago" --diff-filter=D`; units are packages (`.kt`) and script
      plugins (`*.gradle.kts`); a unit stays if the consumer's own code uses it (resolved
      `<pkg>.<name>`, `<pkg>.*`, an own file in the package, a plugin ID), transitively.
- [x] `migrate` calls it after the explicit removals; #841's two blocks go.
- [x] `scripts/test-remove-retired-buildsrc.sh`, including a real `migrate` run.
- [x] shellcheck, the existing `scripts/test-migrate-*.sh`, and a `--dry-run` over local
      consumer checkouts.
- [ ] Review, then commit and open the PR stacked on #841 (on the owner's request).

## Log

- 2026-11-11 — plan approved; prototype confirmed on 13 local checkouts (core-jvm removes
  all 9 orphans; logging and time keep the `JacocoConfig` package).
- 2026-11-11 — implemented. New suite 60/60; the six existing script suites pass;
  shellcheck clean. `--dry-run` over the 13 checkouts matches the prototype. Known
  over-keeping (safe): if an orphan's text mentions a name that `config` still ships
  (`Compiler` in `dependency.local`), the orphan's package is kept.
- 2026-11-11 — review fixes: the script stops before removing anything when a search or
  read fails (it used to fail open), and keeps a unit with a file not committed or changed
  since, so whatever it removes `git checkout` can restore. Folded into #841 at the
  owner's request; the Gradle 9.9.1 and Kotlin bump go to a PR of their own.
