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
    the same gap for already-checked-out submodules, fixed there separately in
    PR #804. `init-submodules` resolves it the same way: with
    `git branch --show-current`, not `git rev-parse --abbrev-ref HEAD`, which
    shortens to a non-ambiguous name and so yields `heads/foo` when a tag
    shares the branch name; the fetch then succeeds but the checkout fails;
  - name the fetch destination explicitly, as
    `+refs/heads/<branch>:refs/remotes/origin/<branch>`. A submodule declaring
    `shallow = true` is cloned single-branch, so for any other tracked branch a
    bare `git fetch origin <branch>` reaches only `FETCH_HEAD` and
    `refs/remotes/origin/<branch>` never appears at all;
  - ask whether the branch exists locally, with `git show-ref --verify`, rather
    than letting Git work the checkout out, because both answers are otherwise
    wrong in a reachable case. A fresh clone has a local branch only for the
    default branch, so `-b` alone would fail with `already exists` on the
    common path; and plain `git checkout <branch>` guesses from a
    remote-tracking ref only when the name resolves to nothing, considering
    only refs the remote's fetch refspec covers, so it fails in the
    single-branch clone above — while a submodule carrying a *tag* of that name
    makes it resolve the tag and detach `HEAD`, which `--ff-only` then
    fast-forwards to the right commit on no branch at all, silently;
  - keep `merge --ff-only` after either checkout path, so a local branch that
    has diverged from `origin/<branch>` is reported rather than overwritten,
    and nothing force-moves a ref but the remote-tracking ref the fetch owns;
  - merge `refs/remotes/origin/<branch>`, not the `origin/<branch>` shorthand.
    gitrevisions(7) resolves a bare name through `refs/tags/` before
    `refs/remotes/`, so a tag literally named `origin/<branch>` wins it. An
    ordinary clone fetches such a tag, and the merge would then fast-forward
    the branch the checkout had just placed exactly onto whatever the tag
    points at, reporting success — turning the narrow-clone path from a warned
    failure into a silent wrong float. Every ref in the chain is now fully
    qualified, in both scripts;
  - give `./config/pull` the same two fixes, so the scripts do not diverge. It
    carried the same bare fetch and bare checkout, and there the silent detach
    matters more: `pull` floats submodules that are already checked out and in
    use, which may carry local work. That work is safe either way, and the two
    cases differ only in whether anything is said: a local branch merely *ahead*
    of `origin/<branch>` floats silently and keeps its commits, while one that
    has genuinely diverged — commits on both sides — fails `--ff-only` and is
    reported by the existing warning, also keeping its commits. Neither depends
    on the `update` strategy; the first commit's message said the diverged case
    was silent, which was an artefact of a fixture whose remote had not moved
    past the pin, so `update = merge` reconciled it before the float;
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
