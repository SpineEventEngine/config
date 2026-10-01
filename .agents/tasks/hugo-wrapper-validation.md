---
slug: hugo-wrapper-validation
branch: hugo-wrapper-validation
owner: claude
status: in-review
started: 2026-10-01
---

## Goal

`migrate` distributes `gradle-wrapper-validation.yml` to Hugo-only repositories that
carry a Gradle Wrapper (`documentation`, `spine.io`) and keeps removing it from every
other repository. In a JVM repository, `setup-gradle` validates the wrapper.

## Context

- `74e30e2f` (#743) retired the workflow everywhere, on the grounds that
  `setup-gradle` validates the wrapper. Hugo-only repos never use `setup-gradle`:
  they keep a wrapper only to run Hugo through Gradle tasks. `documentation` runs
  `./gradlew` after a plain `setup-java`.
- `spine.io` restored the workflow by hand (`cf067589`), and the next pull would
  delete it again.
- Decision: `config` owns the workflow and distributes it, as it does
  `check-links.yml`. It does not merely stop deleting each repository's own copy.

## Plan

- [x] New `.github-workflows/gradle-wrapper-validation.yml`. The push trigger
      moves from `main` to `master`. Adds `permissions: contents: read` and
      `checkout@v6` / `wrapper-validation@v6`. Job name unchanged.
- [x] `migrate`: the `NEEDS_WRAPPER_VALIDATION` predicate (Hugo-only plus wrapper
      JAR), a `copy_workflows` skip, the Hugo-section copy (honouring
      `config:replaces`), and a guarded removal block.
- [x] `README.md`: a `.github-workflows` note and the retirement-pattern pointer.
- [x] `scripts/test-migrate-wrapper-validation.sh`: a regression suite.
- [x] Verify: the new and sibling `test-migrate-*` suites, `shellcheck`,
      `actionlint`, and dry runs on clones of `documentation` and `spine.io`.

## Log

- 2026-10-01: plan approved, executing.
- 2026-10-01: done. New suite passes (21 checks). It fails against `master`'s
  `migrate`, and against variants that drop the `copy_workflows` skip or the
  wrapper-JAR condition. The sibling `test-migrate-*` and `test-update-gitignore`
  suites pass. `shellcheck` reports 0 findings on both `master` and the branch,
  and `actionlint` is clean. Dry runs on clones: `documentation` gains config's
  copy, and `spine.io`'s copy is refreshed in place and never `git rm`ed.
