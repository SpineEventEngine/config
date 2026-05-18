---
description: Run the pre-PR checklist (version bump, build, reviewers) and write a sentinel so `gh pr create` is unblocked.
argument-hint: "[base-ref]"
allowed-tools: Read, Write, Grep, Glob, Bash(./gradlew:*), Bash(git diff:*), Bash(git log:*), Bash(git status:*), Bash(git rev-parse:*), Bash(git ls-files:*), Bash(git show:*), Bash(date:*)
---

Follow the `pre-pr` skill exactly:

- Skill: `.agents/skills/pre-pr/SKILL.md`
- Base ref: $ARGUMENTS (treat empty as `master`).
- Dispatch the reviewers as Claude subagents in parallel — send a single
  message with multiple Agent tool uses:
  - `kotlin-review` when `.kt|.kts|.java` files changed.
  - `review-docs` when `.md` files or KDoc inside sources changed.
  - `dependency-audit` when any file under
    `buildSrc/src/main/kotlin/io/spine/dependency/` changed.
- Each reviewer is read-only; do not pass it edit tools.
- On any reviewer returning `REQUEST CHANGES`, treat the overall result
  as `FAIL` and stop before writing the sentinel as `PASS`.
- Sentinel location: `$(git rev-parse --show-toplevel)/.git/pre-pr.ok`,
  format per the skill (`head=`, `branch=`, `status=`, `timestamp=`,
  `build=`, `reviewers=`). Use `git rev-parse HEAD` for the SHA and
  `date -u +%Y-%m-%dT%H:%M:%SZ` for the timestamp.
- Do NOT run `gh pr create`. That is the user's next step.
