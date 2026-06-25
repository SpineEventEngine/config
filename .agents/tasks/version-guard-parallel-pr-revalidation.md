---
slug: version-guard-parallel-pr-revalidation
branch: improve-version-bump
owner: claude
status: draft
started: 2026-06-25
related-memories:
  - config-auto-merge-drops-late-commits
  - spine-tool-version-publish-ordering
---

## Goal

Close the version-publish gap behind core-jvm-compiler PR #104 (see
`core-jvm-compiler-pr104-version-publish-investigation.md`) and make it safe for
**many agents to open PRs in parallel that all bump to the same next version**.
Concretely:

1. A PR that does **not** raise the version above the target branch fails
   deterministically at PR time (not via a network proxy).
2. When one of several same-version PRs merges, the others are **actively
   re-judged** against the advanced base and go red **before** they merge, so an
   agent re-bumps them — no human in the loop in the common case.
3. The published artifact is **never** corrupted/overwritten.

Accepted residual (explicit product decision): in the rare simultaneous
auto-merge race, a **red Publish run remains**. It is loud and recoverable; it
never corrupts an artifact. The Publish workflow **must not write to `master`**.

## Context

- Root cause: `CheckVersionIncrement` only asks "is `project.version` already in
  remote Maven metadata?" — never "did this PR raise the version above base?".
  It is fail-open (a 404/!200 read → `null` → "unpublished" → pass) and racy.
- Publish fires on **every** push to `master`, so every merged PR must carry a
  version strictly greater than `master`'s current version.
- The deterministic git base-compare already exists in `version-bumped.sh`
  (agent-side only) and a correct numeric comparator exists in
  `report/pom/VersionComparator.kt`. Reuse both; do not reinvent.

### Decisions locked this session (do NOT re-litigate)

| Question | Decision | Why |
|---|---|---|
| Where does the increment check live? | **Inside the `CheckVersionIncrement` Gradle task** | Covers both the CI-PR path and the local `publishToMavenLocal` path with one edit; unit-testable in `buildSrc` (config has no root `version.gradle.kts`); no required gate depending on the *floating* `.agents/shared` submodule. |
| Force stale PRs red after a merge? | **Active re-validation** workflow on push to base | Config-shippable; no merge-queue adoption; no "require up to date" rebuild storm. |
| Re-run the PR workflow vs. post a commit status? | **Commit status** (context `Version Guard`) | `gh run rerun` replays the old event and is fragile to locate; a SHA-bound commit status is deterministic and self-clears on the re-bump push. |
| Make publish never collide (self-heal / derive number)? | **Rejected** | Would require the Publish workflow to write `master` (or a large version-model rework). User: do not write `master`; a rare red Publish is acceptable. |
| Merge queue? | **Rejected** | Serializes merges → slows parallel agents. |
| "Require branches up to date before merging"? | **Off** | Rebuild storm at N parallel agents; the publish backstop already guarantees no corruption. |

