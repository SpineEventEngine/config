---
slug: init-submodules-float-to-branch-tip
branch: init-submodules-float-to-branch-tip
owner: claude
status: in-review
started: 2026-10-16
---

## Goal

A fresh working tree must get the config-managed submodules at the tip of their
tracked branch, not at the commit the superproject pins.

## Context

- `init-submodules` initialized a missing submodule and stopped at the pin. An
  audit on 2026-10-16 found those pins months stale (set between June and
  1 October 2026) in **all 19** Spine consumer repositories, so every
  `git worktree add`, CI checkout, and cloud session materialized hook scripts,
  skills, and guidelines nobody had run for months. In `compiler`, pinned at
  `7bd2d9ee`, the file `.agents/scripts/session-work-tree.sh` did not exist at all.
- The staleness is structural, not neglect, and bumping the pins is a treadmill:
  `.gitmodules` declares `ignore = all` for `.agents/shared`, which hides the
  submodule from `git status` and `git diff`, and `./config/pull` floats the
  on-disk copy without ever committing the moved gitlink. `git log -- .agents/shared`
  returns exactly one commit (the adoption commit) in 17 of the 19 repos. On top
  of that, `Version Guard` and `ensure-reports-updated.yml` make a
  pointer-only PR structurally unmergeable in 15 of them.
- Decisions kept:
  - reuse the existing "config-managed" rule rather than widen it — `config`
    itself plus every submodule declaring a tracked `branch`, exactly what
    `./config/pull` floats, so the two scripts cannot disagree;
  - float with `pull`'s own three steps (`fetch`, `checkout`, `merge --ff-only`),
    leaving the submodule on the branch rather than in a detached `HEAD`;
  - act only on the `-` lines of `git submodule status`, so a submodule already
    present is never rewound, never advanced, and costs no network;
  - no nested re-float after the merge, matching `pull`, which does not do it
    either;
  - resolve a `branch = .` declaration — which gitmodules(5) defines as the
    superproject's current branch — before it reaches a refspec, since
    `git fetch origin .` is a `fatal: invalid refspec`. Nothing in Spine
    declares it, but it is valid anywhere. A detached superproject has no name
    to inherit, so the float is skipped and the pin kept. `./config/pull` has
    the same gap for already-checked-out submodules; that is pre-existing and
    left for a follow-up;
  - a failed float warns and leaves the submodule wherever the steps that did
    succeed put it — at worst the pin — and is never retried, because the
    submodule is no longer a `-` line, so the warning points at `./config/pull`;
    a failed clone (no network) leaves it uninitialized as before; the script
    keeps its `exit 0` semantics either way, so a `SessionStart` hook can never
    fail a session.
- Constraints: bash 3.2 (stock macOS); self-contained, because the script runs
  before `.agents/shared` exists.

## Plan

- [x] Float a freshly initialized config-managed submodule to `origin/<branch>`.
- [x] Update the header comment block and the `AGENTS.md` paragraph that
      `migrate` distributes to consumers.
- [x] Verify in a throwaway `git worktree` of `compiler` (pinned at `7bd2d9ee`):
      `.agents/shared` lands on the `agents` `origin/master` tip `afabda57`, on
      `heads/master`, and `.agents/scripts/session-work-tree.sh` exists; a second
      run changes nothing.
- [x] Verify in a synthetic consumer repo: the consumer-owned submodule (no
      tracked branch) stays uninitialized across both runs; a float that cannot
      reach its branch warns, leaves the pinned commit checked out, and exits `0`.
- [ ] Merge, then delete this file.
