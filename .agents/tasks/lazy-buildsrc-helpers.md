---
slug: lazy-buildsrc-helpers
branch: claude/lazy-buildsrc-helpers-093064
owner: claude
status: in-review
started: 2026-10-29
related-memories:
  - config-build-verification
  - plugin-publications-created-late
---

## Goal

The `buildSrc` helpers wire and configure tasks without creating them, so a build that
does not run a task does not pay for it. `./gradlew help` in `core-jvm` creates fewer
than the 213 of 1,030 tasks it creates on `master` (`2f6f370c`): 139 with this change.

Addresses [#820][issue-820].

## Context

- A lookup by name (`findByName`, `getByName`, `getTask`) creates a registered task.
  `tasks.named { it == name }` is a live collection: it creates nothing, matches a task
  registered later, and stays empty if the task is absent. `tasks.named(name)` creates
  nothing either, but fails if the task is not registered yet.
- Sibling issues cover the plugins: SpineEventEngine/compiler#98,
  SpineEventEngine/tool-base#198, SpineEventEngine/core-jvm-compiler#122.

## Plan

- [x] Apply the draft patch of the issue (six call sites).
- [x] Make the remaining lookups lazy: `javadocTask()`, `dokkaHtmlTask()`,
      `dokkaJavadocTask()` and their callers, `LicenseReporter.mergeAllReports()`,
      `generateProto()`, `setRemoteDebug()`. Deprecate the now unused `getTask()`.
- [x] `hasProto()`: no change, see below.
- [x] `pinPackaging()`: option 2, chosen by the maintainer, see below.
- [x] Verify: `./gradlew :buildSrc:build detekt`; `help` and `build` in a copy of
      `core-jvm`.

## Decisions

### `ProtoExts.hasProto()`

`hasProto()` reads `srcDirs` of each `proto` source directory set. The Protobuf Gradle
Plugin adds the output of `extractProto` there (`proto.srcDir(extractProtoTask)`), so
the read creates the task. The extracted directory exists only after a build has run, so
on a clean checkout, such as CI's, it never counts.

The plugin reads the same `srcDirs` itself once the project is evaluated, in every project
that applies `idea` (`ProtobufPlugin.groovy:272`, `postConfigure` →
`plugins.withId("idea")`). Spine repos apply `idea` to all projects. So whatever
`hasProto()` does, `extractProto` is still created.

1. **Keep it as is (recommended).** Nothing to gain while `idea` is applied.
2. **Conventional directories first.** Check `src/<source set>/proto`, then all `srcDirs`
   only if those hold no files. Same result as today. Tried it: in `core-jvm`, the
   six modules with Proto files had `extractProto` created by the plugin instead, and the
   two without by the fallback. The count did not change, so it was reverted.
3. **Conventional directories only.** Same as 2 in a project with `idea`. Without it,
   spares `extractProto`, but misses a directory added with `proto.srcDir(...)` (only
   `core-java-1x` does so among the local Spine checkouts). It would also ignore protos
   extracted by an earlier local build, so a local `publishToMavenLocal` would publish
   the same `proto` JARs as CI.

The lever is the `idea` plugin, not `hasProto()`. Gradle 9.8 deprecates only its tasks
generating IDEA project files (and `idea.pathVariables`), for removal in Gradle 10.
The plugin and its `idea { }` DSL, which IntelliJ reads on a Gradle import and which the
Protobuf plugin uses here, stay. `core-jvm` applies `idea` without configuring it. Dropping
it there spares `extractProto`, but leaves IntelliJ without the Proto directories that the
Protobuf plugin marks. That is a consumer-side decision outside this task.

### `PublicationSbom.pinPackaging()`

Reading `pom.packaging` without an explicit value makes Gradle take the artifacts of the
component into the publication and read the classifier and extension of each, which
creates every archive task the publication carries. `pinPackaging()` must read it before
the SBOM is added, once the publication is final (see `sbom-pom-packaging`, PR #775).
`MavenPom.packaging` is a plain value, so it cannot be set lazily.

1. **Keep the read.** Correct by construction for any publication. Cost: the archive
   tasks of every publication, in every build.
2. **Set the packaging where the build knows it.** `StandardJavaPublicationHandler`
   publishes the `java` component, so it could set `pom.packaging = "jar"`. The pin then
   reads back the explicit value and resolves nothing, as for the `osgi` case of
   `PublicationSbomIgTest`. Custom publications keep the calculated pin. Risk: two sources
   of truth. If a module gives its `jar` task a classifier, the POM says `jar` with no
   main JAR, where today it says `pom`. `core-jvm-compiler#115` already sets
   `pom.packaging = "jar"` by hand for two publications.
3. **Give the SBOM a classifier.** Then it does not count for the packaging and no pin is
   needed. Rejected in #770: it breaks the `<artifactId>-<version>.spdx.json` convention
   of the SPDX Maven Plugin.
4. **Fix `<packaging>` in `pom.withXml`.** Rejected: Gradle also picks the main artifact
   by the packaging (`determineMainArtifact()`), which the XML does not change.

Measured in the copy of `core-jvm`: with the other rows lazy, `pinPackaging()` is the
first to create 36 tasks (`jar`, `sourcesJar`, `javadocJar`, `htmlDocsJar`, `protoJar`,
`testJar`, `testFixturesJar`, and one `compileJava`). Option 2, tried there with
`pom.packaging = "jar"` next to `from(javaComponent)`, takes `help` from 175 to 139.
Nothing else in `help` creates those tasks. All seven published modules of `core-jvm` use
the standard handler.

Decision: 2, taken by the maintainer on 2026-10-29. `StandardJavaPublicationHandler` sets
`pom.packaging = "jar"` when it publishes the `java` component. The guard runs when the
task writing the POM of `mavenJava` is created, which `help` never does: it fails the build
unless the publication has exactly one main artifact (no classifier, the SBOM aside), and
that a JAR. It skips the check if the build has set a packaging of its own.
`PublicationSbomIgTest` covers both: `help` creates none of the archive tasks of `api` and
`impl`, and a classified `jar` of `api` fails writing its POM. Searched the local Spine
checkouts: only `core-jvm-compiler` sets a packaging (`jar`, on two custom publications),
and `uber-jar-module` publishes with custom publishing, so neither is affected.

## Log

- 2026-10-29 — baseline in a copy of `core-jvm`: `help` creates 213, avoids 817.
- 2026-10-29 — with the change: `help` creates 175 (the draft alone: 176). The rows of the
  draft now hand their tasks to `pinPackaging()`, which creates 36. Tried and reverted a
  `hasProto()` change, see above.
- 2026-10-29 — `./gradlew :buildSrc:build detekt` passes, 315 tests. In the `core-jvm`
  copy, `build sourcesJar javadocJar htmlDocsJar protoJar --dry-run` schedules the same
  393 tasks in the same order as with `master`'s `buildSrc`. The same build without
  `--dry-run` passes, writing non-empty HTML and Javadoc JARs.
- 2026-10-29 — option 2 for `pinPackaging()`: `help` creates 139. The two new
  `PublicationSbomIgTest` cases fail without `declareJarPackaging()` and pass with it.
  `./gradlew :buildSrc:build detekt` passes, 317 tests. `generatePomFileForMavenJavaPublication`
  passes the guard in all seven modules of the `core-jvm` copy, and their POMs are
  byte-identical to those of `master`'s `buildSrc`.

[issue-820]: https://github.com/SpineEventEngine/config/issues/820
