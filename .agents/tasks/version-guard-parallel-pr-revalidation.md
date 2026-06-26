---
slug: version-guard-parallel-pr-revalidation
branch: improve-version-bump
owner: claude
status: in-review
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

### A. Deterministic PR-time gate (in-task base-compare) — DONE
- [x] `buildSrc/.../publish/CheckVersionIncrement.kt`: `checkIncrementedAgainstBase()`
      reads base via `git show origin/$GITHUB_BASE_REF:version.gradle.kts` (a
      `ProcessBuilder`-based `gitShow` that redirects to temp files — avoids both
      `Cli`'s newline-stripping and a pipe-buffer deadlock), parses the publishing
      version with the new `VersionGradleFile` object (key identified by the
      resolved `project.version` as an oracle, so no hard-coded property name),
      compares with `VersionComparator`, and **fails when head ≤ base**.
      Fail-closed on an unresolvable base ref; pass on base-file-absent (newly
      introduced); skip-with-warning on an unrecognized `version.gradle.kts` shape.
      The existing network "already published?" check (`checkNotPublished()`) stays.
- [x] Promoted `VersionComparator` `report.pom` → `io.spine.gradle` (`git mv`,
      still `internal`); updated `DependencyWriter` import. **No `sort -V` in Kotlin.**
- [x] `IncrementGuard.kt`: **wiring change was needed** (see 2026-06-26 log). The
      task was a `dependsOn` of `check`, so the new base-compare ran in *every*
      `./gradlew build` — including `build-on-ubuntu.yml`, which never fetches the
      base ref — and failed closed. The `check` dependency was removed; the CI check
      now runs only via the `Version Guard` workflow (which fetches the base) and
      before `publishToMavenLocal` (which does not base-compare).
- [x] `.github-workflows/increment-guard.yml`: targeted base fetch
      `git fetch --no-tags --depth=1 origin +refs/heads/$GITHUB_BASE_REF:refs/remotes/origin/$GITHUB_BASE_REF`.

### B. PR path emits the shared `Version Guard` commit status (self-clearing) — DONE
- [x] `increment-guard.yml`: `permissions: { contents: read, statuses: write }`;
      `if: always()` step posts `state=success|failure, context="Version Guard"`
      to the PR head SHA, so a re-bump push self-clears a failure. **Guarded to
      non-fork PRs** (`github.event.pull_request.head.repo.fork == false`) — forks
      get a read-only token and are handled by a maintainer.

### C. Active re-validation on push to base (new) — DONE
- [x] NEW `.github-workflows/revalidate-versions.yml`: `on: push` to `'*master'`,
      `'*main'` (aligned with the PR guard's `endsWith`); least-privilege
      `permissions`; per-ref `concurrency` + `cancel-in-progress`; checkout only
      (no JDK/Gradle — pure shell). **Does not write `master`.**
- [x] NEW `scripts/revalidate-versions.sh` (distributed as `config/scripts/...`,
      run via `bash` so it needs no exec bit): reads the base version,
      `gh pr list --base "$BASE_REF" --state open --limit 200` (includes drafts —
      a stale draft must be marked failed; `increment-guard.yml` also adds the
      `ready_for_review` trigger so the draft->ready transition re-checks),
      reads each head's `version.gradle.kts` via the contents API, and posts
      `Version Guard = failure` on stale heads. **Comparison uses `sort -V`**,
      consistent with `version-bumped.sh` (qualifier-edge concern does not apply to
      a project's own successive snapshot numbers) — so the separate headless
      comparator (old step D) was **dropped** as unnecessary.

### E. Publish notifier — DONE
- [x] `.github-workflows/publish.yml`: `if: failure()` step emits a `::error::`
      annotation + runbook. The collision **backstop is the registry's
      immutability itself** (the #104 publish *failed* — overwrite is rejected), so
      no redundant pre-publish network probe was added. **No `master` writes.**

### F. Tests — DONE
- [x] `IncrementGuardTest.kt`: `VersionGradleFile` parser cases (literal, alias to
      another `extra`, alias to a plain `val`, oracle-by-value identification,
      no-match → null). `VersionComparatorSpec` already covers the numeric/qualifier
      ordering. Shell `parse_version`/`is_stale` exercised locally (literal/alias/plain;
      `.080==.080`→stale, `.081>.080`→ok, `.100>.99`→ok, `.9<.100`→stale).

### Verify — DONE
- [x] `JAVA_HOME=17 ./gradlew :buildSrc:build detekt` green (compile + tests + detekt).
- [x] `kotlin-engineer` + `spine-code-review` run over the diff; all should-fix
      findings addressed (deadlock-safe `gitShow`, `VersionComparator` promotion,
      fork-PR status guard, `bash` invocation, branch-glob alignment, doc nits).

### G. Docs — follow-up (separate `agents`-repo PR, NOT this branch)
- [ ] `.agents/shared/guidelines/version-policy.md`: reword the gate to "must be
      advanced vs. base", naming the registry immutability as the collision backstop.

### H. Manual repo-admin (per consumer repo — cannot be shipped/tested by config)
- [ ] Require the status context **`Version Guard`** (replace the old
      `Check version increment` check-run requirement).
- [ ] Leave **"require branches up to date before merging" OFF**.
- [ ] Runbook for the rare red Publish: bump `master` `.NNN → .NNN+1` (small PR)
      and re-run Publish — the Publish workflow does not self-heal.

## Log
- 2026-06-25 — drafted from the investigation + two adversarial design passes
  (in-task base-compare; commit-status active re-validation; rejected
  self-heal/commit-back and merge queue per user constraints).
- 2026-06-25 — implemented A–F. Build + detekt + tests green; two reviewers
  (`kotlin-engineer`, `spine-code-review`) approved-with-changes, all should-fix
  items applied (incl. `VersionComparator` promotion and a deadlock-safe `gitShow`).
  Refinements vs. the draft: Kotlin parses `version.gradle.kts` directly (oracle by
  `project.version`) instead of shelling to `version-bumped.sh`; the fan-out uses
  `sort -V` (headless comparator dropped); the publish backstop is registry
  immutability (no extra network probe). Status: in-review. Remaining: G (agents
  repo) and H (repo-admin).
- 2026-06-26 — **regression fix.** Applying this config to `compiler` broke its
  Ubuntu CI: `checkVersionIncrement` was a `dependsOn` of `check`, so `./gradlew
  build` ran the new base-compare in `build-on-ubuntu.yml`, which does a shallow
  checkout and never fetches `origin/master` — `git show origin/master:...` →
  exit 128 → fail-closed. Windows CI survived only by accident (`fetch-depth: 0`).
  The draft's "no wiring change needed (both paths inherit the task)" missed that
  the `check` path also fires in the plain build workflows, not just the dedicated
  `Version Guard` one. Fix: dropped `tasks.check.dependsOn(checkVersion)` so the
  base-compare runs only where the base ref is guaranteed — the `Version Guard`
  workflow (fetches base) and `publishToMavenLocal` (local; no base-compare).
  Added a regression test asserting `check` does **not** depend on the task.
