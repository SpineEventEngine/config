---
slug: publishing-reliability
branch: publishing-reliability
owner: claude
status: in-progress
started: 2026-10-21
---

## Goal

A `Publish` run on `master` fails only when publishing genuinely cannot
succeed. Duplicate runs of one commit end green instead of racing each other,
transient registry throttling is ridden out, and a version that cannot be
published fails before anything is uploaded, with a message that says why.

## Context

- 41 of 261 `Publish` runs failed between 2026-06-01 and 2026-10-21. Among
  those whose logs survive, the registry-side causes were:
  - Artifact Registry answering `400` to a PUT of a file that already exists.
    On 2026-10-21 a GitHub webhook incident started four push runs for one
    `core-jvm` commit; three lost the race this way.
  - `429 Too Many Requests` on parallel uploads (`tool-base`, 2026-10-16).
    Gradle makes only three attempts at a PUT failing with `429`/`5xx`,
    waiting 1 + 2 s, and never retries `400`.
- Seven failures came from the `Decrypt CloudRepo credentials` step, although
  CloudRepo is deprecated and nothing publishes to it.
- The uploaded `.sha256`/`.sha512` files are read by neither Gradle nor Maven;
  failures to upload them surface as a misleading "doesn't support SHA-256"
  warning.
- Every merge publishes a new immutable version (`x.y.z-SNAPSHOT.N`), so the
  published SBOM's `git+<uri>@<sha>#` source info tells which commit
  published a version.

## Plan

- [x] `publishing.yml`: job-level `concurrency` with `queue: max` on `publish`.
- [x] `publishing.yml`: drop the CloudRepo decrypt step; fix the misnamed
      Plugin Portal step.
- [x] `publishing.yml`: longer publish retries and no `.sha256`/`.sha512`
      uploads via `-D` flags on the publish step.
- [x] `PublicationState` task: `published` when this commit already published
      the version, `unpublished` when nothing is published, a failure for a
      partial publication, a collision, or a published version whose commit
      cannot be confirmed.
- [x] `RemoteFile`: timeouts and retries for the reads of the registry, also
      used by `checkVersionIncrement`.
- [x] `publishing.yml`: run `:publicationState` first; skip publishing and
      attestation on `published`.
- [x] `PublicationState` checks every remote repository each project publishes
      to, GitHub Packages included (read with `GITHUB_TOKEN`): a publication is
      complete when each repository lists its version and has each of its files;
      a gap anywhere is a partial publication. Local repositories are skipped.
- [x] Tests: `PublicationStateSpec`, `PublicationStateIgTest` (a real publish
      to a loopback registry), `RemoteFileSpec`.
- [x] Verify: `:buildSrc:build detekt`, `actionlint`, a dry run against the
      real registry, reviewers.
- [ ] After merge: watch the first `Publish` runs of consumers that pull this
      `config`; `actionlint` 1.7.12 does not know `concurrency.queue` yet
      (rhysd/actionlint#654).

## Log

- 2026-10-21 — plan approved, executing.
- 2026-10-21 — implemented; dry run against Artifact Registry with
  `core-jvm` `2.0.0-SNAPSHOT.575`: `published` for `35054d78`, a collision
  for another commit, `unpublished` for an unpublished version.
- 2026-10-21 — review findings applied (`gradle-review`, `kotlin-engineer`,
  `spine-code-review`, `review-docs`). Verified from `core-jvm` run timings
  that Artifact Registry lists a version only once Gradle uploads the
  publication's `maven-metadata.xml`, i.e. after all its files.
  `:buildSrc:build detekt` green, 255 tests.
- 2026-10-21 — extended to every remote destination. Dry run against Artifact
  Registry and GitHub Packages with `core-jvm` `spine-core`: `.576` published
  from `a67ffd4c`, a collision for another commit, `.565` partial (pruned from
  GitHub Packages), `.999` unpublished, and a clear failure without a token.
  `:buildSrc:build detekt` green, 264 tests.
- 2026-10-21 — second review round applied: publications looked up four at
  a time; the token goes only to HTTPS (or loopback) repositories named as
  GitHub Packages, and to the origin of the request only; redirects only to
  HTTP(S), never down to HTTP; GitHub's rate-limit `403` retried; a pruning
  hint when every gap is in GitHub Packages. Live dry run also read the SBOM
  through GitHub's real redirect, and saw `401` for a wrong token.
  `:buildSrc:build detekt` green, 283 tests.
- 2026-10-21 — decided to keep checking every file in every repository, also
  when a repository does not list the version: the volume (about 110 HEAD
  requests for `core-jvm`, about 450 for `tool-base`) is a fraction of what
  the publication itself uploads, and touches no public Maven repository.
  Skipping the files of a version GitHub Packages does not list would rely on
  it listing a version from the first file on, which is not verified.
