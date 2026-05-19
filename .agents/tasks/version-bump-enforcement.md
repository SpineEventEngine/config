---
slug: version-bump-enforcement
branch: improve-version-bump-enforcement
owner: claude
status: in-review
started: 2026-05-19
---

## Goal

Make it impossible to overwrite Maven Local artifacts on a feature branch
without first bumping `version.gradle.kts`. Today, skills that produce a
publishable change (e.g. `/dependency-update` run first on a branch) leave
the version untouched, so the next `./gradlew build` (which can transitively
trigger `publishToMavenLocal` in this config) silently overwrites previously
published snapshots that integration tests rely on.

## Context

- Repos using this `config` may chain `publishToMavenLocal` into `build`
  for integration-test prerequisites — so `build` itself is publish-risky.
- `.agents/skills/pre-pr/SKILL.md` already gates the version on PR creation
  via `.git/pre-pr.ok` (step 2). We want the same gate to fire earlier,
  during day-to-day work, not only at PR time.
- `.claude/scripts/protect-version-file.sh` already blocks direct edits to
  `version.gradle.kts`; the canonical bump path is `/bump-version`.

## Plan

- [x] Draft this task plan and create TaskCreate items mirroring the steps
- [x] Write `.agents/skills/version-bumped/scripts/version-bumped.sh` — deterministic Layer-1
      check. Returns 0 if (no `version.gradle.kts`) OR (no publishable
      diff vs base) OR (HEAD version > base version). Returns 1 with a
      stderr pointer to `/bump-version` otherwise.
- [x] Write `.claude/scripts/publish-version-gate.sh` — PreToolUse hook
      on `Bash`. Triggers on any `./gradlew … build|publish|
      publishToMavenLocal` invocation (broad-and-safe per user
      decision). Runs Layer 1; on failure, blocks the gradle command.
- [x] Wire the new hook into `.claude/settings.json` under
      `PreToolUse → Bash`, after the existing `pre-pr-gate.sh`.
- [x] Write `.agents/skills/version-bumped/SKILL.md` — Layer 3,
      composable skill (`/version-bumped`) that runs the script and
      invokes `/bump-version` if the check fails, then re-runs to
      confirm.
- [x] Hook the skill into modifying skills:
  - [x] `dependency-update/SKILL.md` — inserted between "review the diff"
        and "run ./gradlew build" in the next-steps list
  - [x] `bump-gradle/SKILL.md` — added as step 8
  - [x] `java-to-kotlin/SKILL.md` — added as final section
  - [x] `move-files/SKILL.md` — added as step 6 (conditional on
        publishable files moved)
  - [x] `update-copyright` — **no change** (always a sub-step of
        another skill that does the check; per user decision)
- [ ] ~~Refactor `pre-pr/SKILL.md` step 2 to call the shared script~~ —
      dropped: `pre-pr` enforces "always bump if versioned project" as PR
      policy, which is stricter than Layer 1's "bump if publishable diff".
      Different purposes; merging them would weaken the PR gate.
- [x] Smoke-test `version-bumped.sh` and `publish-version-gate.sh`
      locally with a synthetic diff — 7 Layer-1 scenarios + 7 hook
      scenarios all pass. Fixed two bugs during smoke-test:
      `read -r` skipping the final segment without trailing newline;
      and `$?` being 0 after a no-else `if-fi` whose test failed.
- [ ] Commit on this branch; do not push (user pushes when ready)

## Log

- 2026-05-19 — drafted; user approved the sketch via "proceed with
  the implementation"
- 2026-05-19 — branch `improve-version-bump-enforcement` created
- 2026-05-19 — all three layers implemented and smoke-tested; status
  flipped to `in-review` pending user review and commit
- 2026-05-19 — bugs caught by smoke-test (read loop, rc capture) fixed
