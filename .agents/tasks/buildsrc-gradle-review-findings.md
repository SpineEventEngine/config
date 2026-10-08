---
slug: buildsrc-gradle-review-findings
branch: gradle-review-skill
owner: claude
status: draft
started: 2026-05-29
related-memories: []
---

## Goal

Apply the findings of the `/gradle-review` run against all sources
under `buildSrc/` in `config` (2026-05-29). The review found three
categories of issues: Spine mandate violations (`group = "spine"` /
`description`), Gradle correctness issues (`Provider.get()` at
configuration time, eager `Configuration`/`FileCollection` APIs that
discard task wiring), and a layer of Should-fix items around
cacheability annotations, `@PathSensitivity`, and lazy task
realisation. The work is large enough that it ships as a separate PR
from the `gradle-review-skill` branch.

## Context

- Review transcript: ran `/gradle-review` on the full `buildSrc/`
  tree on 2026-05-29 in the `gradle-review-skill` branch. Verdict:
  `REQUEST CHANGES`.
- Authoritative rules:
  - [`.agents/skills/gradle-review/spine-task-conventions.md`](../skills/gradle-review/spine-task-conventions.md).
  - [`.agents/skills/gradle-review/practices/tasks.md`](../skills/gradle-review/practices/tasks.md)
    (ingested from the Gradle "Best practices for tasks" page).
- `SpineTaskGroup` constant already exists at
  `buildSrc/src/main/kotlin/io/spine/gradle/SpineTaskGroup.kt` and is
  the recommended replacement for the bare string `"spine"` (see
  [`.agents/tasks/spine-task-group-constant.md`](spine-task-group-constant.md)).
- Pre-flight ripgrep confirmed: **0 hits** for `tasks.create(`,
  `@CacheableTask`, `@DisableCachingByDefault`, `@UntrackedTask`,
  `@PathSensitivity`, and the various `@Input*` / `@Output*`
  annotations across `buildSrc/src/main/kotlin/**`.

## Plan

### A. Spine mandate — `group = SpineTaskGroup.name` (Must fix)

The string `"spine"` does not appear as a task group anywhere in
`buildSrc/`. Two sub-patterns to fix:

**A.1 — Tasks that set `group` to a non-Spine value.**

- [x] `buildSrc/src/main/kotlin/io/spine/gradle/testing/Tasks.kt:82, 96`
      — `FastTest` / `SlowTest`: replace `group = "Verification"`
      with `group = SpineTaskGroup.name`.
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/dart/task/DartTasks.kt:111-114`
      — drop the `Group` object (`"Dart/Build"`, `"Dart/Publish"`).
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/javascript/task/JsTasks.kt:111-117`
      — drop the `Group` object (`"JavaScript/Assemble"`,
      `"JavaScript/Check"`, `"JavaScript/Clean"`, `"JavaScript/Build"`,
      `"JavaScript/Publish"`).
- [x] Update every `Group.*` consumer to set
      `group = SpineTaskGroup.name` instead:
      `Webpack.kt:104`, `Check.kt:100, 129, 164, 189`,
      `Assemble.kt:108, 133, 161, 188`, `Publish.kt:93, 116, 156, 187`,
      `Clean.kt:90, 117`, `IntegrationTest.kt:90, 121`,
      `LicenseReport.kt:80`, `dart/task/Build.kt:101, 128, 150`,
      `dart/task/Publish.kt:95, 131, 163`.

**A.2 — Tasks that set neither `group` nor `description`.**
For each, add `group = SpineTaskGroup.name` and an imperative
`description` (no trailing period):

