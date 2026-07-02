---
slug: address-gradle-deprecations
branch: address-gradle-deprecations
owner: claude
status: in-review
started: 2026-07-02
related-memories:
  - gradle-10-third-party-deprecations
  - config-build-verification
---

## Goal

After the bump to Gradle 9.6.1 (`fbdf0158`), the build logic must run without
Gradle deprecation warnings, so consumer repos that pull `config` do not
inherit nags that will become hard failures in Gradle 10.

## Context

- Gradle 9.6 deprecated the Kotlin DSL delegated-property accessors
  (`by registering`, `by getting`, `PropertyDelegate`); the branch replaces
  them with `register` / `named` / `getByName` and a plain helper function.
- Gradle 9.6 postdates agent knowledge; deprecations were discovered
  empirically with `--warning-mode all` (and located via
  `-Dorg.gradle.deprecation.trace=true`).
- JDK 17 required (`JAVA_HOME` via `/usr/libexec/java_home -v 17`).
- Canonical verification per `config-build-verification` memory:
  `./gradlew :buildSrc:test detekt`; this task additionally ran the fuller
  `./gradlew -p buildSrc build` gate.

## Plan

- [x] Run buildSrc + root builds with `--warning-mode all`; collect warnings
- [x] Fix each deprecation (keep delegate-accessor migration consistent)
  - Kotlin DSL delegates: `jvm-module`, `kmp-module` (pre-existing edits),
    `jacoco-kotlin-jvm`, `jacoco-kmm-jvm`, `uber-jar-module`,
    `write-manifest`, `ProjectMetadata.kt`
  - Third-party nags (Detekt, Kover, Gradle Doctor): no released fixes;
    documented in the `gradle-10-third-party-deprecations` memory
- [x] Re-run builds; confirm zero deprecation warnings from our code,
      tests green
- [x] Review pass over the diff (`spine-code-review`, `kotlin-engineer`,
      `review-docs` agents); findings applied
- [x] Stage changes, show diff (no commit without authorization)

## Log

- 2026-07-02 22:20 — task file created; discovery build starting
- 2026-07-02 22:40 — inventory complete: 7 own-code sites, 3 third-party
- 2026-07-02 22:55 — own-code fixes applied; builds green, zero own nags
- 2026-07-02 23:10 — upstream research done; residuals documented in memory
- 2026-07-02 23:25 — reviewer findings applied (dead imports, KDoc, docs);
  status → in-review
