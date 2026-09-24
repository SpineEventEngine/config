---
slug: generate-sboms
branch: generate-sboms
owner: claude
status: in-progress
started: 2026-09-23
related-memories:
  - pom-report-per-project-collectors
  - config-build-verification
---

## Goal

Publish one SPDX 2.3 SBOM per published module — and, for Kotlin Multiplatform modules,
one per target publication — next to each artifact in the Maven repositories, so that
#763's provenance attestation covers it. Closes [#762][issue-762].

## Context

- Decisions (2026-09-23): SPDX via the SPDX Gradle plugin (`org.spdx.sbom` 0.12.0) applied
  from `buildSrc`; runtime dependencies only; Maven repositories only, not the Gradle Plugin
  Portal; provenance only — no SBOM-predicate attestation, no archive.
- `publish.yml` already runs `./gradlew publish publicationChecksums`, and
  `collectPublicationChecksums` hashes every artifact of every Maven publication, so an SBOM
  attached to a publication is published and attested with no workflow logic change.
- Plugin bug [spdx/spdx-gradle-plugin#192][spdx-192]: a dependency resolved from a `file:`
  repository fails the SBOM task. `standardToSpineSdk()` adds `mavenLocal()`, so the task
  extension maps `file:` repositories to `NOASSERTION`.
- The plugin names sibling modules by Gradle project name, without a purl; a post-process
  step renames them to their published coordinates.
- plugin-publish 2.1.1 skips non-jar artifacts when uploading to the Portal
  (`PublishTask.addAndHashArtifact`); older versions pinned in compiler, ProtoTap and
  Chords are unverified.

## Plan

- [x] 1. Add `org.spdx:spdx-gradle-plugin` to `buildSrc`; raise `jacksonVersion` to 2.22.0.
- [x] 2. Test first: `PublicationSbomIgTest` (JVM modules, plugin module, KMP `jvm` target;
      parent-POM license on the Plexus 4 classpath; `file:` repository; runtime-only;
      sibling renaming; umbrella and marker publications get no SBOM).
- [x] 3. Implement `PublicationSbom.kt` and hook it into `SpinePublishing.configured()`.
- [x] 4. Update the expected list in `PublicationChecksumsIgTest`.
- [x] 5. Note the SBOMs at the `publicationChecksums` step of `publish.yml`.
- [ ] 6. Verify: `./gradlew :buildSrc:test detekt`; smoke tests in `core-jvm`, `logging`
      and `elastic` (Native targets); pre-PR reviewers.

## Log

- 2026-09-23 — plan approved in Plan mode; KMP added to scope on review.
- 2026-09-23 — steps 1–5 done. `./gradlew :buildSrc:test detekt` green: 106 tests
  (98 existing + 8 in `PublicationSbomIgTest`). The parent-POM case passes, so the SPDX
  plugin's Maven 3.9 model works on `buildSrc`'s Plexus 4 classpath. Consumer smoke tests
  run in scratch clones and call the SBOM tasks directly rather than
  `publishToMavenLocal`, to leave Maven Local and the Version Guard untouched.
- 2026-09-23 — smoke test `elastic` (clone, `kmp-publish` wired locally): SBOMs for the
  `jvm` target and all six Native targets, 12 s; all 14 SBOM tasks up to date on a rerun.
  Native SBOMs list the module alone — the Native stdlib comes from the Kotlin/Native
  distribution, not Maven. Smoke test `logging`: its master does not build with the current
  `config` `buildSrc` at all (`kotlinx-coroutines-bom` 1.11.0 vs 1.10.2 conflict, reproduced
  with `config` master and no SBOM code) — needs its own `config` update, not this change.
- 2026-09-23 — `dependency-audit`: APPROVE, no findings.
- 2026-09-23 — smoke test `core-jvm-compiler` failed in SBOM tasks: `No XmlService
  implementation found`. Shadow 9.6.1 brings `plexus-xml` 4.1.1, which merges `Xpp3Dom`
  through `maven-xml` 4.0.0-rc-5; its `XmlService` is found via the thread context class
  loader, which does not see `buildSrc` on Gradle's task threads. The SPDX plugin's Maven 3.9
  model merges plugin configuration with the parent POM — which the fixture's POMs did not
  do, so the test missed it. Reproduced in `PublicationSbomIgTest` (parent and child POMs
  now configure the same plugins), then fixed by forcing `plexus-xml` 3.1.0, the standalone
  Maven 3 line; `maven-xml` leaves the classpath. 106 tests + detekt green.
- 2026-09-23 — BLOCKED on a decision. After the XmlService fix, `core-jvm-compiler` fails
  with `Could not determine effective POM` for `io.spine:spine-server:2.0.0-SNAPSHOT.551`:
  its published POM lists `io.grpc` dependencies with no `<version>` and no `grpc-bom`
  import. `core-jvm/server` declares them version-less; the versions reach Gradle only via
  `client`'s `api(platform(Grpc.bom))` in module metadata and a `force(Grpc.bom)` in its
  `module.gradle.kts`, which POMs cannot carry. Same pattern in the latest `tool-base`
  (gRPC), `compiler-api` and `spine-format` (Jackson 3). The SPDX plugin builds each
  dependency's effective POM strictly and fails the task on one it cannot build — no
  option to skip. So as it stands, SBOMs would fail `publish` in most repositories until
  these POMs are fixed and republished. Maven users cannot consume those artifacts today.
- 2026-09-23 — reviews: `dependency-audit` APPROVE (also of the `plexus-xml` pin),
  `spine-code-review` APPROVE, `kotlin-engineer` and `review-docs` APPROVE WITH CHANGES,
  `gradle-review` REQUEST CHANGES (`platform.get()` during configuration). All applied: the
  task resolves its own coordinates while it runs, from declared inputs and a plain
  fallback; a KMP target without `main` (Android) gets no SBOM instead of failing;
  strict coordinate parsing; renames (`taskNameFor`, `registerSbom`,
  `publishedCoordinates`); `mavenPublications()` moved to `PublishingExts.kt`; KDoc
  fixes. New cases: a JVM module naming its KMP sibling by the `jvm` artifact, and a
  module registered twice. 107 tests + detekt green. Still blocked on the POM decision.
- 2026-09-23 — correction to the BLOCKED entry. `compiler-api` and `spine-format` are
  fine: they import `tools.jackson:jackson-bom`, which manages `tools.jackson.*` — the scan
  matched BOM imports by group only. Only `spine-server` and `tool-base` declare `io.grpc`
  dependencies with no version and no BOM that manages them (for `spine-server` the Maven
  model builder confirmed it). Gradle consumers are unaffected: they read `.module`, where
  `spine-client`'s `grpc-bom` platform aligns the graph. "Maven users cannot consume them"
  was overstated and untested: Maven 3 is expected to warn that the POM is invalid and
  drop its transitive dependencies, not to fail.
- 2026-09-23 — re-scanned 28 current artifacts, resolving each imported BOM (and nested
  imports) instead of matching groups. Only `spine-server` 2.0.0-SNAPSHOT.551 has unmanaged
  version-less dependencies (`io.grpc`); `tool-base` .410 has them too, but that artifact is
  discontinued (`ToolBase.lib` is deprecated; 404 at .423). All current `tool-base`,
  `compiler`, `base-libraries`, `time`, `logging`, `reflect`, `change`, `testlib` artifacts
  and `core-jvm`'s `client`/`core` are fine. So the SBOM blocker is limited to modules
  whose runtime classpath includes `spine-server` (e.g. `core-jvm-compiler`), and the fix is
  `api(platform(Grpc.bom))` in `core-jvm`'s `server`, as its `client` already has.
- 2026-09-23 — correction: `client-testlib` too. That re-scan named 28 artifacts and missed
  `core-jvm`'s `*-testlib` modules and `server-otel`. The `gradle-review` of the `core-jvm`
  fix found `io.spine.tools:client-testlib` 2.0.0-SNAPSHOT.551 with the same unmanaged
  `io.grpc:grpc-protobuf` (`implementation(Grpc.protobuf)`, no BOM of its own). All seven
  `core-jvm` artifacts checked: only these two. [core-jvm#1677][cjvm-1677] fixes both
  (`implementation(platform(Grpc.bom))` in `client-testlib`), version 2.0.0-SNAPSHOT.552;
  unblock once it is published and `config`'s `CoreJvm` pin reaches it.
- 2026-09-24 — [core-jvm#1677][cjvm-1677] merged; 2.0.0-SNAPSHOT.552 is published, and
  its `spine-server` and `client-testlib` POMs pass the BOM-resolving check. `CoreJvm.version`
  in `config` is still .551. Next: move it to .552, then re-run the `core-jvm-compiler`
  smoke test.
- 2026-09-24 — `CoreJvm.version` → .552. The `core-jvm-compiler` smoke test passes (92 s):
  SPDX-2.3 SBOMs for `:gradle-plugin` (`core-jvm-gradle-plugin`) and `:compiler-plugins`
  (`core-jvm-plugins`), recording `spine-server`/`-client`/`-core` .552. Findings:
  (1) its 11 unpublished siblings, bundled into the two fat jars, keep project names with
  no purl and `NOASSERTION` license, one warning each per build — as designed, but noisy;
  (2) there the SBOM tasks re-run on every invocation: each sibling `jar` is rebuilt
  because `write-manifest`'s `Build-Timestamp` changes; ~2.5 s per SBOM task, cold;
  (3) Spine Gradle plugin POMs carry no `<licenses>` (`validation-gradle-plugin` .464,
  `time-gradle-plugin` .251), so they show as `NOASSERTION`; (4) `onlyUseLocalLicenses`
  leaves 21 licenses as `LicenseRef-gnrtd*` (e.g. MIT at `spdx.org/licenses/MIT.txt`,
  MPL-2.0, zlib), their names and URLs kept in `hasExtractedLicensingInfos`.
- 2026-09-24 — fixed (1): `PublicationSbomTask` declares the build's license for every
  module of this build, published or not, and logs a sibling without coordinates at info
  level; it keeps its project name and gets no purl. Only an unpublished (bundled) module
  gets here: Gradle cannot write the POM of a module depending on a project with several
  differently named publications. New `PublicationSbomIgTest` case with an unpublished
  `bundled` module (red on `NOASSERTION`, then green); `:buildSrc:build detekt` green,
  108 tests. `core-jvm-compiler` smoke: no sibling warnings (was 12); `NOASSERTION` down
  to 7 per SBOM — 5 Spine plugin POMs, handled in a separate session, plus
  `jcip-annotations` 1.0 and `oro` 2.0.8, whose POMs declare no license.
- 2026-09-24 — unblocked. `/pre-pr` on the uncommitted tree: `:buildSrc:build detekt` green
  (108 tests); `spine-code-review`, `kotlin-engineer`, `gradle-review`, `review-docs` and
  `dependency-audit` all APPROVE after two named-argument fixes and two wording fixes. No
  sentinel — nothing is committed; re-run `/pre-pr` on the commit. Optional nits left:
  a `"spdx.json"` constant, `MAIN_COMPILATION_NAME`, `super.mapRepoUri` with a
  case-insensitive scheme, `platform` → `targetName`, one name for presumed/fallback
  coordinates, a KDoc caveat for a custom module with two publications, "SPDX Gradle
  Plugin" capitalization, "this build" in `LicenseSettings`, a link for #763. The `logging`
  smoke test is still pending (its own `kotlinx-coroutines-bom` conflict).
- 2026-09-24 — Copilot on #764: one SBOM per module, attached to all its publications,
  named every copy after one artifact when a module had several. Now each publication gets
  its own task and file (`build/sbom/<publication>.spdx.json`), named after its own
  coordinates; `publicationSbom` runs them all; the fallback coordinates are gone. The tasks
  are registered by the root's one `projectsEvaluated` hook, when publications are final —
  so a publication a build removes (`core-jvm-compiler`'s `pluginMaven`) gets none. A unit
  records itself instead of adding a hook: Gradle forbids that in the KMP target callback.
  `twin` fixture module (two publications and a removed third); 110 tests green. Smoke:
  `core-jvm-compiler` (`fatJar`, `pluginJar`, no warnings), `elastic` (7 targets; re-run
  up to date).
  A hand-picked scan cannot clear the blocker; the consumer smoke tests can, since the
  SPDX plugin builds the effective POM of every runtime dependency.

[issue-762]: https://github.com/SpineEventEngine/config/issues/762
[spdx-192]: https://github.com/spdx/spdx-gradle-plugin/issues/192
[cjvm-1677]: https://github.com/SpineEventEngine/core-jvm/pull/1677