- [x] `buildSrc/src/main/kotlin/io/spine/gradle/javadoc/ExcludeInternalDoclet.kt:94`
      (`tasks.register(taskName, Javadoc::class.java)`).
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/publish/IncrementGuard.kt:58`
      (`tasks.register(taskName, CheckVersionIncrement::class.java)`).
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/github/pages/UpdateGitHubPages.kt:121, 149, 165, 183`
      (`updateGitHubPages`, `copyJavadocDocs`, `copyHtmlDocs`,
      `updatePagesTask`).
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/report/coverage/JacocoConfig.kt:165, 196`
      (`jacocoRootReport`, `copyReports`). **Superseded by Kover-only
      migration**: this file is deprecated; do not invest in micro-rewrites.
      See `.agents/skills/raise-coverage/references/migrate-to-kover.md`.
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/report/license/LicenseReporter.kt:111`
      (`mergeAllLicenseReports`).
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/report/pom/PomGenerator.kt:85`
      (`generatePom`).
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/publish/PublishingExts.kt:235, 249, 261, 273`
      (`sourcesJar`, `protoJar`, `testJar`, `javadocJar` via
      `getOrCreate`).
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/ConfigTester.kt:100, 121, 136`
      (three registrations).
- [x] `buildSrc/src/main/kotlin/write-manifest.gradle.kts:106`
      (`exposeManifestForTests`).
- [x] `buildSrc/src/main/kotlin/config-tester.gradle.kts:53`
      (the script's local `clean`).
- [x] `buildSrc/src/main/kotlin/jvm-module.gradle.kts:152`
      (`cleanGenerated`).

**A.3 — Additional Spine-owned registrations uncovered during
review of Section A.** Not listed in the original `/gradle-review`
report but covered by the same mandate. All addressed in the same PR.

- [x] `buildSrc/src/main/kotlin/DokkaExts.kt:206`
      (`htmlDocsJar` via `getOrCreate`).
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/dart/task/IntegrationTest.kt:76`
      (`DartTasks.integrationTest`).
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/publish/PublishingExts.kt:157`
      (`getOrCreatePublishTask` — the root-aggregator `publish` task
      created when the `maven-publish` plugin is absent).
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/publish/PublishingExts.kt:186`
      (`registerCheckCredentialsTask` — both the `register` and the
      `replace` code paths).

### B. `Provider.get()` outside a task action (Must fix)

Each of these forces evaluation during the configuration phase,
breaking the configuration cache and serialising work Gradle would
otherwise run in parallel.

- [x] `buildSrc/src/main/kotlin/jacoco-kmm-jvm.gradle.kts:58-72` —
      rewrite the `tasks.getting(JacocoReport::class) { ... }` block
      to `tasks.named<JacocoReport>("jacocoTestReport") { ... }`,
      remove the `project.layout.buildDirectory.get().asFile.absolutePath`
      call, and replace the eager `walkBottomUp().toSet()` with
      a lazy `DirectoryProperty`/`Provider<FileTree>` chain. Note
      that the current code silently produces an empty set on a
      clean build because `build/classes/kotlin/jvm/` does not yet
      exist — that correctness bug goes away with the lazy form.
      **Superseded by Kover-only migration**: this file is deprecated;
      do not invest in micro-rewrites. See
      `.agents/skills/raise-coverage/references/migrate-to-kover.md`.
