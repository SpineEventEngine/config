---
slug: test-fixtures-pom-warnings
branch: suppress-test-fixtures-pom-warnings
owner: claude
status: in-progress
started: 2026-10-16
related-memories:
  - plugin-publications-created-late
---

## Goal

No consumer repo prints the "Maven publication '…' pom metadata warnings" about
the `testFixturesApiElements` and `testFixturesRuntimeElements` variants when it
generates the POMs of a project that applies `java-test-fixtures`. The POMs and
the Gradle Module Metadata stay the same.

## Context

`java-test-fixtures` adds the test fixtures to the `java` component as two variants
with a capability of their own (`<group>:<name>-test-fixtures`). Gradle Module
Metadata (`.module`) keeps them; a POM cannot express a capability, so Gradle
leaves them out of it by design and warns about it for every POM it writes.
The warnings carry no information.

`compiler` silenced them locally in `buildSrc/src/main/kotlin/module.gradle.kts`
(`suppressTestFixturesPomWarnings()`, `SpineEventEngine/compiler#90`, commit
`52bed25961`). That file is owned by each consumer, so the shared fix belongs in
the publishing code of `buildSrc`, which `./config/pull` distributes.

## Plan

- [x] Suppress the warnings in `PublicationHandler.apply()`, so that both
      the standard and the custom publication handlers get the suppression.
      Lazily: `withPlugin("java-test-fixtures")` +
      `publications.withType<MavenPublication>().configureEach`.
- [x] TestKit regression test (`PublicationHandlerIgTest`): a standard and
      a custom (`java-gradle-plugin`) publication stay silent; a publication
      outside `spinePublishing` still warns.
- [x] `./gradlew :buildSrc:test detekt` (JDK 17).
- [x] Verify in `compiler`: no warning from `:api:`, `:backend:`, and
      `:testlib:generatePomFileForMavenJavaPublication --rerun`;
      the POMs are byte-identical.
- [x] `/pre-pr`.
- [ ] Push, hand over `gh pr create`.

## Log

- 2026-10-16 — `PublicationHandlerIgTest`: 3/3 pass; with the call disabled,
  the two "silence" cases fail and the control case passes.
- 2026-10-16 — `compiler` `master` (`3a5a776198`) with this diff applied to its
  `PublicationHandler.kt`: `:api:`, `:backend:`, and
  `:testlib:generatePomFileForMavenJavaPublication --rerun` print no warnings
  (three before); the three POMs are byte-identical to those generated before.
- 2026-10-16 — `/pre-pr`: `:buildSrc:test detekt` pass (213 tests);
  `spine-code-review` and `kotlin-engineer` approve, `review-docs` approves
  with changes, which are applied.

## Follow-ups (not in this change)

- Drop `suppressTestFixturesPomWarnings()` from `compiler`'s `module.gradle.kts`
  after its next `./config/pull`.
