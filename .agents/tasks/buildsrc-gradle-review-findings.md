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

- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/testing/Tasks.kt:82, 96`
      — `FastTest` / `SlowTest`: replace `group = "Verification"`
      with `group = SpineTaskGroup.name`.
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/dart/task/DartTasks.kt:111-114`
      — drop the `Group` object (`"Dart/Build"`, `"Dart/Publish"`).
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/javascript/task/JsTasks.kt:111-117`
      — drop the `Group` object (`"JavaScript/Assemble"`,
      `"JavaScript/Check"`, `"JavaScript/Clean"`, `"JavaScript/Build"`,
      `"JavaScript/Publish"`).
- [ ] Update every `Group.*` consumer to set
      `group = SpineTaskGroup.name` instead:
      `Webpack.kt:104`, `Check.kt:100, 129, 164, 189`,
      `Assemble.kt:108, 133, 161, 188`, `Publish.kt:93, 116, 156, 187`,
      `Clean.kt:90, 117`, `IntegrationTest.kt:90, 121`,
      `LicenseReport.kt:80`, `dart/task/Build.kt:101, 128, 150`,
      `dart/task/Publish.kt:95, 131, 163`.

**A.2 — Tasks that set neither `group` nor `description`.**
For each, add `group = SpineTaskGroup.name` and an imperative
`description` (no trailing period):

- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/javadoc/ExcludeInternalDoclet.kt:94`
      (`tasks.register(taskName, Javadoc::class.java)`).
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/publish/IncrementGuard.kt:58`
      (`tasks.register(taskName, CheckVersionIncrement::class.java)`).
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/github/pages/UpdateGitHubPages.kt:121, 149, 165, 183`
      (`updateGitHubPages`, `copyJavadocDocs`, `copyHtmlDocs`,
      `updatePagesTask`).
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/report/coverage/JacocoConfig.kt:165, 196`
      (`jacocoRootReport`, `copyReports`).
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/report/license/LicenseReporter.kt:111`
      (`mergeAllLicenseReports`).
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/report/pom/PomGenerator.kt:85`
      (`generatePom`).
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/publish/PublishingExts.kt:235, 249, 261, 273`
      (`sourcesJar`, `protoJar`, `testJar`, `javadocJar` via
      `getOrCreate`).
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/ConfigTester.kt:100, 121, 136`
      (three registrations).
- [ ] `buildSrc/src/main/kotlin/write-manifest.gradle.kts:106`
      (`exposeManifestForTests`).
- [ ] `buildSrc/src/main/kotlin/config-tester.gradle.kts:53`
      (the script's local `clean`).
- [ ] `buildSrc/src/main/kotlin/jvm-module.gradle.kts:152`
      (`cleanGenerated`).

### B. `Provider.get()` outside a task action (Must fix)

Each of these forces evaluation during the configuration phase,
breaking the configuration cache and serialising work Gradle would
otherwise run in parallel.

- [ ] `buildSrc/src/main/kotlin/jacoco-kmm-jvm.gradle.kts:58-72` —
      rewrite the `tasks.getting(JacocoReport::class) { ... }` block
      to `tasks.named<JacocoReport>("jacocoTestReport") { ... }`,
      remove the `project.layout.buildDirectory.get().asFile.absolutePath`
      call, and replace the eager `walkBottomUp().toSet()` with
      a lazy `DirectoryProperty`/`Provider<FileTree>` chain. Note
      that the current code silently produces an empty set on a
      clean build because `build/classes/kotlin/jvm/` does not yet
      exist — that correctness bug goes away with the lazy form.
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
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/report/coverage/JacocoConfig.kt:98-99`
      — same pattern with the `reportsDirSuffix` directory.
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
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/report/coverage/CodebaseFilter.kt:65`
      — `it.classesDirs.files.stream()`: switch to
      `it.classesDirs.elements` and a lazy stream/iterator.

### D. Plugin task classes — caching annotations (Should fix)

None of the three custom `DefaultTask` subclasses are annotated.
Document the contract:

- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/RunGradle.kt:48`
      — add
      `@DisableCachingByDefault(because = "Runs an external Gradle build whose outputs are not tracked")`.
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/publish/CheckVersionIncrement.kt:45`
      — add
      `@DisableCachingByDefault(because = "Performs network I/O against a Maven repository")`.
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
- [ ] `buildSrc/src/main/kotlin/jacoco-kmm-jvm.gradle.kts:58` —
      `tasks.getting(JacocoReport::class)` →
      `tasks.named<JacocoReport>("jacocoTestReport") { ... }` (folded
      into B above).
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
- [ ] `buildSrc/src/main/kotlin/io/spine/gradle/report/coverage/JacocoConfig.kt:196-203`
      — drop the trailing `dependsOn(projects.map { ... })` once
      `everyExecData` is verified to be a Provider-typed chain that
      carries producer dependencies.
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
      `CheckVersionIncrement.kt:60-115` (`project.artifactPath()` and
      friends), `PomGenerator.kt:85-93`,
      `LicenseReporter.kt:120-122`. Capture the necessary values or
      `Provider`s during configuration; pass them in via task
      properties.
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