- [ ] `buildSrc/src/main/kotlin/DokkaExts.kt:62` — change
      `dokkaHtmlOutput(): File` to return `Provider<Directory>` (or
      a `DirectoryProperty`); update its two call sites.
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/ProjectExtensions.kt:105-106`
      — `val Project.buildDirectory: File`: same pattern. Either
      delete the helper (it just shells out to
      `layout.buildDirectory.get().asFile`) or change it to return
      `Provider<Directory>`. Audit callers before deciding.
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/report/license/LicenseReporter.kt:84`
      — `project.layout.buildDirectory.dir(Paths.relativePath).get().asFile`:
      compose with `map` and consume inside the action.
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/report/coverage/JacocoConfig.kt:98-99`
      — same pattern with the `reportsDirSuffix` directory.
      **Superseded by Kover-only migration**: this file is deprecated;
      do not invest in micro-rewrites. See
      `.agents/skills/raise-coverage/references/migrate-to-kover.md`.
- [ ] `buildSrc/src/main/kotlin/DependencyResolution.kt:146` —
      `named(configurationName).get().exclude(...)`: rewrite as
      `named(configurationName) { exclude(...) }`.

### C. Eager `FileCollection` / `Configuration` APIs (Must fix)

These resolve configurations during configuration *and* discard
implicit task-dependency wiring — the wrong-outputs failure mode the
upstream rule warns about.

- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/javadoc/ExcludeInternalDoclet.kt:109`
      — `docletpath = excludeInternalDoclet.files.toList()`: route
      via a `Provider<List<File>>` from `excludeInternalDoclet.elements`,
      resolved inside the `Javadoc` task action.
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/report/coverage/CodebaseFilter.kt:65`
      — `it.classesDirs.files.stream()`: switch to
      `it.classesDirs.elements` and a lazy stream/iterator.
      **Superseded by Kover-only migration**: this file is deprecated;
      do not invest in micro-rewrites. See
      `.agents/skills/raise-coverage/references/migrate-to-kover.md`.

### D. Plugin task classes — caching annotations (Should fix)

None of the three custom `DefaultTask` subclasses are annotated.
Document the contract:

- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/RunGradle.kt:48`
      — add
      `@DisableCachingByDefault(because = "Runs an external Gradle build whose outputs are not tracked")`.
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/publish/CheckVersionIncrement.kt:45`
      — add
      `@DisableCachingByDefault(because = "Performs network I/O against a Maven repository")`.
      Landed as `because = "Queries a remote Maven repository and produces no outputs."`.
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/docs/UpdatePluginVersion.kt:56`
      — add
      `@DisableCachingByDefault(because = "Rewrites build scripts in place without declared outputs")`.

### E. `@PathSensitivity` (Should fix)

- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/docs/UpdatePluginVersion.kt:58-59`
      — add `@get:PathSensitive(PathSensitivity.RELATIVE)` on the
      `directory` `DirectoryProperty`. The task only cares about
      file names matching `build.gradle.kts` within the tree.

### F. Eager realisation in convention code (Should fix)

Replace eager APIs with their lazy siblings where one exists:

- [ ] `buildSrc/src/main/kotlin/uber-jar-module.gradle.kts:73` —
      `tasks.getting` → `tasks.named<Task>("publishFatJarPublicationToMavenLocal") { ... }`.
- [x] `buildSrc/src/main/kotlin/jacoco-kmm-jvm.gradle.kts:58` —
      `tasks.getting(JacocoReport::class)` →
      `tasks.named<JacocoReport>("jacocoTestReport") { ... }` (folded
      into B above).
      **Superseded by Kover-only migration**: this file is deprecated;
      do not invest in micro-rewrites. See
      `.agents/skills/raise-coverage/references/migrate-to-kover.md`.
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/publish/IncrementGuard.kt:60`
      — `tasks.getByName("check").dependsOn(this)` →
      `tasks.named("check") { dependsOn(this@register) }`.
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/report/pom/PomGenerator.kt:95, 99`
      — `tasks.findByName("assemble")!!` /
      `tasks.findByName("build")!!` →
      `tasks.named("assemble") { ... }` / `tasks.named("build") { ... }`.
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/javadoc/JavadocConfig.kt:41, 46`
      — `getByName(named) as Javadoc` → `tasks.named<Javadoc>(named)`;
      ripple through callers (`ExcludeInternalDoclet.kt:93`,
      `JavadocConfig.kt:73`).
- [ ] `buildSrc/src/main/kotlin/dokka-setup.gradle.kts:51` — replace
      `val kspKotlin = tasks.findByName("kspKotlin")` inside
      `afterEvaluate { ... }` with a
      `tasks.matching { it.name == "kspKotlin" }.configureEach { ... }`
      block, or rely on `tasks.named("kspKotlin").orNull`.

### G. `dependsOn` where input/output wiring expresses the link (Should fix)

- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/publish/PublishingExts.kt:273-278`
      — `javadocJar()`: replace
      `from(layout.buildDirectory.dir("dokka/javadoc"))` +
      `dependsOn("dokkaGeneratePublicationJavadoc")` with
      `from(tasks.named("dokkaGeneratePublicationJavadoc").map { it.outputs.files })`.
- [ ] `buildSrc/src/main/kotlin/DokkaExts.kt:206-213` —
      `htmlDocsJar()`: same pattern; wire
      `from(tasks.dokkaHtmlTask().map { it.outputs.files })` and
      remove the explicit `dependsOn(dokkaTask)`.
- [x] `buildSrc/src/main/kotlin/io/spine/gradle/report/coverage/JacocoConfig.kt:196-203`
      — drop the trailing `dependsOn(projects.map { ... })` once
      `everyExecData` is verified to be a Provider-typed chain that
      carries producer dependencies.
      **Superseded by Kover-only migration**: this file is deprecated;
      do not invest in micro-rewrites. See
      `.agents/skills/raise-coverage/references/migrate-to-kover.md`.
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/report/license/LicenseReporter.kt:117-118, 123`
      — the explicit `consolidationTask.dependsOn(perProjectTask)`
      and `perProjectTask.dependsOn(assembleTask)` should be
      expressed via the merge task's `@InputFiles` on the per-project
      report files. Refactor `mergeReports` to take a
      `ListProperty<RegularFile>` and let Gradle infer ordering.

### H. Nits

- [ ] **Trailing period in `description`** — strip from every Dart/JS
      task helper (`Webpack.kt:103`, `Check.kt:99, 128, 163, 188`,
      `Assemble.kt:107, 132, 160, 187`, `Publish.kt:92, 115, 155, 186`,
      `Clean.kt:89, 116`, `IntegrationTest.kt:88, 120`,
      `LicenseReport.kt:79`, `dart/task/Build.kt:100, 127, 149`,
      `dart/task/Publish.kt:94, 130, 162`) and from
      `testing/Tasks.kt:81, 95`.
- [ ] **KDoc back-link.** Add a KDoc link to the relevant rule from
      [`.agents/skills/gradle-review/practices/tasks.md`](../skills/gradle-review/practices/tasks.md)
      (or the upstream Gradle page) on each of `RunGradle.kt`,
      `CheckVersionIncrement.kt`, `UpdatePluginVersion.kt`.
- [ ] **`project` access inside task actions** — `RunGradle.kt:142-180`
      (`project.rootDir`, `project.gradle.taskGraph.hasTask(":clean")`,
      `project.file(directory)`, `project.rootProject`),
      `PomGenerator.kt:85-93`,
      `LicenseReporter.kt:120-122`. Capture the necessary values or
      `Provider`s during configuration; pass them in via task
      properties.
  - [x] `CheckVersionIncrement.kt` — `project.rootDir` and
        `project.artifactPath()` became the `rootDir` and `artifactPaths`
        task properties, set by `IncrementGuard`.
  - [x] `IncrementGuard.kt` — the `onlyIf` spec of `checkVersionIncrement`
        scanned `project.gradle.taskGraph` at execution time. A
        `taskGraph.whenReady` hook now does the scan and sets the
        `publishesToMavenLocal` task property, which the spec reads.
  - [x] `dokka-setup.gradle.kts:26-30` — the `onlyIf` spec of every
        `DokkaBaseTask` called `Task.isInPublishingGraph()` (`DokkaExts.kt`),
        which read `project.gradle.taskGraph` at execution time.
        `Project.runOnlyInPublishingGraph(tasks)` now scans the graph in a
        `taskGraph.whenReady` hook and keeps the result in a
        `Property<Boolean>`, which the spec reads.
        `Task.isInPublishingGraph()` is deprecated.
- [x] **Wrong artifact path for tool modules in `checkVersionIncrement`**
      — `Project.artifactPrefix()` in `IncrementGuard.kt` ignored
      `SpinePublishing.toolArtifactPrefix` and custom publication
      `artifactId`s. For `io.spine.tools` modules, the metadata lookup got
      a 404, so `checkNotPublished` passed silently. Example: core-jvm-compiler
      `:compiler-plugins` checked `spine-compiler-plugins`, but publishes
      `core-jvm-plugins`. Now `IncrementGuard` takes the `artifactPaths`
      from the coordinates of the Maven publications of the module,
      plugin markers included. `checkNotPublished` checks each of them, and
      warns when no repository has the metadata of an artifact.
