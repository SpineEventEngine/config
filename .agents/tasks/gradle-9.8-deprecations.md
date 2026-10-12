---
slug: gradle-9.8-deprecations
branch: gradle-9.8-deprecations
owner: claude
status: in-review
started: 2026-10-12
related-memories:
  - gradle-10-third-party-deprecations
---

## Goal

Consumers of `config` print no Gradle 9.8.1 deprecation warning from `config`
at configuration time under `--warning-mode all`:

1. `Configuration.setVisible(boolean)` (removed in Gradle 11) — raised by the
   third-party plugins pinned in `buildSrc/build.gradle.kts`.
2. `Starting an external process at configuration time` (an error in
   Gradle 11) — raised by `gcloud`, started while `PublicationHandler`
   registers the publishing repositories.

## Context

Measured in `tool-base` (config@3409e7dd, Gradle 9.8.1) with
`./gradlew help --warning-mode all --stacktrace`.

`setVisible` callers and upstream state, checked 2026-10-12:

| Plugin | Pinned | Newest stable | Upstream fix |
|---|---|---|---|
| Dokka | 2.2.0 | 2.2.0 | [dokka#4601] merged 2026-09-11; no stable release (2.3.0-Beta only) |
| Kover | 0.9.9 | 0.9.11 | none — [kotlinx-kover#836] open; `main` still sets `isVisible` |
| Protobuf | 0.10.0 | 0.10.0 | [protobuf-gradle-plugin#816] merged 2026-09-29; unreleased |
| detekt | 1.23.8 | 1.23.8 | [detekt#9577] merged to `main` (2.0 line) 2026-08-07; no 1.23.x |

No fixed release exists, so the versions stay as they are.

The `gcloud` start: `PublicationHandler`'s `RepositoryHandler.register` calls
`Repository.credentials(rootProject)` to configure each repository. For
`CloudArtifactRegistry`, that is `DefaultCredentialProvider` falling back to
`gcloud config config-helper`. `IncrementGuard` only stores the `Repository`;
`CheckVersionIncrement` reads the public metadata without credentials.

Gradle constraints, verified with a scratch build on 9.8.1:
- `PasswordCredentials.username` is a non-optional `@Input` of
  `PublishToMavenRepository`, validated before any task action, so a
  `doFirst` is too late to supply it.
- Gradle marks explicit repository credentials as unsupported with the
  configuration cache, and degrades it for builds that publish.

## Plan

- [x] Check upstream for `setVisible` fixes in each plugin.
- [x] Obtain repository credentials in a task that each
      `PublishToMavenRepository` task of that repository depends on;
      mark it `notCompatibleWithConfigurationCache`.
- [x] Document on `Repository` when `credentialValues` is invoked.
- [x] Add an integration test: `help` does not obtain credentials, with
      the configuration cache stored and reused; publishing sends them.
- [x] Run `:buildSrc:test detekt`.
- [x] Verify in a scratch `tool-base` clone: before/after warnings.
- [x] Update the `gradle-10-third-party-deprecations` memory.

## Log

- 2026-10-12 — drafted; upstream status recorded above.
- 2026-10-12 — `PublishingCredentialsIgTest` fails on the old code (all
  three cases) and passes on the new one. `:buildSrc:test detekt`: 196 tests,
  no failures.
- 2026-10-12 — scratch `tool-base` (commit `6d4dc19b`) with the files that
  `migrate` copies from config@3409e7dd, Gradle 9.8.1,
  `./gradlew help --warning-mode all --stacktrace`. Before: 36 `setVisible`
  (Dokka 22, Kover 9, Protobuf 3, detekt 2) and 1 external process
  (`GcloudCredentials.getGcloudAccessToken`). After: the same 36
  `setVisible`, no external process. All other warnings are unchanged.
- 2026-10-12 — `gradle-review` and `kotlin-engineer` reviews applied. The
  suppliers of parallel modules may now run at once, so
  `Repository.credentials` serializes its calls; at configuration time
  they ran one after another. The GitHub Packages token is unpacked into
  the root build directory, which every supplier shares. The supplier
  joined `SpineTaskGroup`. The test publishes to two destinations, so
  each repository must get only its own credentials. Against the old
  `PublicationHandler`, only the configuration-time case fails; both
  publishing cases pass, so publishing behaves as before.
  `publish -m` in `tool-base` orders each `supply<Repo>Credentials`
  before the publishing tasks of its repository.
- 2026-10-12 — `checkCredentials` (`PublishingExts.kt`) no longer calls
  `Task.project` in `doLast`. Gradle 9.8.1 printed "Invocation of
  Task.project at execution time has been deprecated" on every `publish`.
  The project is resolved at configuration time. The task is
  `notCompatibleWithConfigurationCache` because `Repository.credentials`
  takes the `Project`. Without that marker, `publish --configuration-cache`
  fails to serialize the project. `PublishingCredentialsIgTest` runs
  `checkCredentials` with `--warning-mode all`, which prints no deprecation
  now; against the old code it prints that warning. The test also checks
  that the task fails when a destination has no credentials, and its
  publishing cases run `publish`. `:buildSrc:test detekt`: 198 tests, no
  failures.

[dokka#4601]: https://github.com/Kotlin/dokka/pull/4601
[kotlinx-kover#836]: https://github.com/Kotlin/kotlinx-kover/issues/836
[protobuf-gradle-plugin#816]: https://github.com/google/protobuf-gradle-plugin/pull/816
[detekt#9577]: https://github.com/detekt/detekt/pull/9577
