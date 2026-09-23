---
slug: attest-published-artifacts
branch: apply-attestation
owner: claude
status: in-progress
started: 2026-09-23
related-memories:
  - pom-report-per-project-collectors
  - config-build-verification
---

## Goal

Apply `actions/attest@v4` to the distributed `publish.yml`, so every artifact
Spine publishes to GitHub Packages carries SLSA build provenance bound to the
workflow run that produced it. Subjects are enumerated by Gradle — the only
component that authoritatively maps published coordinates to files on disk.

Closes [#602](https://github.com/SpineEventEngine/config/issues/602).

## Context

- `.github-workflows/publish.yml` is distributed to ~40 consumer repos by
  `./config/pull`, so a single edit here reaches the whole SDK.
- Publish destinations are Cloud Artifact Registry, CloudRepo and GitHub
  Packages (`PublishingRepos.kt`). There is no Maven Central publisher; the
  issue was re-scoped to GitHub Packages on 2026-09-23.
- An attestation binds a *digest* to a provenance claim. `subject-checksums`
  takes the digest and the subject name **verbatim** from a manifest file and
  never re-hashes (`actions/attest`, `src/subject.ts`). Whatever writes that
  manifest is therefore inside the trust boundary.
- Globbing `build/libs` enumerates the wrong set: it misses `.pom`/`.module`
  (which live under `build/publications/<pub>/` as `pom-default.xml` and
  `module.json`), it catches `buildSrc/build/libs/buildSrc.jar`, and the
  on-disk jar name omits the `artifactPrefix` (`client-*.jar` is published as
  `spine-client-*.jar`).

## Design decisions

1. **Per-project collector + root aggregator**, mirroring `generatePom` /
   `collectResolvedVersions`. Enforced by `pom-report-per-project-collectors`:
   a root task must not reach into other projects. Here the constraint is
   cross-project *state* access rather than configuration resolution, but the
   remedy is the same shape.
2. **Hook in `SpinePublishing.configured()`**, which already iterates
   `projectsToPublish()`. This makes the tasks appear in every consumer on the
   next `./config/pull` with no consumer-side build-file edit — unlike
   `PomGenerator.applyTo()`, which consumers must call explicitly.
3. **Names derived from the publication**, not from the filesystem:
   `"${artifactId}-${version}[-${classifier}].${extension}"`, plus `.pom` and
   `.module`. This is what a consumer of GitHub Packages actually resolves.
4. **No declared task inputs/outputs**, same rationale as the pom report: the
   tasks always run, so the manifest can never be stale relative to the jars
   that were just built.
5. **Fail loudly on a missing artifact file.** Silently skipping would
   under-attest, and an attestation's value is that its subject set is
   complete.

## Plan

- [x] 1. `buildSrc/.../gradle/publish/PublicationChecksums.kt`
      - `collectPublicationChecksums` (per project): walks
        `publishing.publications.withType<MavenPublication>()`, emits
        `<sha256>  <publishedName>` for each artifact + the `GenerateMavenPom`
        and `GenerateModuleMetadata` outputs; writes
        `build/attestation/checksums.txt`.
      - `publicationChecksums` (root): `dependsOn` the collectors, merges and
        sorts into `build/attestation/subject-checksums.txt`.
- [x] 2. Wire both into `SpinePublishing.configured()`.
- [~] 3. Unit spec for name derivation + manifest serialization — **not added**.
      The derivation helpers are private to `PublicationChecksums.kt`, and the
      integration test asserts their output both against an explicit list of
      published names and against the files actually staged. A unit spec would
      need production visibility widened purely for the test, to re-check what
      is already covered against ground truth. Flagged for review.
- [x] 4. TestKit integration test (`PublicationChecksumsIgTest`, 3 cases).
- [x] 5. `.github-workflows/publish.yml`: `permissions:` block,
      `publicationChecksums` in the publish invocation, `actions/attest@v4`
      step placed last.
- [x] 6. Verify: `./gradlew :buildSrc:test detekt` — 94 tests, 0 failures.

## Risks

- **The `permissions:` block is exhaustive.** `publish.yml` has none today, so
  `GITHUB_TOKEN` inherits repo/org defaults. Adding a block drops every scope
  not listed to `none`, and `GitHubPackages.kt:62` passes `GITHUB_TOKEN` as the
  Maven password — so `packages: write` must be present or publication breaks
  on `master` for every consumer. Planned block: `contents: read`,
  `packages: write`, `id-token: write`, `attestations: write`,
  `artifact-metadata: write`.
- `artifact-metadata` is a newer scope; confirmed documented and valid on
  github.com, but version-gated in GitHub's own docs, so unavailable on GHES.
- Attestation runs per `master` merge (`publish.yml` is `on: push`), i.e. per
  snapshot, not per release. Intended, but it is a volume change.
- Rollout is staged: consumers pick this up only on their next `./config/pull`.

## Log

- 2026-09-23 — plan drafted, approved; branch renamed to `apply-attestation`.
- 2026-09-23 — verified empirically before coding that `MavenPublication.artifacts`
  includes component-derived artifacts, and that the derived names match the
  files Gradle actually publishes (probe: 4/4 exact).
- 2026-09-23 — implemented, wired, tested. `:buildSrc:test detekt` green.
  Open: step 3 deviation (no unit spec); nothing committed yet.
