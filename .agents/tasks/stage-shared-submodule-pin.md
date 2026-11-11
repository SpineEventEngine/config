---
slug: stage-shared-submodule-pin
branch: stage-shared-submodule-pin
owner: claude
status: in-review
started: 2026-11-11
---

## Goal

Make the commit of a `./config/pull` record the `.agents/shared` version it floated
to, so consumers stop pinning the commit of the day the submodule was adopted.

## Context

- `.gitmodules` declares `ignore = all` for `.agents/shared` (set by
  `adopt-shared-agents`), which hides the moved pointer from `git status` and
  `git diff`; Git 2.56 `git add` skips it ("Skipping submodule due to
  ignore=all … Use --force").
- `pull` floated the on-disk copy but never staged the pointer, so the
  "Update shared agent tooling and config (./config/pull)" commits recorded the new
  `config` pointer but never `.agents/shared`. In `base-libraries`, the pin dated
  2026-06-02 and lagged 312 commits behind `agents@master` (2026-11-11).

## Plan

- [x] `pull`: after a successful float, `git add --force` the submodule — only when
      it is exactly at `refs/remotes/origin/<branch>`, so a local branch ahead of
      the remote is never pinned; a failed float stages nothing.
- [x] Docs that said the pin is never maintained: `init-submodules` header,
      `AGENTS.md`, `docs/project.md`, `adopt-shared-agents` comment.
- [x] `scripts/test-pull-stages-shared-pointer.sh` — throwaway local repos, isolated
      from the user's Git config: float staged, reported, and committed;
      ahead-of-remote not staged, with a NOTE; failed float stages nothing, with a
      WARNING; a no-op pull leaves the index clean. 13/13 pass; `origin/master`'s
      `pull` fails 7 of them.

## Log

- 2026-11-11 — implemented and verified.
- 2026-11-11 — `review-docs` and `spine-code-review` APPROVE WITH CHANGES; applied:
  neutral NOTE wording, a "Staged … at <sha>" line, "commit the pull on its own",
  the test kept in the repo, doc wording. Owner asked for the PR.
