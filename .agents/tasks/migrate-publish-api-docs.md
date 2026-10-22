---
slug: migrate-publish-api-docs
branch: migrate-publish-api-docs
owner: claude
status: in-progress
started: 2026-10-22
related-tasks:
  - publish-api-docs
---

## Goal

`./config/pull` leaves a consumer that called the old `updateGitHubPages { }` API
with a build that compiles: `migrate` rewrites the known uses of the API that
`PublishApiDocs` replaced in the consumer's own Gradle scripts, and names every
use it cannot rewrite, with its replacement.

## Context

- `58cd2d4a` (`publish-api-docs`) renamed the `UpdateGitHubPages` plugin to
  `PublishApiDocs`, with no deprecated aliases. `migrate` deletes the stale plugin
  sources, but `module.gradle.kts`, which it preserves, still calls
  `updateGitHubPages { }`; `buildSrc` then fails with
  `Unresolved reference 'updateGitHubPages'` and `'rootFolder'`.
- Found while re-shipping `time` in the `summit` wave; its fix by hand is
  `54c0e602` in `time`, which mirrors `jvm-module.gradle.kts`.
- The other consumers hit the same break on their next pull.

### Survey of `origin/master`, 2026-10-22

Consumer-owned files that use the old API (files that `config` distributes are
left out, as the pull overwrites or deletes them):

| Form | Repositories |
|---|---|
| `fun Module.setupDocPublishing()` / `setupPublishing()` with `updateGitHubPages { }` and `tasks.named("publish") { dependsOn("${project.path}:updateGitHubPages") }` | core-jvm, validation, compiler, jdbc-storage, core-jvm-compiler, ProtoTap, delivery; gcloud-jvm in the root `build.gradle.kts` |
| `fun Module.configureGitHubPages()` with `updateGitHubPages { }` (`updateGitHubPages() {` in time), and `project.afterEvaluate { val publish = tasks.findByName("publish"); publish?.dependsOn(...) }` | base-libraries, time, testlib |
| `fun Module.configureGitHubPages()`, no `publish` dependency | tool-base |
| A top-level `updateGitHubPages() { }` | change |
| Nothing (`jvm-module` users) | logging, base-types, money, reflect, dokka-tools, BuildSpeed |

Every file imports `io.spine.gradle.github.pages.updateGitHubPages`.

## Plan

- [x] `scripts/migrate-publish-api-docs.sh [ROOT]`, called by `migrate`:
  - Candidates: the Kotlin sources and scripts under `buildSrc/src`, and the
    tracked `*.gradle.kts` files elsewhere, that name the old API.
  - Rewrites, each idempotent:
    - the import of `updateGitHubPages` → `publishApiDocs`;
    - the call `updateGitHubPages {` / `updateGitHubPages() {` → `publishApiDocs {`;
    - the two known forms of the `publish` dependency on the docs task: deleted,
      with the blank line before them;
    - a `configureGitHubPages()` defined in the same `.gradle.kts` script →
      `configureApiDocs()`, with the KDoc of `jvm-module.gradle.kts` when it has
      none.
  - A warning, with the file, the line, and the replacements, for every use left
    afterwards, and for any tracked workflow that names the old task.
  - Writes a file only when its content changes; keeps CRLF line endings.
- [x] `migrate` calls it after the workflows are copied; the closing
      `git add ./buildSrc/src` stages the `buildSrc` changes.
- [x] `scripts/test-migrate-publish-api-docs.sh`: fixtures from the surveyed
      forms, expected output, idempotence, warnings, CRLF, a run of the real
      `migrate`.
- [x] Verify that the rewritten real files compile against `config` master.
- [ ] `./gradlew detekt`, `./gradlew :buildSrc:test`, `shellcheck`.

## Log

- 2026-10-22 — surveyed the consumers; drafted.
- 2026-10-22 — implemented. On copies of the 13 files, the script rewrote each
  and left nothing to report; `time`'s result is byte-identical to `54c0e602`,
  and a second run changes nothing.
- 2026-10-22 — ran the real `migrate` over `origin/master` copies of time,
  compiler, testlib, tool-base, change, and core-jvm, with this branch as `config`.
  Each `buildSrc` compiled (`./gradlew help` with a minimal root;
  `:buildSrc:compileKotlin` ran, not from the cache). The control, compiler's
  unmigrated `module.gradle.kts`, fails with `Unresolved reference
  'updateGitHubPages'` and `'rootFolder'`.
- 2026-10-22 — `test-migrate-publish-api-docs.sh`: 28 checks pass, and fail when
  the `afterEvaluate` rule is disabled. The other `migrate` suites and
  `shellcheck` pass.
