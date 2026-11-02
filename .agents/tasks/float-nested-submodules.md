---
slug: float-nested-submodules
branch: fix-nested-submodule-float
owner: claude
status: in-review
started: 2026-11-02
---

## Goal

After `init-submodules` or `./config/pull` moves a config-managed submodule, its
own submodules sit at the commits its new HEAD pins, so a recursive status of a
fresh consumer worktree is clean.

## Context

- `init-submodules` initializes a submodule recursively at the superproject's pin,
  then floats it with `git checkout` and `git merge --ff-only`, which leave nested
  submodules where they were. `pull` has the same gap after
  `git checkout master && git pull --ff-only` in `config`.
- For `config` the nested submodule is `.agents/shared`, declared `ignore = all`,
  so plain `git status` hides the mismatch while `git status
  --ignore-submodules=none` and Claude Code's archive check report
  ` M .agents/shared`. Reproduced on 2026-11-02 in a fresh worktree of
  `documentation`: `config` pinned at `f5a8d40`, floated to `3a0d13a`, while
  `config/.agents/shared` stayed at `9cb20bd` instead of `551ad4f`.
- Decisions:
  - nested submodules go to the commits the new HEAD pins, not to their own
    branch tip, even when they track a `branch`. That pin is maintained (`config`
    bumps `.agents/shared` in its own commits), floating it would bring the
    mismatch back whenever `agents` moves on, and a consumer reads nothing
    through `config/.agents/shared`;
  - `init-submodules` passes `--init --recursive`, repeating its own
    initialization at the new HEAD; `pull` passes `--recursive` only, so it moves
    nested submodules already checked out and clones none, since many consumers
    never materialized `config/.agents/shared`;
  - each nested submodule's own update strategy applies, so with `update = merge`
    one already past the pin is not rewound;
  - a failed nested update warns and never fails the session or the pull.

## Plan

- [x] Update nested submodules after a successful float in `init-submodules`.
- [x] Do the same in `pull`, after step 1 and after each float in step 2.
- [x] Verify in a fresh `git worktree` of `documentation` at `bdf35bd` (pins
      `config` at `f5a8d40`): the old script leaves `config:  M .agents/shared`;
      the new one leaves the recursive status empty, and a second run moves
      nothing. The new `pull` repairs a worktree the old script left broken.
- [x] Verify the edge cases in a synthetic fixture with local remotes: a
      submodule the tip adds is cloned; a failed nested update warns and exits
      `0`; `pull` clones no nested submodule that was not checked out, and with
      `update = merge` does not rewind one past the pin. The unfixed scripts
      fail the fixture's pin and status checks.
- [ ] Merge, then delete this file.