**Necessary-but-not-sufficient:** active re-validation narrows the window; it
loses the race to auto-merge (auto-merge fires in ms; the fan-out arrives in
tens of s — cf. `config-auto-merge-drops-late-commits`, the #708 incident). The
**only** guarantee against overwriting an immutable artifact is the fail-closed
publish-collision check on the publish path. Keep it load-bearing.

## Plan

### A. Deterministic PR-time gate (in-task base-compare)
- [ ] `buildSrc/.../publish/CheckVersionIncrement.kt`: add a base-compare —
      read base via `Cli(rootDir).execute("git","show","origin/$GITHUB_BASE_REF:version.gradle.kts")`,
      extract the version literal, compare `project.version` vs base with
      `VersionComparator`. **Fail when head ≤ base.** Fail-closed on ambiguity
      (base ref unresolvable / unparseable → `GradleException`). Treat
      base-file-absent as OK (newly introduced). Keep the existing network
      "already published?" check too (now a secondary catch on this path).
- [ ] `report/pom/VersionComparator.kt`: promote from `report.pom`-`internal`
      to a shared internal location; reuse. **No `sort -V` anywhere.**
- [ ] `IncrementGuard.kt`: no wiring change (both paths already inherit it);
      widen the task description from "not yet published" to "advanced vs. base".
- [ ] `.github-workflows/increment-guard.yml`: make the base tip resolvable —
      `fetch-depth: 0` **or** a targeted `git fetch --depth=1 origin
      +refs/heads/$GITHUB_BASE_REF:refs/remotes/origin/$GITHUB_BASE_REF`.
      (Plain `git fetch origin $BASE` lands in `FETCH_HEAD` and does **not**
      reliably create `origin/$BASE` — avoid that form.)

### B. PR path emits the shared `Version Guard` commit status (self-clearing)
- [ ] `.github-workflows/increment-guard.yml`: add
      `permissions: { contents: read, statuses: write }`; after the gradle step,
      `if: always()` POST `state=success|failure, context="Version Guard"` to
      `github.event.pull_request.head.sha`. This is what makes a re-bump push
      self-clear (fresh `success` on the new SHA). Job stays gated to
      `master`/`main`/release-line bases (skipped job ⇒ no status ⇒ fine, since
      `Version Guard` is not required on auxiliary bases).

### C. Active re-validation on push to base (new)
- [ ] NEW `.github-workflows/revalidate-versions.yml`:
      `on: push: branches: [master, main, '*-master', '*-main']`;
      `permissions: { contents: read, statuses: write, pull-requests: read }`;
      `concurrency: { group: revalidate-versions-${{ github.ref }},
      cancel-in-progress: true }`; checkout + JDK 17 + Gradle; run the script.
      **Does not write `master`** (read-only checkout + status POSTs to PR heads).
- [ ] NEW `config/scripts/revalidate-versions.sh` (config-owned, like
      `decrypt.sh`): read base version at the new tip; `gh pr list --base
      "$BASE_REF" --state open --limit 200` (skip drafts — `--limit` matters,
      `gh` truncates at 30); for each, read the head's `version.gradle.kts` via
      `gh api repos/$REPO/contents/version.gradle.kts?ref=$sha`; compare via the
      headless comparator (D); if `head ≤ base`, POST `Version Guard = failure`
      to that head SHA; leave passing heads untouched. Fork heads the token
      cannot status stay Pending = blocked (acceptable; out of agent threat model).

### D. Headless `VersionComparator` entry point (no shell math)
- [ ] buildSrc: add a tiny `main()`/task that compares two version strings with
      the existing `VersionComparator` and prints/exits `-1|0|1`. Single source
      of comparison semantics for the PR task, the publish backstop, and the
      fan-out script.

### E. Publish backstop + notifier (the actual guarantee)
- [ ] `.github-workflows/publish.yml`: ensure the fail-closed network
      "already published?" check runs **before** publish and hard-fails on
      collision. Harden the fetch: distinguish a genuine 404 (OK) from any other
      IO error / timeout (fail-closed), add cache-busting. **No `master` writes.**
- [ ] `.github-workflows/publish.yml`: add an `if: failure()` notifier so the
      rare red Publish alerts a human/agent to bump `master` and re-run.

### F. Tests
- [ ] `buildSrc/.../publish/IncrementGuardTest.kt`: pure base-compare cases —
      `head == base → fail` (the #104 case), `head < base → fail`,
      `head > base → pass`, base-absent → pass, unparseable → fail-closed.
- [ ] Comparator entry-point cases (incl. `…SNAPSHOT.100 > .99`, `-RC`/`-SNAPSHOT`
      qualifier edges that `sort -V` would get wrong).

### G. Docs
- [ ] `.agents/shared/guidelines/version-policy.md`: reword the gate from
      "fails when the version already exists in Maven" to "must be advanced vs.
      base", with the publish check named as the collision backstop. *(Lives in
      the `agents` submodule — separate PR there, not in this config branch.)*

### H. Manual repo-admin (per consumer repo — cannot be shipped/tested by config)
- [ ] Require the status context **`Version Guard`** (replacing the old
      `Check version increment` check-run requirement).
- [ ] Leave **"require branches up to date before merging" OFF**.
- [ ] Document the rare-red-Publish runbook: bump `master` `.NNN → .NNN+1`
      (via a tiny PR) and re-run Publish. The Publish workflow does not self-heal.

### Verify
- [ ] `JAVA_HOME=17 ./gradlew :buildSrc:build detekt` green (config has no root
      `build` task; this is the buildSrc verification per team memory).
- [ ] Run `kotlin-engineer` + `spine-code-review` (+ `gradle-review`) over the diff.

## Log
- 2026-06-25 — drafted from the investigation + two adversarial design passes
  (in-task base-compare; commit-status active re-validation; rejected
  self-heal/commit-back and merge queue per user constraints). Awaiting approval
  to implement.