- [ ] **`@Internal lateinit var directory: String` in `RunGradle.kt:60-62`**
      — should be a `DirectoryProperty` (or at least a
      `Property<String>`) so the task can participate in
      configuration-cache serialisation.

### Verification

- [ ] Run `./gradlew clean build` against `config` and confirm it
      passes without configuration-cache warnings.
- [ ] Re-run `/gradle-review` against `buildSrc/` and confirm
      `APPROVE` (or `APPROVE WITH CHANGES` for residual Nits).
- [ ] Smoke-test downstream consumers (`base`, `base-types`,
      `core-java`) via the `buildDependants` task in
      `config-tester.gradle.kts`.

## Decisions

- **Scope.** All three findings categories (Must fix, Should fix,
  Nits) are in scope. The volume justifies a dedicated PR.
- **Sequencing.** Sections A and B-C are independent and can be done
  in either order. Section G depends on the lazy-Provider rewrites
  in B for `DokkaExts.kt`. Run the verification step after every
  section to keep the PR bisectable.
- **Out of scope.** `io.spine.dependency.*` files (owned by the
  `dependency-audit` skill) and `gradlew` / `gradlew.bat` are
  excluded from this task.

## Log

- 2026-05-29 — drafted from the `/gradle-review` run on the full
  `buildSrc/` tree. Branch `gradle-review-skill` carries the
  document; execution lands in a separate PR.
- 2026-05-29 — Section A applied on branch
  `address-gradle-review-01`. Five review rounds against the diff
  surfaced four additional Spine-owned task registrations that the
  original report missed (`htmlDocsJar`, dart `integrationTest`, the
  root-aggregator `publish` task, and `checkCredentials`); all four
  added to Section A.3 and addressed in the same PR. Sections B–H
  remain pending.
- 2026-10-08 — `CheckVersionIncrement` no longer calls `Task.project`
  from its action (branch `version-increment-task-inputs`). Gradle 9
  deprecated that call, so every consumer's `checkVersionIncrement` run
  warned. Verified in `money` on Gradle 9.8.1: the warning is gone, and
  the failure messages match the old ones.
- 2026-10-08 — the `onlyIf` spec of `checkVersionIncrement` no longer
  calls `Task.project` either (branch `checkversion-config-cache`); the
  configuration cache reported that call. `IncrementGuardIgTest` runs the
  task with the configuration cache, storing and then reusing the entry.
  Verified in `core-jvm-compiler` with a pull-request environment: the
  problem is gone, and the task still runs. The remaining problem there —
  `gcloud config config-helper` started at configuration time — is not
  this task's: `PublicationHandler.registerDestinations` reads
  `Repository.credentials`, which calls
  `CloudArtifactRegistry.fetchGoogleCredentials`.
- 2026-10-08 — the `onlyIf` spec that `dokka-setup` adds to Dokka tasks
  no longer calls `Task.project` (branch `dokka-config-cache`).
  `DokkaSetupIgTest` gates a probe `DokkaBaseTask` with the configuration
  cache, storing and then reusing the entry; against the old spec, every
  case fails on that `Task.project` call. Each `dokkaGenerate*` task of
  Dokka 2.x matches the predicate by its own name, so the spec never
  skips a real Dokka task; the semantics are kept as they were.
- 2026-10-08 — `checkVersionIncrement` checks the artifact paths of the
  Maven publications of a module, rather than a path computed from its
  name. Verified with `GITHUB_EVENT_NAME=pull_request GITHUB_BASE_REF=master`
  against the published versions. In core-jvm-compiler (`2.0.0-SNAPSHOT.094`),
  `:compiler-plugins` and `:gradle-plugin` passed before and fail with
  "already published" now. In `logging` (`2.0.0-SNAPSHOT.425`), `:logging`
  and `:logging-testlib` passed before, and fail now. Modules that publish
  nothing skip the lookup. With the configuration cache on, the provider
  of `artifactPaths` stores and reuses cleanly. `CheckVersionIncrementIgTest`
  runs the task in a real build and checks its output, warnings included.
