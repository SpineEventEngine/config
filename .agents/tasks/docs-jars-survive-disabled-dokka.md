---
slug: docs-jars-survive-disabled-dokka
branch: docs-jars-survive-disabled-dokka
owner: claude
status: in-progress
started: 2026-10-16
related-memories: []
---

## Goal

Make `disableDocumentationTasks()` stop the documentation *generators* only,
leaving the JARs that pack the documentation enabled, so that every artifact a
publication declares exists on disk.

## Context

- `compiler`'s `Publish` workflow fails on `master`:

  ```
  Execution failed for task ':test-env:collectPublicationChecksums'.
  > Cannot attest a missing artifact file:
    `.../test-env/build/libs/test-env-2.0.0-SNAPSHOT.073-javadoc.jar`.
  ```

  Seen on runs [38944987966][run-072] (`.072`) and [38951775822][run-073] (`.073`).

- `test-env/build.gradle.kts` calls `disableDocumentationTasks()`, which disables
  every task with `dokka` or `javadoc` in its name. `javadocJar` is such a name,
  so the JAR the publication declares is never written. `htmlDocsJar` is not, so
  the same publication ships an empty `-html-docs.jar` — the asymmetry was
  invisible until `collectPublicationChecksums` started reading every declared
  file.

- The rule this restores is the one already agreed for
  `skipDokkaWhenPublishingToMavenLocal` (see `dokka-maven-local-gate`): a skipped
  Dokka task leaves `javadocJar`/`htmlDocsJar` holding only a manifest, and the
  publication stays well-formed.

- The module publishes nothing remotely (`PublishToMavenRepository` is
  `onlyIf { false }`), so nothing read the missing file before the attestation
  manifest arrived.

## Plan

- [x] 1. `DokkaExts.kt`: disable a task only when it generates documentation —
  a `Jar` task is excluded by its type, which covers `javadocJar`,
  `htmlDocsJar`, and any documentation JAR added later.
- [x] 2. `PublicationChecksumsIgTest`: a module calling
  `disableDocumentationTasks()` is still covered by the manifest.
- [ ] 3. Pull the updated `buildSrc` into `compiler` and re-publish from
  `master`.

## Verification

- `PublicationChecksumsIgTest` — passes with the change; without it the new case
  fails with the CI error, naming `api-1.0.0-javadoc.jar`.
- `compiler` at `master` (37f30c91cf), the change applied to its `buildSrc` copy:
  `:test-env:collectPublicationChecksums` fails before and succeeds after;
  `publicationChecksums` writes all 82 subjects. The Dokka tasks of `test-env`
  stay `SKIPPED`, and its `-javadoc.jar` is 261 bytes — byte-identical to the
  `-html-docs.jar` the module already published.

## Open

- The manifest covers `test-env`, whose artifacts reach Maven Local only. The
  attestation thus names files that no registry serves. Harmless, but it
  overstates what the build published — worth a separate look.

## Log

- 2026-10-16 — diagnosed from the two failed `compiler` runs; steps 1-2 done
  and verified, step 3 waits on this change reaching `master`.
- 2026-10-16 — review findings applied: the documentation JARs are described
  as containing only a manifest, and the fixture of the new test is written
  from the same constant the test matches on.

[run-072]: https://github.com/SpineEventEngine/compiler/actions/runs/38944987966/job/117121739485
[run-073]: https://github.com/SpineEventEngine/compiler/actions/runs/38951775822/job/117144658146
