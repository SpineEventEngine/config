---
slug: plugin-pom-metadata
branch: worktree-plugin-pom-metadata
owner: claude
status: in-review
started: 2026-09-24
related-memories:
  - plugin-publications-created-late
---

## Goal

Every Maven publication that `spinePublishing` sets up or adopts carries the same
project-wide POM metadata as a standard publication: `<inceptionYear>`,
`<licenses>`, and `<scm>`. This includes the `pluginMaven` publication and
the plugin markers of `java-gradle-plugin` modules listed in
`modulesWithCustomPublishing`.

## Context

Found while adding SBOM generation (branch `generate-sboms`). The SPDX Gradle
Plugin reads licenses from dependency POMs, so the Spine plugin artifacts showed
up as `NOASSERTION` in the SBOMs of consumers.

Published evidence (Artifact Registry, `snapshots`):

- `io.spine.tools:validation-gradle-plugin:2.0.0-SNAPSHOT.464`,
  `time-gradle-plugin:2.0.0-SNAPSHOT.251`, `root-gradle-plugins`,
  `protobuf-setup-plugins`, and `jvm-tool-plugins` (`2.0.0-SNAPSHOT.423`) have
  no `<licenses>`, `<scm>`, or `<inceptionYear>`.
- In the same `protobuf-setup-plugins` module, the `fatJar` publication
  (`protobuf-setup-plugins-all`) has all three, while `pluginMaven` has none.
  The handler is applied to custom modules; it misses `pluginMaven` only.

### Root cause

`SpinePublishing.setUpPublishing()` applies the handler in `afterEvaluate` of
the module. For a module listed in `modulesWithCustomPublishing`, that action is
added while the root script is evaluated — before the module applies
`java-gradle-plugin`, which creates `pluginMaven` and the `*PluginMarkerMaven`
publications in an `afterEvaluate` action of its own. `CustomPublicationHandler`
iterated `project.publications.forEach { }`, a snapshot, so the publications
created afterwards never got `copyProjectAttributes()`.

In the opposite order — a module opening `spinePublishing { customPublishing = true }`
after applying `java-gradle-plugin` — the same loop did reach the plugin markers
and replaced their `groupId` with the project group, which breaks resolving
the plugin by its ID.

## Plan

- [x] Reproduce with a TestKit test (`CustomPublicationHandlerIgTest`) for each way
      to declare custom publishing: in the root project, in the module, and in both.
      Before the fix, 3 of the 4 root and module cases failed.
- [x] Guard against applying the attributes twice: with the `applied` check of
      `PublicationHandler.apply()` disabled, the "both" case fails with two licenses.
- [x] `CustomPublicationHandler.handlePublications()`: `configureEach` over
      `MavenPublication`s instead of `forEach`.
- [x] Split `PublicationHandler.copyProjectAttributes()`: the inception year,
      license, and SCM moved to `copyProjectWideAttributes()`, which plugin markers
      get alone, so that their coordinates, name, and description stay those of
      the plugin.
- [x] Share `isPluginMarker` with the SBOM code, which landed on `master` with its
      own private copy meanwhile: it is `internal` in `PublishingExts.kt` now.
- [x] Verify: `./gradlew :buildSrc:build detekt` (JDK 17).
- [ ] Commit and open a PR (awaiting the user).

## Follow-ups (not in this change)

- `core-jvm-compiler/gradle-plugin` removes `pluginMaven` and patches its marker
  POM by deleting the dependency whose `artifactId` is `gradle-plugin`. With this
  change, that dependency already reads `core-jvm-gradle-plugin`, so the marker
  gets it twice (same GAV, once with `runtime` scope) until the workaround matches
  the prefixed name or is dropped.
- `PublicationChecksums.registerCollectorIn()` rejects a Maven snapshot version in
  `afterEvaluate` with an eager `forEach`, so `pluginMaven` of a module listed in
  `modulesWithCustomPublishing` is only checked when the collector runs.
- The workarounds naming `pluginMaven` by hand in `validation`, `time`, and
  `ProtoTap` become redundant once they pull this change.
- POMs of standard publications have no `<name>`, `<url>`, or `<developers>`
  either, so none are added here. Adding them would change every published POM.
- Done in #765: `SpinePublishing.publishTo()` returned the `destinations` of
  the calling extension where it meant those of the parent, so a module opening
  `spinePublishing` without `destinations` failed instead of inheriting them.

## Log

- 2026-09-24 — root cause confirmed against the published POMs; test written
  first (red), fix applied (green); `:buildSrc:build detekt` passes.
- 2026-09-24 — added the "both" declaration after review: it is how `tool-base`
  declares `protobuf-setup-plugins` and `jvm-tool-plugins` (via `uber-jar-module`).
  All 104 `buildSrc` tests pass.
