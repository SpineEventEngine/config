---
slug: sbom-custom-publications
branch: improve-sbom-generation
owner: claude
status: in-review
started: 2026-09-25
related-memories:
  - config-build-verification
  - pom-report-per-project-collectors
  - design-over-detekt-thresholds
---

## Goal

Make the SBOM of a custom publication describe what that publication publishes: the
dependencies its POM declares as `DEPENDS_ON`, and the modules and libraries packed into
its artifact as `CONTAINS`. SBOMs of component-based publications stay as they are.

Closes [#768][issue-768].

## Context

- #764 writes the SBOM of every JVM publication from `runtimeClasspath`. A publication
  made with `artifact(...)` — custom publications, `uber-jar-module` — publishes
  something else: a hand-written POM, and content packed into its JAR.
- SPDX Gradle Plugin 0.12.0 builds one document per target from a *list* of
  configurations, deduplicating packages across them. It writes only `DEPENDS_ON`
  edges, each package under its first discovery (a DFS spanning tree). The DFS of a later
  root walks *through* known packages and hangs new ones under them. Multi-file
  components become a container package that `CONTAINS` a package per file.
- `MavenPublication` instances are `ExtensionAware` (decorated) — verified on Gradle 9.7.1.
- Shadow 9.6.1 exposes what a fat JAR packs only as files: `ShadowJar.includedDependencies`
  (its `configurations` minus what `dependencyFilter` excludes). `TaskProvider.map` makes
  the task a producer of the mapped value; `flatMap` takes the inner provider's producer.
- Real shapes: tool-base's `intellij-platform*` shade only JetBrains artifacts and declare
  the rest in the POM; `core-jvm-compiler`'s `fatJar` filters modules out and declares
  25 by hand; its `pluginJar` packs `grpc`, `ksp`, `routing` through `tasks.jar`.
- Nothing may resolve in the root `projectsEvaluated` hook
  ([[pom-report-per-project-collectors]]); providers are evaluated by the module's own task.

## Design

Per-publication DSL, next to `artifact(...)` and the POM:

```kotlin
create<MavenPublication>("fatJar") {
    artifact(tasks.shadowJar)
    sbom {
        dependencies(fatJarPom)  // A resolvable configuration; default: `runtimeClasspath`.
        bundled(tasks.shadowJar) // Or `bundled(configuration)` for other packing.
    }
}
```

- `DEPENDS_ON` comes from the graph of `dependencies`.
- A bundled component is `CONTAINS` from the artifact, and never `DEPENDS_ON` from it.
- Left out: a component neither in the `dependencies` graph nor bundled, and a
  `DEPENDS_ON` edge that the bundle pass hung under a dependency package.
- A publication without `sbom { }` keeps its SBOM as it is: no new inputs, no rewrite.

Files, chosen on design grounds ([[design-over-detekt-thresholds]]):

- `SbomContent.kt` (new) — the public DSL, apart from the `internal` machinery.
- `ArtifactComponents.kt` (new) — the value the task gets, and the relationship rewrite.
- `ComponentKey.kt` (new) — the key of a component (`project:<path>`,
  `module:<g>:<n>:<v>`), alike from Gradle's graph and from an SPDX package.
- `SpdxField.kt` (new) — the SPDX JSON field names, shared with the tests.
- `PublicationSbomTask.kt` — the optional `artifactComponents` input, applied before
  the existing renaming.
- `PublicationSbom.kt` — validation, SPDX target choice (reuse the unit target when the
  configurations collapse to it), lazy key providers, warnings for ignored `sbom { }`.
- `uber-jar-module.gradle.kts` — `sbom { bundled(tasks.shadowJar) }` on `fatJar`.

## Plan

- [x] 1. Test first: `PublicationSbomIgTest` modules `thin` (like `pluginJar`), `fat`
      (like `core-jvm-compiler`'s `fatJar`, with the leak case), `uber` (like
      `uber-jar-module`); `ArtifactComponentsSpec` on hand-written JSON. Confirm red.
- [x] 2. `SbomContent.kt`.
- [x] 3. `PublicationSbomTask.kt`: input, keys, rewrite.
- [x] 4. `PublicationSbom.kt`: validation, targets, providers, warnings.
- [x] 5. `uber-jar-module.gradle.kts`.
- [x] 6. KDoc: `PublicationSbom`, `PublicationSbomTask`, `SpinePublishing`.
- [x] 7. Verify: `./gradlew :buildSrc:build detekt`; smoke tests in scratch clones —
      tool-base (`uber-jar-module`), `core-jvm-compiler` (step 3 simulated; the issue's
      POM-vs-SBOM check).
- [x] 8. `/pre-pr` reviewers.

Out of scope: the consumer follow-ups — `core-jvm-compiler` (step 3 of the issue) and,
optionally, tool-base pointing the SBOMs of its uber JARs at their POM dependencies;
hoisting the dependencies of bundled code to the root; `PROVIDED_DEPENDENCY_OF`.

## Log

- 2026-09-25 — drafted after reading the SPDX and Shadow plugin sources and the
  `core-jvm-compiler` SBOMs.
- 2026-09-25 — a Plan agent's review added: the leak rule, `flatMap` for Shadow providers,
  reuse of the unit target, tagged keys, a unit spec for the rewrite. Plan approved; the
  user asked to keep code together by design and suppress detekt's size rules instead of
  splitting files for them.
- 2026-09-25 — red confirmed with stubs: all 18 existing SBOM cases passed, 20 new failed
  on assertions. Green: `./gradlew :buildSrc:build detekt` passes, 144 tests (15 in
  `ArtifactComponentsSpec`, 23 in `PublicationSbomIgTest`). Lessons: a project from
  `root.allprojects` is a lifecycle-aware wrapper, so `task.project === module` is false —
  compare paths; top-level constants follow the local SCREAMING_CASE convention (object
  members stay camelCase); `serialVersionUID` carries `@Serial`, as in Spine libraries.
- 2026-09-25 — per review: no duplicated string literals (`SpdxField`, shared test
  snippets); `SpdxField` and `ArtifactComponents` got files of their own.
- 2026-09-25 — `core-jvm-compiler` smoke test (scratch clone of `publish-sboms`, this
  `buildSrc`). Without `sbom { }`: both SBOMs identical to #764's (180/179 packages,
  same relationships and licenses). With step 3 simulated (POM-mirror configurations):
  `pluginJar` DEPENDS_ON `core-jvm-plugins` and `kotlinpoet-ksp` only, CONTAINS `grpc`,
  `ksp`, `routing`. `fatJar` first showed 5 declared Gradle plugins as CONTAINS: Shadow is
  given their files and strips their classes by path. A third `declared` set was tried
  and reverted on review — a fat JAR either bundles a module or declares it; the fix is
  for the consumer to exclude such modules with the dependency filter (KDoc of
  `bundled(TaskProvider<ShadowJar>)` says so). With them in `pomProvidedModules`: 24 of
  25 POM dependencies are DEPENDS_ON, none CONTAINS, 26 bundled components CONTAINS,
  `verifyBundledPackages` passes. The KSP plugin marker is POM-only, so the SPDX plugin
  lists no package for it (it lists `symbol-processing-gradle-plugin`, its content).
  The plugin's spanning tree leaves some declared dependencies (Jackson) under other
  packages — as in every SBOM it writes; out of scope.
