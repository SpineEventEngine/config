---
slug: dokka-maven-local-gate
branch: dokka-publishing-gate
owner: claude
status: in-review
started: 2026-10-08
related-memories: []
---

## Goal

Make `dokka-setup` skip Dokka tasks in a build that publishes to Maven Local
only (the build that feeds integration tests), while keeping them running
everywhere else, with configuration-cache compatibility kept.

## Context

- The gate never skips anything under Dokka 2.x: the predicate accepts a graph
  holding `publish` or any task whose name contains `dokkaGenerate`, and every
  `DokkaBaseTask` is such a task (`dokkaGenerate` itself is a `DokkaBaseTask`;
  `logLink…` enters the graph only as a finalizer of one). So
  `publishToMavenLocal` → `javadocJar` → `dokkaGeneratePublicationJavadoc` runs.
- History of the predicate: `publish*` except `publishToMavenLocal*` (2024-03),
  `publish` only (2025-03), `publish || dokkaGenerate*` (2025-04, the Dokka 2.x
  migration, which made it always true).
- Builds on the uncommitted `dokka-config-cache` work (`runOnlyInPublishingGraph`
  and `DokkaSetupIgTest`), copied into this tree as the baseline.
- Rule agreed with the user (2026-10-08): run Dokka when a Dokka task is
  requested on the command line, or the graph holds `publish`, or the graph holds
  no `PublishToMavenLocal` task. CI's `publish -x test` and `dokkaGenerate`,
  `updateGitHubPages`, and `publishPlugins` keep their documentation.
  `updateGitHubPages` counts as `publish` does, so running it along with
  `publishToMavenLocal` keeps the documentation too (from the Gradle review).
- A skipped Dokka task leaves `javadocJar`/`htmlDocsJar` with a manifest only,
  or with the output of an earlier Dokka run.
  Checked on Gradle 9.8.1: the JAR is still built, and `publishToMavenLocal`
  succeeds. Acceptable for Maven Local, which feeds integration tests only.

## Plan

- [x] Replace `Project.runOnlyInPublishingGraph(tasks)` with
      `Project.skipDokkaWhenPublishingToMavenLocal()` in `DokkaExts.kt`; decide in
      `taskGraph.whenReady`, keep the result in a `Property<Boolean>`.
- [x] Keep the deprecated `Task.isInPublishingGraph()` as it was; repoint its
      deprecation message.
- [x] Call the new function from `dokka-setup.gradle.kts`.
- [x] Extend `DokkaSetupIgTest`: a fixture publication with a `javadocJar` fed by
      the probe, a file-based repository, and a private Maven Local; cases for
      Maven Local only (skipped), `publish`, both, Maven Local plus a requested
      Dokka task, Maven Local plus `updateGitHubPages`, and no publishing. The probe
      is named like `dokkaGeneratePublicationJavadoc`; named `docsProbe`, it hid
      the bug. The project is named `dokka-sample` to cover `dokka` in a task path.
- [x] Verify: `./gradlew :buildSrc:test detekt` with JDK 17.
- [x] Log the outcome in `buildsrc-gradle-review-findings.md`.

## Log

- 2026-10-08 — rule confirmed by the user; executing.
- 2026-10-08 — implemented. `./gradlew :buildSrc:test detekt` passes on JDK 17
  (171 tests). Against the previous gate, only the Maven Local case fails.
- 2026-10-08 — `gradle-review` and `review-docs`: APPROVE WITH CHANGES; applied.
  Added `updateGitHubPages` to the tasks that need the documentation, and the
  `dokka-sample` project name. Removing either guard fails its own case.
  `./gradlew :buildSrc:test detekt` passes (172 tests).
