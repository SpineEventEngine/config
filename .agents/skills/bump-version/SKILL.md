---
name: bump-version
description: >
  Bump the project version in `version.gradle.kts` following the Spine SDK
  versioning policy. Use when starting a new branch, before opening a PR, or
  when CI rejects a branch for a missing/insufficient version increment. Covers
  version-number selection, the commit-message convention, the post-bump
  rebuild, dependency-report updates, and conflict resolution against the base
  branch.
---

# Bump the project version

The authoritative versioning policy is
[Spine SDK Versioning][wiki-versioning]. This skill encodes the parts of that
policy that affect day-to-day work in this repo. CI enforces the bump via
`io.spine.gradle.publish.CheckVersionIncrement` (applied through
`IncrementGuard`): it fetches the project's Maven metadata and fails if the
current version already exists in the repository. It does **not** diff
against `master` or any git branch — the gate is "this version must not
already be published" — and it does not inspect the commit subject.

## Version format

Versions live in `version.gradle.kts` at the project root, for example:

```kotlin
val versionToPublish: String by extra("2.0.0-SNAPSHOT.182")
```

Spine uses Semantic Versioning with extensions:

- **Snapshot:** `MAJOR.MINOR.PATCH-SNAPSHOT.NUMBER` during active development.
  The trailing `NUMBER` is the per-PR counter.
- **Release:** standard semver, such as `1.5.0`.
- **Patch release:** issued only when an urgent fix is needed against a
  released minor; bumps the `PATCH` digit (e.g. `1.5.0` → `1.5.1`).
- **Flavor:** suffixes like `-jdk8` denote a specific build flavor.

Preserve zero-padding on the snapshot `NUMBER` when it is present:
`2.0.0-SNAPSHOT.009` → `2.0.0-SNAPSHOT.010`.

## When to bump

Every code advancement must move the version forward. Which component moves
depends on the release line:

- **Snapshot line (active development):** bump the snapshot `NUMBER` on every
  PR. This is the normal day-to-day case.
- **Released minor needing an urgent fix:** bump the `PATCH` digit and cut a
  patch release.
- **Feature or significant bug fix on a released line:** bump the `MINOR`
  digit.

Also bump when starting a new branch so the branch starts ahead of `master`.
PRs without a version bump fail CI.

## How to bump

1. Increment the version in `version.gradle.kts`. Pick the component that
   matches the release line (see "When to bump"):
   - **Snapshot PR (default):** `+1` on the snapshot `NUMBER`
     (e.g. `2.0.0-SNAPSHOT.182` → `2.0.0-SNAPSHOT.183`). Preserve zero-padding
     (see "Version format").
   - **Snapshot PR with a breaking change:** advance `NUMBER` to the next
     multiple of 10 **strictly greater than** the current value (`.187` →
     `.190`, and also `.180` → `.190` — never stay at the same value).
     Rounding signals "this PR is significant" to downstream readers.
   - **Feature/significant fix on a released line:** `+1` on `MINOR`, reset
     `PATCH` to `0` (e.g. `1.5.3` → `1.6.0`).
   - **Urgent fix on a released minor:** `+1` on `PATCH`
     (e.g. `1.5.0` → `1.5.1`).
2. Commit the version bump as a **separate commit**, with this subject (ASCII
   arrow `->`, backticked version):

   ```text
   Bump version -> `2.0.0-SNAPSHOT.183`
   ```

   When the commit bumps a named dependency rather than the current project's
   own version, replace `version` with the dependency's display name — the
   Kotlin `object` name from `buildSrc/src/main/kotlin/io/spine/dependency/local/*.kt`
   (`Time`, `Validation`, `Logging`, ...). Compound names use the spaced form
   seen in recent commits (e.g. `CoreJvm Compiler`, `Shadow plugin`). When in
   doubt, check the prevailing form: `git log --format=%s --grep='^Bump '`.

   ```text
   Bump Time -> `2.0.0-SNAPSHOT.238`
   ```

   The arrow style and backticks are the same.

   When a single commit bumps **multiple dependencies**, the `-> \`<version>\``
   tail is omitted because no single version applies. Use one of:

   ```text
   Bump Time and Validation
   Bump local dependencies
   ```

   Name the dependencies when there are two or three; use the canonical
   generic form `Bump local dependencies` (the prevailing form in repo
   history) when more are touched. Confirm against current usage with
   `git log --format=%s --grep='^Bump '` before inventing a new variant.
   Prefer one bump-per-dependency when feasible so the arrow form (and its
   CI-relevant version) is preserved.

3. Rebuild to verify the bump and regenerate dependency reports:

   ```bash
   ./gradlew clean build
   ```

   `build` finalizes `generatePom` and `mergeAllLicenseReports`, so `pom.xml`
   and `dependencies.md` are regenerated automatically when they are produced
   by this repo's build.

4. If `./gradlew clean build` produced changes to `pom.xml` and/or
   `dependencies.md`, stage **whichever of the two changed** and commit them
   as a second commit:

   ```text
   Update dependency reports
   ```

   If neither file changed, skip this step — do not create an empty commit.

## Resolving conflicts in `version.gradle.kts`

Use these roles to disambiguate during a merge:

- **base branch:** the branch being merged in (typically `master`).
- **feature branch:** the branch currently checked out (the one with the open
  PR).

Resolve as follows:

- If the base branch's number is **less than** the feature branch's, keep the
  feature branch's version unchanged.
- If the base branch's number is **greater than or equal to** the feature
  branch's, set the feature branch's number to `base + 1` (or apply the
  rounding rule from "How to bump" for a breaking change).

## Validate

- `./gradlew clean build` succeeds after the bump.
- The bump commit's subject matches the convention. Locate it without assuming
  it is `HEAD` (a "Update dependency reports" commit may sit on top), and
  scope to commits introduced on the current branch. Set `BASE` to the
  branch this PR will merge into — typically `master`, but a release line
  such as `1.5.x` for a patch release:

  ```bash
  BASE=master
  git fetch --quiet origin "$BASE"
  git log --format=%s "$(git merge-base HEAD origin/$BASE)..HEAD" | grep '^Bump '
  ```

  The `git fetch` step guards against false negatives in fresh clones,
  worktrees, or CI checkouts where `origin/$BASE` may not exist locally yet.

- If dependency reports were regenerated, the commit immediately following the
  bump has subject `Update dependency reports`, and `git status` is clean.

[wiki-versioning]: https://github.com/SpineEventEngine/documentation/wiki/Versioning
