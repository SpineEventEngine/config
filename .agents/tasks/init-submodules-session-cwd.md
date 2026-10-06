---
slug: init-submodules-session-cwd
branch: init-submodules-session-cwd
owner: claude
status: in-review
started: 2026-10-05
---

## Goal

When Claude Code runs `init-submodules` as a `SessionStart` hook in a worktree
session, the script initializes the *worktree's* config-managed submodules
(`config`, `.agents/shared`), not the main checkout's. Manual runs keep acting on
the current directory and never wait for input.

## Context

- Consumers receive the script from `migrate` (`cp init-submodules ..`) and wire
  `$CLAUDE_PROJECT_DIR/init-submodules` as a `SessionStart` hook, as config does in
  `.claude/settings.json` and `.claude/settings-hugo.json`. The hook has no matcher,
  so it fires on `startup`, `resume`, `clear`, and `compact`.
- In a worktree session, `$CLAUDE_PROJECT_DIR` and the hook's process directory stay
  in the main checkout. Only the `cwd` field of the hook input JSON follows the
  session into the worktree. `git rev-parse --show-toplevel`, run from the process
  directory, therefore acts on the main checkout. Observed on 2026-10-02 in a
  `jdbc-storage` worktree session: its `config` and `.agents/shared` stayed
  uninitialized until the script was run by hand.
- The patch was written and reviewed in another session. Decisions kept:
  - read stdin only when `CLAUDE_PROJECT_DIR` is set and stdin is not a terminal,
    so a manual run whose stdin never closes does not hang;
  - climb from `cwd` out of submodules to the project's own work tree, bounded by
    the project's common Git directory;
  - fall back to the current directory in every other case.
- Constraints: bash 3.2 (stock macOS); self-contained, because the script runs
  before `.agents/shared` exists.

## Plan

- [x] Apply the patch to `init-submodules`.
- [x] Verify in throwaway repos under the session scratchpad: a consumer-shaped
      fixture, the desktop worktree layout, and isolated Git config.
  1. `cwd` is a fresh worktree: its `config` and `.agents/shared` get initialized,
     `docs/theme` is skipped, and the main checkout is not acted on. Contrast
     with `master`'s script.
  2. Manual run whose stdin is an open FIFO that never delivers: returns at once.
  3. Terminal run (`script`) with `CLAUDE_PROJECT_DIR` set: returns at once.
  4. Empty stdin, JSON without `cwd`, malformed JSON, and a nonexistent `cwd`:
     each falls back to the current directory.
  5. `cwd` is `<main>/config`: config's own `.agents/shared` stays uninitialized
     and its `core.hooksPath` unset; main's is set.
  6. `cwd` is an unrelated repo with `.gitmodules`: its `core.hooksPath` stays untouched.
- [x] Fix two defects that re-verification found in the new code. These are
      deltas from the reviewed patch:
  - `unset CDPATH`. In a main checkout, `--git-common-dir` prints a relative
    `.git`. Under an exported `CDPATH` (`.`, or a directory holding a `.git`),
    `cd` into it echoed the directory or entered the wrong one. Worktree
    detection then silently fell back to the main checkout.
  - `common_git_dir` checks each `git` result. `cd ""` succeeds in bash, so a
    non-repo directory returned its own path instead of the documented empty
    string, and the loop's `[ -n "$project" ]` guard never fired. This had no
    observable effect.
- [x] `bash -n` and `shellcheck`; run everything under `/bin/bash` 3.2.
- [x] Confirm the `./init-submodules` paragraph in `AGENTS.md` still reads
      accurately. No README or `docs/` page describes the script.
- [x] Show the diff and stop. No commit, push, or PR without an explicit request.

## Rollout notes (for the PR description)

- Consumers get the new file via `./config/pull` (`migrate` copies it), then
  commit it.
- Worktree sessions run the main checkout's copy
  (`$CLAUDE_PROJECT_DIR/init-submodules`). The fix takes effect once the main
  checkout has the new file.

## Log

- 2026-10-05: plan written. The user's prompt supplied the reviewed patch and
  asked to apply and verify it, which serves as approval. Executing.
- 2026-10-05: patch applied, byte-identical to the other session's copy. The
  harness has 15 sections and 70 checks, run with `/bin/bash` 3.2.57, git 2.54,
  and jq 1.7.1. The six requested cases are joined by checks for a subdirectory
  `cwd`, a nested submodule, a meta-repository bound, `CDPATH`, missing `jq`,
  `/tmp` symlinks, and spaces in paths. Results: the reviewed patch passes all six
  requested cases but fails 5 checks (4 for `CDPATH`, 1 for `common_git_dir`);
  with both fixes, 70/70. `shellcheck` reports 0 findings on the branch and on
  `master`. Awaiting review.
- 2026-10-05: the session that wrote the patch independently reported the
  `CDPATH` bug, which Codex's review of SpineEventEngine/agents#45 found. It is
  already fixed here. Without `CLAUDE_PROJECT_DIR`, runs behave exactly as on
  `master` (7/7 on both) in a Codex `SessionStart` hook (which runs in the
  session `cwd`), an agent shell, and a terminal. The `config` repository's
  `.codex/hooks.json` has no `SessionStart` entry, and `migrate` does not
  distribute `.codex/`.
- 2026-10-05: committed as `2cb777e0` and opened as #782. A follow-up commit
  applies the `review-docs` wording suggestions.
- 2026-10-05: Codex's review of #782 flagged the undeclared `jq` dependency.
  Without `jq`, worktree detection fell back to the main checkout. When `jq` is
  missing, a pattern match now reads a `cwd` value that holds no JSON escape
  sequence. The harness has 83 checks; the previous commit fails the 4 that run
  without `jq`.
- 2026-10-05: Codex then flagged that this fallback refused every escaped value,
  and a native Windows path is always escaped (`C:\\Users\\…`). `json_unescape`
  now decodes `\\`, `\"`, and `\/`; any other escape still falls back. The
  harness has 103 checks; the previous commit fails the 6 that decode without
  `jq`.
- 2026-10-05: Copilot's review of `ecc78002` flagged that the `jq`-free fallback
  accepts malformed input. A JSON validator in Bash was not added. The comment
  now states that without `jq` only the first `cwd` member is read, and the path
  must still belong to the project. Harness section 18 pins both points (108
  checks).
