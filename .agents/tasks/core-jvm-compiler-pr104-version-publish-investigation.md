# Investigation: behavior-changing PR merged without a version bump → publish silently lost

> **Handoff note.** This report is written so it can be attached to a **fresh
> `config`-repo session** with no prior context. The incident originates in
> `SpineEventEngine/core-jvm-compiler` PR #104 and surfaced as a build failure in
> `SpineEventEngine/compiler`, but the *machinery that should have caught it* —
> the **Version Guard** (`checkVersionIncrement`) and the **Publish** workflow —
> is owned and distributed by **`config`**. The goal of the follow-up work is to
> harden that machinery. All file paths below are **`config`-repo-relative**.

---

## 1. TL;DR

1. core-jvm-compiler **PR #104 "Support ID fields of enum type"** was merged to
   `master` **without bumping the version** — it stayed at
   `2.0.0-SNAPSHOT.079`.
2. `.079` had **already been published** to GAR (from a prior `master` build that
   predates #104).
3. The PR-time **Version Guard** check (`checkVersionIncrement`) **passed**,
   because it only verifies *"this version is not yet published"* — **not**
   *"the version was incremented relative to the base branch."*
4. The post-merge **Publish** workflow then **failed** at *"Publish artifacts to
   Maven"* — you cannot overwrite the already-published fixed version `.079`.
5. **Net effect:** #104's code lives on `master` but was **never published**. The
   published `.079` artifact is the *pre-#104* build. Every downstream consumer
   pinned to `.079` silently lacks the fix, and **no published version contains
   it** until someone bumps to `.080` and publishes.

This is a **process/tooling gap**, not a one-off mistake: the guard is named
"check version increment" but does not enforce an increment, and the only thing
that *did* catch the collision (the publish step) runs **after** the PR is
already merged, when it is too late to block.

---

## 2. The config-owned mechanisms involved

| Concern | File (config-relative) | What it does |
|---|---|---|
| Version Guard workflow | `.github-workflows/increment-guard.yml` | Distributed to consumers as `.github/workflows/increment-guard.yml`. Job **"Check version increment"**, `on: pull_request`, runs only when `github.base_ref` ends with `master`/`main`. Runs `./gradlew checkVersionIncrement`. |
| The Gradle task | `buildSrc/src/main/kotlin/io/spine/gradle/publish/CheckVersionIncrement.kt` | Fetches the artifact's published `maven-metadata.xml` and **fails only if the version list already `contains(project.version)`** (`checkInRepo`, ~lines 70–85). It never compares against the base branch. |
| Task enablement | `buildSrc/src/main/kotlin/io/spine/gradle/publish/IncrementGuard.kt` | `shouldCheckVersion(event, baseBranch)` (lines 54–59) enables the task only for `pull_request` events targeting `*master`/`*main`. Wires the task into `check` (line 83). |
| Publish workflow | `.github-workflows/publish.yml` | Distributed as `.github/workflows/publish.yml`. Job **"Publish to Maven repositories"**, `on: push: branches: [master]`. Step **"Publish artifacts to Maven"** performs the actual deploy — and **fails if the version already exists**. |
| Prior design context | `.agents/tasks/version-guard-aux-base-branches.md` | Earlier task that scoped `IncrementGuard` to PR-to-master/main only. Useful background for why the guard is shaped the way it is. |

**Key semantic to internalize:** `checkVersionIncrement` is really
`checkVersionNotYetPublished`. A PR that does **not** touch the version passes it,
as long as that version is not (yet) in the published metadata at the moment the
check runs.

---

## 3. Evidence & timeline (all timestamps UTC, 2026-06-19 unless noted)

| Time | Event | Evidence |
|---|---|---|
| 2026-06-18 16:06 | core-jvm-compiler version bumped to `.079` in a **separate** commit `0618423e` ("Bump version -> `2.0.0-SNAPSHOT.079`") — *not* part of #104 | `git log` of `version.gradle.kts` |
| 08:02:43 | **Publish** workflow on `master` head `66521edb` (version `.079`) — **success** → `.079` lands on GAR (the pre-#104 build) | Actions run list; `66521edb:version.gradle.kts` = `.079` |
| 12:15:49 | #104's **"Check version increment"** check — **success** (PR head `75e8fdd3`, version `.079`, no bump) | check-runs on `75e8fdd3` |
| 12:48:46 | **#104 merged** to `master` (merge commit `1ef7e7c`), base `master`, **no version change** | `gh pr view 104` |
| 12:48:49 | **Publish** workflow on the #104 merge `1ef7e7c` — **FAILURE** at step *"Publish artifacts to Maven"* | Actions run `27826655989`, job "Publish to Maven repositories" |
| now | core-jvm-compiler `master` still declares `.079`; latest published `core-jvm-plugins` on GAR is `.079` (pre-#104). The fix is unpublished. | GAR `maven-metadata.xml`; `master:version.gradle.kts` |

**Confirmed facts**
- PR #104 contains **no** version bump (verified: `75e8fdd3:version.gradle.kts` = `.079`).
- The published `.079` predates #104. Its `RequiredIdReaction` **rejects** enum ID
  fields; the post-#104 code (master, and the local `.080` build) **allows** them.
- The merge's publish failed *because* `.079` was already on GAR (08:02) — fixed
  versions cannot be overwritten.

---

## 4. Gap analysis — why nothing blocked it

1. **The PR-time guard does not enforce an increment.** Despite the name,
   `checkVersionIncrement` only asks "is `project.version` already published?".
   A behavior-changing PR that keeps the same (not-yet-published) version sails
   through. There is no comparison of *PR-head version* vs *base-branch version*.

2. **Time-of-check / time-of-use (TOCTOU) race.** Even the "not-yet-published"
   semantics are racy: between a PR's check passing and that PR merging, **another
   merge can publish the same version**. That is exactly what happened here — by
   the time #104 merged (12:48), `.079` had been published (08:02) by a different
   `master` build, so #104's publish failed. GitHub's *"require branches up to date
   before merging"* is **not** enforced, so the stale check was never re-evaluated
   against the advanced base.

3. **The only effective guard runs too late.** The actual collision was caught by
   the **Publish** step — which runs **on push to `master`, after the PR is already
   merged**. A failed publish does not un-merge the PR; it just leaves `master`
   with unpublished changes and a red post-merge workflow that is easy to miss.

4. **Open question (central to the fix):** why did #104's check **pass at 12:15**
   when `.079` was already published at **08:02** (≈4 h earlier)? If the check had
   correctly seen `.079` as published, it would have **failed #104 at PR time** and
   forced a bump. Candidate causes to investigate in `config`:
   - GAR `maven-metadata.xml` propagation lag / CDN or HTTP caching seen by
     `CheckVersionIncrement.fetch()` (it does a plain `URL` read with no
     cache-busting).
   - Per-module metadata: the task runs per Gradle subproject
     (`project.artifactPath()` uses `artifactPrefix + project.name`); the specific
     module(s) checked may have lagged the publish of the fat-jar artifact.
   - The PR check ran against a checkout/metadata snapshot that did not yet list
     `.079`.
   Reproducing this race is the key to a durable fix.

---

## 5. Suggested directions for `config` (not prescriptive — design choices)

These are options to weigh, not a fixed plan:

- **A. Make the guard a real increment check.** In `CheckVersionIncrement` (or a
  companion task), compare the PR-head version against the **base-branch**
  `version.gradle.kts`. Fail when they are equal *and* the PR contains publishable
  changes. This directly catches "merged behavior change without a bump,"
  independent of publish timing/caching. (`IncrementGuard` already has
  `GITHUB_BASE_REF`; the base version can be read via `git show
  origin/$BASE:version.gradle.kts`.)

- **B. Close the TOCTOU gap.** Either require PR branches to be **up to date with
  base** before merge (so the guard re-runs against the advanced base), or add a
  cache-busting/freshness step to the metadata fetch so the "already published"
  read is authoritative at check time.

- **C. Make publish failures loud and actionable.** A failed **Publish** on
  `master` currently lands *after* merge. Consider alerting on it explicitly, or a
  pre-merge dry-run of the publish-collision check, so the signal arrives before
  it's too late to block.

- **D. Codify the "bump-on-behavior-change / one-bump-per-branch" policy in the
  tooling** rather than leaving it to convention, so the guard — not a human —
  enforces it.

Any change here ships to **all** consumer repos via `config` distribution, so it
should be covered by `IncrementGuardTest`
(`buildSrc/src/test/kotlin/io/spine/gradle/publish/IncrementGuardTest.kt`) and
validated against the `config` repo's own CI before floating.

---

## 6. How to reproduce / verify

1. **Confirm the published `.079` predates #104:** the GAR
   `core-jvm-plugins` `maven-metadata.xml` lists `.079` as latest; its
   `RequiredIdReaction` lists `enum` among *rejected* ID types, whereas
   core-jvm-compiler `master` (post-#104, commit `1ef7e7c`) lists `enum` among
   *allowed* types.
2. **Confirm the failed merge publish:** GitHub Actions run
   `27826655989` ("Publish", head `1ef7e7c`) → job "Publish to Maven
   repositories" → failed step "Publish artifacts to Maven".
3. **Confirm the guard semantics:** read
   `buildSrc/src/main/kotlin/io/spine/gradle/publish/CheckVersionIncrement.kt`
   (`checkInRepo` only fails on `versions.contains(version)`).

---

## 7. Downstream impact & the *separate* immediate unblock

The consumer symptom that triggered this investigation is in
`SpineEventEngine/compiler` (PR #74): `:api:launchTestSpineCompiler` fails because
a test proto uses an **enum entity-ID** and the build resolves the **pre-#104
`.079`** from GAR. That is being unblocked **separately** by:
- publishing core-jvm-compiler **`.080`** (already prepared on its
  `update-tool-base` branch, includes #104) to GAR, then
- bumping `CoreJvmCompiler` `.079 → .080` in the `compiler` repo.

That consumer unblock is **not** the subject of this report. The `config` work is
the **systemic** fix so a behavior-changing PR can never again merge without a
bump and silently fail to publish.

---

## 8. Reference index

- core-jvm-compiler PR #104 "Support ID fields of enum type" — merged
  2026-06-19T12:48:46Z, merge commit `1ef7e7c`, base `master`, **no version bump**.
- Version bump to `.079`: commit `0618423e` (2026-06-18T16:06:14Z), separate from #104.
- `.079` publish (success): Actions run on `master` head `66521edb`, 08:02:43.
- #104 "Check version increment" (success): 12:15:49 on PR head `75e8fdd3`.
- #104 merge publish (failure): Actions run `27826655989`, 12:48:49.
- config files to edit/inspect:
  - `.github-workflows/increment-guard.yml`
  - `.github-workflows/publish.yml`
  - `buildSrc/src/main/kotlin/io/spine/gradle/publish/CheckVersionIncrement.kt`
  - `buildSrc/src/main/kotlin/io/spine/gradle/publish/IncrementGuard.kt`
  - `buildSrc/src/test/kotlin/io/spine/gradle/publish/IncrementGuardTest.kt`
  - `.agents/tasks/version-guard-aux-base-branches.md` (background)
