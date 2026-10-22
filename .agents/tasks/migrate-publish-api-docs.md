---
slug: migrate-publish-api-docs
branch: migrate-publish-api-docs
owner: claude
status: in-progress
started: 2026-10-22
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
- Found while re-shipping `time` in the `summit` wave. The fix by hand,
  `54c0e602` in `time`, mirrors `jvm-module.gradle.kts`.
- The other consumers hit the same break on their next pull.

### Survey of `origin/master`, 2026-10-22

Consumer-owned files that use the old API (files that `config` distributes are
left out, as the pull overwrites or deletes them):

| Form | Repositories |
|---|---|
| A, `tasks.named` | `core-jvm`, `validation`, `compiler`, `jdbc-storage`, `core-jvm-compiler`, `ProtoTap`, `delivery`; `gcloud-jvm` in the root `build.gradle.kts` |
| B, `afterEvaluate` | `base-libraries`, `time`, `testlib` |
| C, no dependency | `tool-base` |
| D, top-level call | `change` |
| None (`jvm-module` users) | `logging`, `base-types`, `money`, `reflect`, `dokka-tools`, `BuildSpeed` |

- **A** — `fun Module.setupDocPublishing()` or `setupPublishing()` calls
  `updateGitHubPages { }`, then
  `tasks.named("publish") { dependsOn("${project.path}:updateGitHubPages") }`.
- **B** — `fun Module.configureGitHubPages()` calls `updateGitHubPages { }`
  (`updateGitHubPages() {` in `time`); elsewhere in the script,
  `project.afterEvaluate { val publish = tasks.findByName("publish"); publish?.dependsOn(...) }`.
- **C** — `fun Module.configureGitHubPages()` calls `updateGitHubPages { }`; no
  `publish` dependency.
- **D** — a top-level `updateGitHubPages() { }`.

Every file imports `io.spine.gradle.github.pages.updateGitHubPages`.

## Plan

- [x] `scripts/migrate-publish-api-docs.sh [ROOT]`, called by `migrate`:
  - Candidates: the Kotlin sources and scripts under `buildSrc/src`, and the
    tracked `*.gradle.kts` files elsewhere, that name the old API.
  - Rewrites, each idempotent:
    - the import of `updateGitHubPages` → `publishApiDocs`;
    - the call `updateGitHubPages {` / `updateGitHubPages() {` → `publishApiDocs {`;
    - the two known forms of the `publish` dependency on the docs task: deleted,
      with the blank line before them, or else the one after;
    - a `configureGitHubPages()` defined in a `.gradle.kts` script, and its calls →
      `configureApiDocs()`, with the KDoc of `configureApiDocs()` from
      `jvm-module.gradle.kts` when the function has none.
  - A warning, with the file, the line, and the replacements, for every use left
    afterwards, and for any tracked workflow that names the old task.
  - Writes a file only when its content changes; keeps CRLF line endings.
- [x] `migrate` calls it after the workflows are copied; the closing
      `git add ./buildSrc/src` stages the `buildSrc` changes.
- [x] `scripts/test-migrate-publish-api-docs.sh`: fixtures from the surveyed
      forms, expected output, idempotence, warnings, CRLF, a run of the real `migrate`.
- [x] Compile the rewritten real files against `config` master.
- [x] `shellcheck` and the `migrate` test suites.
- [x] `./gradlew detekt`, `./gradlew -p buildSrc test`.

## Log

- 2026-10-22 — surveyed the consumers; drafted.
- 2026-10-22 — implemented. On copies of the 13 files, the script rewrote each
  and left nothing to report; `time`'s result was byte-identical to `54c0e602`,
  and a second run changed nothing.
- 2026-10-22 — ran the real `migrate` over `origin/master` copies of `time`,
  `compiler`, `testlib`, `tool-base`, `change`, and `core-jvm`, with this branch as
  `config`. Each `buildSrc` compiled (`./gradlew help` with a minimal root;
  `:buildSrc:compileKotlin` ran, not from the cache). The control, the unmigrated
  `module.gradle.kts` of `compiler`, failed with
  `Unresolved reference 'updateGitHubPages'` and `'rootFolder'`.
- 2026-10-22 — `test-migrate-publish-api-docs.sh`: 28 checks passed, and failed
  when the `afterEvaluate` rule was disabled. The other `migrate` suites and
  `shellcheck` passed; `detekt` passed, and so did 315 `buildSrc` tests.
- 2026-10-22 — `review-docs`: approve with changes; applied.
- 2026-10-22 — `spine-code-review`: approve with changes; applied. A `dependsOn` is
  deleted only when the docs task is its single argument; Git lists paths with
  `-z`, so a non-ASCII path is not missed; a declaration of `updateGitHubPages()`
  is not taken for a call; the temporary file lives outside the consumer's tree;
  the paths and the KDoc reach `awk` through the environment. The suite has 38
  checks now, among them a drift check of the KDoc against
  `jvm-module.gradle.kts`; each new guard, reverted, fails it. The outputs for
  the 13 real files did not change. The JVM fixtures of the publishing-pin and
  wrapper-validation suites now carry the script.
- 2026-10-22 — `publish-api-docs.yml` gets the swap step of `build-on-ubuntu.yml`,
  before the deploy key is decrypted, after a Codex finding on `time#202`: for
  a release, the job runs Dokka for every published module, in parallel.
