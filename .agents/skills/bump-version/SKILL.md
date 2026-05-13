---
name: bump-version
description: >
  Bump the project version in `version.gradle.kts` following the Spine SDK
  versioning policy. Use when starting a new branch, before opening a PR, or
  when CI rejects a branch for a missing/insufficient version increment. Covers
  locating the published version value, choosing the increment, committing the
  bump, rebuilding reports, and resolving version conflicts.
---

# Bump the project version

The authoritative policy is [Spine SDK Versioning][version-policy]. In this
repo, CI runs `io.spine.gradle.publish.CheckVersionIncrement` through
`IncrementGuard`; it fails if the current project version already exists in the
Maven repository. It does not compare git branches or inspect commit subjects.

## Checklist

1. Locate `version.gradle.kts` and update the value that feeds
   `versionToPublish`.

   The published version may be a literal:

   ```kotlin
   val versionToPublish: String by extra("2.0.0-SNAPSHOT.182")
   ```

   Or it may come from another variable:

   ```kotlin
   val compilerVersion: String by extra("2.0.0-SNAPSHOT.043")
   val versionToPublish by extra(compilerVersion)
   ```

   In the second case, update the source value (`compilerVersion` here), not
   only the `versionToPublish` alias.

2. Choose the increment.

   For the normal snapshot-line PR, increment the trailing snapshot number by
   one: `2.0.0-SNAPSHOT.182` -> `2.0.0-SNAPSHOT.183`. Preserve existing
   zero-padding: `2.0.0-SNAPSHOT.009` -> `2.0.0-SNAPSHOT.010`.

   For a breaking snapshot-line PR, advance to the next multiple of 10 that is
   strictly greater than the current value: `.187` -> `.190`, and `.180` ->
   `.190`.

   For release-line work, follow the [policy][version-policy]: urgent fixes bump `PATCH`;
   feature work or significant fixes bump `MINOR` and reset `PATCH` to `0`.

3. Commit only the `version.gradle.kts` change with this subject:

   ```text
   Bump version -> `2.0.0-SNAPSHOT.183`
   ```

4. Run the build to verify the bump and regenerate reports:

   ```bash
   ./gradlew clean build
   ```

   Repos using this config commonly finalize `generatePom` and
   `mergeAllLicenseReports` after `build`, which updates
   `docs/dependencies/pom.xml` and `docs/dependencies/dependencies.md` when
   those reports are configured.

5. If `docs/dependencies/pom.xml`, `docs/dependencies/dependencies.md`, or
   `docs/dependencies/license-report.md` changed, commit those generated files separately:

   ```text
   Update dependency reports
   ```

   If the PR has the "License Reports" workflow, make sure the branch modifies
   `docs/dependencies/pom.xml` and either `docs/dependencies/dependencies.md`
   or `docs/dependencies/license-report.md`.

6. Validate the branch state.

   ```bash
   BASE=master
   git fetch --quiet origin "$BASE"
   RANGE="$(git merge-base HEAD origin/$BASE)..HEAD"
   git log --format=%s "$RANGE" | grep '^Bump version ->'
   git diff --name-only "$RANGE" -- version.gradle.kts | grep '^version.gradle.kts$'
   ```

   Use the actual merge target for `BASE` when it is not `master`.

## Conflict Rule

When merging a base branch into a feature branch:

- If the base branch version is lower, keep the feature branch version.
- If the base branch version is greater than or equal to the feature branch
  version, set the feature branch version to `base + 1`, or apply the breaking
  change rounding rule.

Do not require a completely clean worktree if unrelated user changes are
present. Instead, make sure no uncommitted changes were created by the version
bump or report regeneration.

[version-policy]: https://github.com/SpineEventEngine/documentation/wiki/Versioning
