---
slug: version-guard-aux-base-branches
branch: claude/modest-thompson-0aqffs
owner: claude
status: in-review
started: 2026-06-10
---

## Goal

PRs with a base branch other than a default (or release-line) one must not
fail `Version Guard`, `JUnit Test Report (push)`, or `License Reports`
because of a missing version increment. Only a branch heading into a
default/protected branch is responsible for bumping the version; auxiliary
branches stay out of the release cycle.

## Context

- `IncrementGuard` enables `checkVersionIncrement` on `push` to any branch
  not ending with `master`/`main` — it cannot know the merge target of
  a push, so feature branches of auxiliary branches fail.
- `checkVersionIncrement` is wired into `check`, so `Ubuntu CI` (`on: push`)
  fails the whole build; tests never run and `JUnit Test Report (push)`
  fails with "no test reports found".
- `License Reports` requires the dependency reports (which embed the
  version) to be touched in every PR, including PRs to auxiliary branches.
- The only reliable signal of "aims to merge into X" is a pull request
  with base `X` → use `pull_request` events and `GITHUB_BASE_REF`.

## Plan

- [x] `IncrementGuard.kt`: enable the task only for `pull_request` events
      whose `GITHUB_BASE_REF` ends with `master`/`main`; drop the
      `push`-event logic; update KDoc; keep the decision a pure function.
- [x] `increment-guard.yml`: trigger on `pull_request` (instead of `push`
      to all branches) and skip the job unless the base branch ends with
      `master`/`main`.
- [x] `ensure-reports-updated.yml`: same job-level base gate, so PRs into
      auxiliary branches do not require report updates.
- [x] Add `IncrementGuardTest` for the decision function.
- [x] Run buildSrc tests and detekt.

## Log

- 2026-06-10 — drafted and started.
- 2026-06-10 — implemented; buildSrc tests and detekt pass.
- 2026-06-10 — review (PR #686): replaced the `branches` filters with
  job-level `if` conditions — a workflow skipped by branch filtering
  leaves a required check `Pending` (blocking the PR), while a skipped
  job satisfies it.
