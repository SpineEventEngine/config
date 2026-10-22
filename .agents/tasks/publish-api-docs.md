---
slug: publish-api-docs
branch: claude/publish-api-docs-a78bfa
owner: claude
status: in-review
started: 2026-10-21
---

## Goal

API documentation is published by a workflow of its own, `publish-api-docs.yml`,
on every push to `master`, and Maven publishing no longer waits for it or fails
because of it. (`publish` still runs Dokka for the Javadoc and HTML JARs it
uploads; what leaves it is the push to `gh-pages`.) The task that does the work
is named for what it does — `publishApiDocs` — and so are the plugin,
the extension, and the DSL around it.

## Context

- Before this change, `jvm-module.gradle.kts` made `publish` depend on `updateGitHubPages`, and
  most consumers did the same in their own `buildSrc/.../module.gradle.kts`
  (gcloud-jvm in its root `build.gradle.kts`). A docs failure breaks a Maven
  publication halfway, and every publication waits for the docs.
- `publishing.yml` must not be responsible for the docs (an earlier attempt to
  add a docs step there was reverted).
- The Pages update is safe to repeat: `Repository.commitAllChanges` commits with
  `--allow-empty`, `push()` retries with `git pull --rebase`.
- Only the docs read `REPO_SLUG` (`RepoSlug` is used by
  `forPublishingDocumentation` alone), `FORMAL_GIT_HUB_PAGES_AUTHOR`, and the
  `deploy_key_rsa` SSH key.
- `migrate` copies `buildSrc` with `cp -R`, which overlays but never deletes, so
  consumers would keep stale `UpdateGitHubPages*.kt` copies that no longer compile.
