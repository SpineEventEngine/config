---
slug: sbom-pom-packaging
branch: sbom-pom-packaging
owner: claude
status: in-review
started: 2026-09-28
related-memories:
  - config-build-verification
  - plugin-publications-created-late
---

## Goal

A publication that carries an SBOM declares the same `<packaging>` in its POM as it would
without the SBOM: none (`jar`) for a JAR, the extension of any other main artifact, and
`pom` for a publication without one. The SBOM keeps its published name,
`<artifactId>-<version>.spdx.json`.

Closes [#770][issue-770].

## Context

- `PublicationSbom.publishSbom` adds the SBOM to a publication without a classifier.
  Gradle 9.7.1 infers the packaging from the unclassified artifacts: the extension of
  the only one, or `pom` for several (`DefaultMavenPublication.determinePackagingFromArtifacts`).
  With the packaging `pom`, `determineMainArtifact()` finds no main artifact either.
- The SPDX Maven Plugin deploys its JSON document the same way, as type `spdx.json` with
  no classifier, so giving the SBOM a classifier would break that convention.
- `MavenPom.packaging` is a plain value in the public API; its lazy convention is internal.
  `publishSbom` runs once all projects are evaluated, when publications are final, so
  the packaging read there is that of the artifacts without the SBOM.
- `core-jvm-compiler#115` works around this with `pom.packaging = "jar"` in two
  publications. The overrides become redundant once it pulls this fix.

## Plan

- [x] Branch `sbom-pom-packaging` from `master`.
- [x] Add IG cases to `PublicationSbomIgTest`: JAR publications declare no packaging;
      a new `archive` module publishing a ZIP declares `zip`; `fat`, whose Shadow JAR is
      classified `all`, declares `pom`. See them fail on `master`.
- [x] Align the fixture's `uber` with `uber-jar-module`: its fat JAR has no classifier,
      so it is among the JAR cases.
- [x] Cover a packaging the build sets: a new `osgi` module publishes its JAR with the
      `bundle` packaging, which its POM keeps.
- [x] Keep the packaging in `publishSbom`: pin it with `pinPackaging()` before adding
      the SBOM. Document why in the KDoc.
- [x] Verify: the IG test, `./gradlew :buildSrc:build detekt`, and the scratch
      reproduction of the issue.

## Log

- 2026-09-28 — reproduced on `master` (`4c26dc55`); plan approved, executing.
- 2026-09-28 — red without the fix: four JAR POMs declare `pom`, the ZIP `pom`, and
  the classified fat JARs `spdx.json`, the SBOM taking the place of the main artifact.
  Green with it; `./gradlew :buildSrc:build detekt` passes, 160 tests. The scratch
  reproduction publishes its JAR POMs without `<packaging>` again.
- 2026-09-28 — review: reading the packaging, adding the SBOM, and setting the packaging
  back read as if `artifact(...)` changed it by itself. Replaced by `pinPackaging()`,
  called before the SBOM is added, with the mechanism in its KDoc.
- 2026-09-28 — review: the KDoc read as if `pom` were the aim for classified artifacts;
  reworded as what the POM would declare without the SBOM. The fixture's `uber` now
  publishes an unclassified fat JAR, as `uber-jar-module` does.
- 2026-09-28 — pre-PR passed at `e12bee2b`. Applied the reviewers' findings: an explicitly
  typed local in `pinPackaging()`, so a lazy `MavenPom.packaging` fails to compile there
  rather than referring to itself; `forAll` in the JAR case, naming each packaging; and
  three KDoc fixes, one scoping the component note to publications made from one.
- 2026-09-28 — PR #775. Copilot's review asked for a case of an explicitly set packaging.
  It passes without the fix, as an explicit value overrides the calculation, but it fails
  if the packaging is ever calculated anew from the artifacts, ignoring the build.

[issue-770]: https://github.com/SpineEventEngine/config/issues/770
