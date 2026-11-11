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

- [x] `pull`: a new step 4, after `migrate`, `git add --force`s each config-managed
      submodule only when its `HEAD` is the tip of its branch on the remote the
      superproject declares (read with `git ls-remote`). After `migrate`, because
      adoption rewrites the URL to the canonical remote and resets `master` there; this
      also keeps a fork-only or ahead-of-remote commit, or a failed float, unstaged.
- [x] Docs that said the pin is never maintained: `init-submodules` header,
      `AGENTS.md`, `docs/project.md`, `adopt-shared-agents` comment.
- [x] `scripts/test-pull-stages-shared-pointer.sh` — throwaway local repos, isolated
      from the user's Git config. Without adoption: float staged, reported, committed;
      ahead-of-remote and failed float not staged; a no-op pull leaves the index clean.
      With the real `adopt-shared-agents`: the canonical tip is staged, and a consumer
      on a fork gets the canonical tip, never the fork's, so a fresh clone checks it
      out. 21/21 pass; the first version of this PR fails the fork case.

## Log

- 2026-11-11 — implemented and verified.
- 2026-11-11 — `review-docs` and `spine-code-review` APPROVE WITH CHANGES; applied:
  neutral NOTE wording, a "Staged … at <sha>" line, "commit the pull on its own",
  the test kept in the repo, doc wording. Owner asked for the PR.
- 2026-11-11 — PR #839. Copilot review: staging in step 2 ran before `migrate` adopts
  `.agents/shared` from the canonical remote, so a consumer on a fork got a fork-only
  commit staged under the canonical URL. Moved staging to a step 4 after `migrate`,
  verified against the declared remote; added adoption cases to the test.