- Before this change, only the modules with a `publish` task pushed docs. `jvm-module` applies the
  plugin to every module, published or not (e.g. `logging`'s test modules), so
  running every module's task by name would push docs that were never pushed.
- Repositories that publish but document nothing: `bootstrap`, `template`.
- No deprecated aliases for the old names, as agreed with Alexander Yevsyukov.
  Consumer builds break until the `summit` wave updates them.
- Only release versions publish their docs. The plugin skipped snapshot versions
  until `bf19ed33` (2025-11-24) commented the skip out, pending a new publishing
  procedure; `2f1ee940` (2026-04-06) removed the commented-out code. This change
  restores it, as agreed with Alexander Yevsyukov: before, every merge pushed
  a `v/<version>` folder for its `-SNAPSHOT.N` version.

## Plan

- [x] Rename, package `io.spine.gradle.github.pages` kept:
  - `git mv` `UpdateGitHubPages.kt` → `PublishApiDocs.kt` (class `PublishApiDocs`),
    `UpdateGitHubPagesExtension.kt` → `PublishApiDocsExtension.kt` (class
    `PublishApiDocsExtension`, `fun Project.publishApiDocs { }`, extension name
    `publishApiDocs`).
  - `TaskName.updateGitHubPages` → `TaskName.publishApiDocs = "publishApiDocs"`.
  - References: `Update.kt`, `RepositoryExtensions.kt` (commit author name
    `PublishApiDocs Plugin`), `DokkaExts.kt` (`docsShippingTaskNames`, KDoc),
    `jvm-module.gradle.kts`, `DokkaSetupIgTest`, KDoc and comments.
  - `git mv` `Update.kt` → `DocsPublication.kt`: `updateGhPages()` →
    `internal publishToGitHubPages()`, `UpdateDocumentation` → `DocsPublication`
    (`run()` → `publish()`), `UpdateJavadocFormat`/`UpdateHtmlFormat` →
    `JavadocPublication`/`HtmlPublication`; no "update" wording is left.
- [x] `migrate`: delete the stale `UpdateGitHubPages.kt` and
      `UpdateGitHubPagesExtension.kt` from a consumer's `buildSrc`, as it does for
      `ProtoData.kt` and the other retired files.
- [x] Decouple: drop `publish.dependsOn(updateGitHubPages)` from `jvm-module`.
- [x] Root aggregate `:publishAllApiDocs`, a plain lifecycle task registered by
      `internal object PublishAllApiDocs`, which `spinePublishing { }` calls next to
      `:publicationState`:
  - depends on the `publishApiDocs` task of each published module that has one
    (the modules `spinePublishing` publishes), resolved once all projects are
    evaluated. Before, any module with a `publish` task pushed docs, so a
    `jvm-module` project publishing through its own `maven-publish`, outside
    `spinePublishing`, no longer does; none is known;
  - exists in every repository that runs the distributed workflows, including
    those with no documented module, where it does nothing;
  - a name of its own, distinct from the `publishApiDocs` of the modules, so that
    it never collides with the root's own task in a single-project build, and so
    that `./gradlew publishAllApiDocs` needs no path to select only it;
  - Why `spinePublishing`: it is the only root-level hook that every repository
    that publishes has, and it knows which modules are published.
  - Neither job depends on the other: `publish` and `publishApiDocs` do not
    depend on each other's tasks.
  - The `getOrRegister` helper moves from `PublicationChecksums.kt` to
    `io/spine/gradle/TaskName.kt`, so both places use it.
- [x] Test `PublishApiDocsIgTest` (TestKit, `--dry-run`): `:publishAllApiDocs` plans
      the docs of the published documented modules only; `publish` plans no docs
      task; a build with no documented module runs `:publishAllApiDocs`; in a
      single-project build it runs the project's own `:publishApiDocs`;
      a module that opens `spinePublishing { }` itself is included too; a snapshot
      version publishes nothing, in a multi-module and in a single-project build.
- [x] Snapshot versions: the plugin registers a no-op `publishApiDocs` that only
      says it skips the docs.
- [x] `.github-workflows/publish-api-docs.yml`, self-contained (no caller/reusable
      split, see below): push to `master`; `concurrency` per repository with
      `queue: max`; `contents: read`, `packages: read`; checkout with submodules,
      JDK 17, `setup-gradle`, decrypt `deploy_key_rsa`, `chmod`, then
      `./gradlew :publishAllApiDocs` with `GITHUB_TOKEN`, `FORMAL_GIT_HUB_PAGES_AUTHOR`,
      `REPO_SLUG`.
- [x] `publishing.yml`: remove `Decrypt Git SSH credentials`,
      `Set file system permissions`, and the `FORMAL_GIT_HUB_PAGES_AUTHOR` and
      `REPO_SLUG` variables, which only the docs read.
- [x] Verify: `:buildSrc:build detekt`, `actionlint` (ignore only the
      `concurrency.queue` error), `scripts/test-migrate-publishing-pin.sh`,
      reviewers (`gradle-review`, `kotlin-engineer`, `spine-code-review`,
      `review-docs`).
- [x] Hand-off note for the `summit` wave (below), also for the PR description.

### Not gated on the publication state

Before, the docs were pushed by the `publish` task, in the `Publish artifacts to
Maven` step, which the `state` check of `publishing.yml` skips when the version
is already published. The new workflow
does not ask: when `Publish` stops because the version was published from
another commit, or only in part, the docs of the commit are still copied over
the docs of that version (the `v/<version>` folder and the folder of the most
recent docs of each module; the copy overlays and never deletes). The version
checks of pull requests normally keep such a commit off `master`. Asking the
publication state would couple the jobs again, so the trade-off is accepted.

### Why one workflow file, not a caller and a reusable body

The `publish.yml` → `publishing.yml` split exists for the attestation: the signer
recorded by Sigstore is the file whose steps the job ran, and only a body hosted
in `config` raises the provenance to SLSA Build Level 3. The docs carry no
attestation, so the split would buy nothing there. A distributed workflow is
overwritten by every `./config/pull`, so its steps already move in lockstep with
`buildSrc`, as `build-on-ubuntu.yml` does; a reusable body would only add a
second `CONFIG_COMMIT` pin to `migrate` and its tests. Nor would it protect a
secret: the consumer's Gradle build runs in the same job as the decrypted deploy
key either way.

## Consumer migration (for the `summit` wave)

The `summit` wave is the one-time roll-out of this change to the consumer
repositories, run from the `summit` repository.

### What `./config/pull` does by itself

- `buildSrc` gets `PublishApiDocs.kt` and `PublishApiDocsExtension.kt`; `migrate`
  deletes the retired paths below, and its closing `git add ./buildSrc/src`
  stages the deletions. The stale copies would not compile anyway, as they refer
  to the removed `TaskName.updateGitHubPages`.
  - `buildSrc/src/main/kotlin/io/spine/gradle/github/pages/UpdateGitHubPages.kt`
  - `buildSrc/src/main/kotlin/io/spine/gradle/github/pages/UpdateGitHubPagesExtension.kt`
  - `buildSrc/src/main/kotlin/io/spine/gradle/github/pages/Update.kt`, now
    `DocsPublication.kt`; the stale copy refers to the removed `UpdateGitHubPages`.

  `github/pages/TaskName.kt` stays; only its constant is renamed.
- `.github/workflows/publish-api-docs.yml` is added, from `.github-workflows/`,
  like the other distributed workflows (a `config:replaces publish-api-docs.yml`
  directive withholds it). It is self-contained: not a caller of a reusable
  workflow, so there is no `@CONFIG_COMMIT` pin. It decrypts the repository's own
  `.github/keys/deploy_key_rsa.gpg` with the repository's `GIT_CREDENTIALS_KEY`
  secret, as `publishing.yml` did before.
- `publish.yml` is re-pinned to a `publishing.yml` that no longer decrypts the
  deploy key or passes `REPO_SLUG`/`FORMAL_GIT_HUB_PAGES_AUTHOR`.
- `spinePublishing { }` now registers `publishAllApiDocs` in the root project.
- **Behavior change:** only release versions publish their docs to the site. For
  a snapshot version, the task of each module only logs that it skips the docs,
  so a `publish-api-docs.yml` run in a repository on a snapshot version ends green
  without pushing anything. The Javadoc and HTML archives of a snapshot version
  are still built by Dokka and published to Maven repositories by `publish`.
  Before, every merge also pushed the docs of its `-SNAPSHOT.N` version to the
  site.
- The commits on `gh-pages` are authored by `PublishApiDocs Plugin` instead of
  `UpdateGitHubPages Plugin`, and their messages say "Publish the `<format>`
  documentation of the module …" instead of "Update `<format>` documentation …".

### The new names

All in `io.spine.gradle.github.pages`, with no deprecated aliases:

| Before | After |
|---|---|
| `Project.updateGitHubPages { }` | `Project.publishApiDocs { }` |
| `UpdateGitHubPagesExtension` | `PublishApiDocsExtension` (members unchanged) |
| `UpdateGitHubPages` (plugin) | `PublishApiDocs` |
| `TaskName.updateGitHubPages` | `TaskName.publishApiDocs` |
| extension `updateGitHubPages` | extension `publishApiDocs` |
| task `updateGitHubPages` | task `publishApiDocs`, plus `publishAllApiDocs` in the root project |

### What each consumer must change, in the same PR as the pull

The pulled `buildSrc` no longer has `updateGitHubPages`, so a consumer-owned
script that calls it stops compiling. In every consumer-owned Gradle file
(`buildSrc/src/main/kotlin/module.gradle.kts`, root and module
`build.gradle.kts`, other consumer-owned scripts):

1. Rename the import and the call; the block body stays as it is:

   ```kotlin
   import io.spine.gradle.github.pages.updateGitHubPages   // before
   import io.spine.gradle.github.pages.publishApiDocs      // after

   updateGitHubPages { rootFolder.set(rootDir) }            // before
   publishApiDocs { rootFolder.set(rootDir) }               // after
   ```

2. Delete the dependency of `publish` on the docs, in whatever form it takes,
   typically:

   ```kotlin
   tasks.named("publish") {
       dependsOn("${project.path}:updateGitHubPages")
   }
   ```

   Left in place, it fails `publish` when Gradle builds the task graph
   ("Task with path ':x:updateGitHubPages' not found"). Do not rename it to
   `publishApiDocs`: that would couple the two jobs again.
3. Rename any other use of `UpdateGitHubPages`, `UpdateGitHubPagesExtension`,
   `TaskName.updateGitHubPages`, or the `"updateGitHubPages"` task name.
   Renaming an enclosing function such as `configureGitHubPages()` is optional.
4. If a consumer-owned workflow excludes the task (`-x updateGitHubPages`), drop
   the exclusion: Gradle fails on an excluded task it cannot find. The only known
   ones, in Chords (below), sit in the distributed `publish.yml`, which the pull
   overwrites.

Find every place: `git grep -nE 'updateGitHubPages|UpdateGitHubPages'` (it does
not descend into the `config` submodule) — expect no hits afterwards. Check:
`./gradlew publishAllApiDocs --dry-run` lists the `publishApiDocs` tasks of the
published modules, and `./gradlew publish --dry-run` lists no `publishApiDocs` task.

Publish the docs of the whole build with `publishAllApiDocs`. An unqualified
`publishApiDocs` selects the task of every project that has one, including the
modules that are not published.

### Per repository, from the survey on 2026-10-21

This list was checked against each repository's `master` on 2026-10-21, by the
`summit` session that runs the wave.

- `module.gradle.kts` with both the call and the `publish` dependency: core-jvm,
  compiler, validation, base-libraries, time, delivery, ProtoTap,
  core-jvm-compiler, testlib, jdbc-storage.
- gcloud-jvm: the same two edits in `setupPublishing()` of the root
  `build.gradle.kts`.
- `jvm-module` users (logging, base-types, money, reflect, dokka-tools, elastic):
  nothing, unless they name the old task or function themselves.
- tool-base and change: rename the call (`configureGitHubPages()` in tool-base,
  a top-level call in change's `module.gradle.kts`). **Behavior change:**
  both registered the docs tasks but did not run them (their `gh-pages` branches
  have not changed since 2025-12-02 and 2025-12-22, respectively); now
  `publishAllApiDocs` publishes the docs of their published modules on their next
  release version, as agreed with Alexander Yevsyukov.
- Archived on GitHub, so out of scope: web, dart, text, model-compiler,
  model-tools.
- Chords and Chords-Gradle-plugin: the pull erases the `-x updateGitHubPages`
  hand-edited into their `publish.yml`. **Behavior change:** their docs get
  published by the new workflow, from their next release version. Check that
  each repository has `.github/keys/deploy_key_rsa.gpg` and `GIT_CREDENTIALS_KEY`,
  or drop the plugin call if their docs were off on purpose.
- Repositories that document nothing (bootstrap, template): nothing to do;
  `publishAllApiDocs` does nothing there.
- core-java-1x `publish-v1.yml` runs the v1 line with its own `buildSrc`: out of
  scope.

Secrets stay as they are: `GIT_CREDENTIALS_KEY` and
`.github/keys/deploy_key_rsa.gpg` are now used by `publish-api-docs.yml` only.
The workflow decrypts the key in every repository that gets it, even where no
module is documented, so a repository missing either goes red on each push to
`master`. `publishing.yml` decrypted the key on every publication too, so every
repository that publishes has them.

## Log

- 2026-10-21 — explored; drafted; user chose no deprecated aliases.
- 2026-10-21 — plan approved, executing.
- 2026-10-21 — implemented. `PublishApiDocsIgTest` needed two fixture fixes:
  Gradle 9 rejects an included project without a directory, and TestKit reports
  no tasks for `--dry-run`, so the test reads them from the output.
- 2026-10-21 — `:buildSrc:build detekt` green, 287 tests. Answered the `summit`
  session's questions; took its corrections to the per-repository list.
- 2026-10-21 — `kotlin-engineer` and `review-docs` findings applied: internal
  companion, `val` extension properties, dead `registerNoOpTask()` removed,
  KDoc and comment wording, a test for a module opening `spinePublishing { }`
  itself. Not done: citing docs for `concurrency.queue` (as in `publishing.yml`),
  a shared `buildSrcClasspath()` test helper (ten copies; a separate change).
- 2026-10-21 — `gradle-review`: approve. Applied the wording nits and a note on
  the Gradle cache of the workflow. Left as they were: the module task's
  `dependsOn` on the copy tasks and the parallel pushes of modules to
  `gh-pages` (both pre-existing; pushes retry, and a re-run is safe).
- 2026-10-21 — `spine-code-review`: approve with changes. Applied: the Chords
  note (the pull erases their `-x`), the trade-off of not gating on the
  publication state, `publish-api-docs.yml` removed from Hugo-only repositories,
  a README paragraph.
- 2026-10-21 — verified after all review changes: `:buildSrc:build detekt` green,
  288 tests; `actionlint` clean but for `concurrency.queue`;
  `scripts/test-migrate-publishing-pin.sh` and `shellcheck migrate` pass.
- 2026-10-21 — committed as `10af7ffd`; merged `origin/publishing-reliability`
  (`3fe01881`) as `c1c3684c`. Resolved `publishing.yml` by keeping its credential
  steps after the publication check, gated on `unpublished`, and dropping the SSH
  steps, `REPO_SLUG`, `FORMAL_GIT_HUB_PAGES_AUTHOR`, and the mentions of the
  GitHub Pages update among the tasks of `publish`. Merged tree: 295 tests green.
- 2026-10-21 — pre-PR pass: `kotlin-engineer`, `spine-code-review` approve;
  `review-docs` approves with changes, applied.
- 2026-10-22 — restored the snapshot skip at the user's request: a snapshot
  version registers a no-op `publishApiDocs`, merged with the root aggregate in
  a single-project build. Two new `PublishApiDocsIgTest` cases.
- 2026-10-22 — Codex review: queued runs may start out of commit order, so an
  older release run could overwrite the most recent docs. A run now replaces
  them only if no newer version is published under `v/` (`newestAbove`, by
  `VersionComparator`); `NewestAboveSpec`. 302 tests green.
- 2026-10-22 — pinned that the snapshot skip concerns the site only: a new
  `PublishApiDocsIgTest` case shows that `publish` of a snapshot version still
  plans Dokka and `javadocJar`, and no `publishApiDocs`.
- 2026-10-22 — the root aggregate gets a name of its own, `publishAllApiDocs`, at
  the user's request. The module task and the no-op go back to a plain
  `register`; the aggregate depends on `TaskProvider`s, the root's own task
  included in a single-project build. The workflow runs `:publishAllApiDocs`.
- 2026-10-22 — the aggregate's registration moves out of the companion of the
  per-module `PublishApiDocs` plugin into `internal object PublishAllApiDocs`
  (`PublishAllApiDocs.kt`), as `PublicationChecksums` does; the task stays
  a plain lifecycle one, with no action of its own.
- 2026-10-22 — no "update" wording left in the publication code, at the user's
  request: `Update.kt` is now `DocsPublication.kt`, with `publishToGitHubPages()`,
  `DocsPublication`, `JavadocPublication`, `HtmlPublication`; `migrate` also deletes
  a consumer's stale `Update.kt`. Not committed yet, for the user's review.