- 2026-09-25 — tool-base smoke test (scratch clone of `master` 7faea573, all of `config`'s
  `buildSrc` synced in as `migrate` does). `java-code`, `proto-code` and so
  `jvm-tool-plugins` and `protobuf-setup-plugins` fail to resolve on a
  `kotlinx-coroutines-bom` 1.11.0 vs 1.10.2 conflict — unrelated, as recorded for `logging`
  under `generate-sboms`. With `--continue`, the two `uber-jar-module` modules got SBOMs
  from the shared `publication` document: `intellij-platform` CONTAINS 40 packages, all
  of the JetBrains groups its Shadow `include` filter admits, and each of its 20 POM
  dependencies is a DEPENDS_ON target; `intellij-platform-java` CONTAINS 110 JetBrains
  packages and DEPENDS_ON its sibling `io.spine.tools:intellij-platform` only.
- 2026-09-25 — reviews: `kotlin-engineer`, `spine-code-review`, `gradle-review`,
  `review-docs` — all APPROVE WITH CHANGES, no must-fix; run in isolated worktrees, tree
  and remote verified untouched afterwards. Applied: `ComponentKey` value class (own file)
  instead of `String` keys; `describeIn` as an extension, leaving `ArtifactComponents`
  a pure value; named arguments; checked JSON accessors; a pure `map` instead of
  a mutating `onEach`; `TaskProvider<out ShadowJar>`; shared `DEPENDS_ON`/`CONTAINS`/
  `DESCRIBES`/`DOCUMENT_ID`; SCREAMING_CASE test constants; KDoc on why the Shadow task
  is realized; doc wording. Skipped: explicit `internal` on the nested `Bundle` classes
  (redundant). `:buildSrc:build detekt` green, 144 tests; `core-jvm-compiler` smoke test
  re-run with the final sources — same results.

[issue-768]: https://github.com/SpineEventEngine/config/issues/768
