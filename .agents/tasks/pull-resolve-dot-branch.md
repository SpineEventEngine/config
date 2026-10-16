---
slug: pull-resolve-dot-branch
branch: pull-resolve-dot-branch
owner: claude
status: in-review
started: 2026-10-16
---

## Goal

`./config/pull` resolves a `branch = .` declaration to the superproject's
current branch name before the value reaches a Git refspec, so a submodule
declaring it floats to the right tip instead of dying on
`fatal: invalid refspec '.'`.

## Context

`pull` floats every *config-managed* submodule — `config` itself plus every
submodule declaring a tracked `branch` in `.gitmodules` — with the three steps
`git fetch origin <branch>`, `git checkout <branch>`,
`git merge --ff-only origin/<branch>`. The branch value comes straight from
`git config -f .gitmodules --get-regexp '^submodule\..*\.branch$'`.

gitmodules(5) gives `.` a special meaning: the branch in the submodule should
carry the same name as the current branch in the current repository. That is a
sentinel, not a ref. Passed through as-is, it makes `git fetch origin .` fail,
so the float warns about a nonexistent `origin/.` and the submodule is left
wherever it was.

Found by a Codex review of [#802], which fixed the identical gap in
`init-submodules` (commit `11ffb771`). It was kept out of that PR on purpose:
it is pre-existing, it affects already-checked-out submodules which #802 does
not touch, and folding it in would have widened a reviewed three-file diff.

Step-for-step parity between the two scripts is a deliberate design point, so
this mirrors #802's approach rather than inventing its own. The consequence
differs, though: `init-submodules` only ever floats a submodule it has just
cloned, whereas `pull` floats submodules that are already checked out and
in use — hence the detached-superproject path leaves the submodule strictly
untouched.

Nothing in Spine declares `.` today (all 20 local checkouts with a tracked
branch use the literal `master`), so this is latent robustness, not a live
outage.

[#802]: https://github.com/SpineEventEngine/config/pull/802

## Plan

- [x] Reproduce `fatal: invalid refspec '.'` against the pre-fix script
- [x] Mirror #802's resolution into `pull`, resolving before the
      `Floating '<path>' -> origin/<branch>` message and before
      `git submodule update --init`, so a skip really is a no-op
- [x] Verify with throwaway repos: named branch, detached superproject,
      ordinary `branch = master` run twice, and a superproject carrying both a
      branch and a tag of the same name
- [x] `bash -n` and `shellcheck` clean (only the pre-existing SC1091 on
      `source migrate`, unchanged from before)
- [ ] Open the PR against `master`

## Log

- 2026-10-16 — reproduced the failure, applied the fix, verified all three
  cases plus a branch the submodule remote lacks (falls into `pull`'s existing
  float-failure warning, naming the resolved branch)
- 2026-10-16 — pre-PR review: `spine-code-review` APPROVE, `review-docs`
  APPROVE WITH CHANGES. Took the prose fixes, and documented that the
  detached-superproject skip bypasses this entry's `git submodule update
  --init` too, leaving a not-yet-checked-out submodule to `init-submodules`
- 2026-10-16 — Codex review of `f1423f5e` found that
  `git rev-parse --abbrev-ref HEAD` shortens to a *non-ambiguous* name, so a
  superproject with both a branch and a tag called `foo` resolved to
  `heads/foo`: the fetch succeeded, the checkout failed, and the submodule was
  left stale and detached at the pin. Reproduced, then switched to
  `git branch --show-current`, which prints the name exactly and prints
  nothing when detached. `git symbolic-ref --short` was checked and shortens
  the same way, so it is not an alternative. The `= "HEAD"` half of the
  detachment guard went with it, being unreachable for the new command
