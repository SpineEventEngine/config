---
name: pre-pr
description: >
  Run the pre-PR checklist for this repo: confirm `version.gradle.kts` was
  bumped, run the right Gradle build per `.agents/running-builds.md`, and
  invoke the configured reviewers (`kotlin-reviewer`, `review-docs`,
  `dependency-auditor`) against the branch diff. On success, write a
  sentinel file at `.git/pre-pr.ok` so the `gh pr create` hook can verify
  the checklist ran for the current HEAD. Use before opening a PR, or
  when CI rejected a branch and you want a fast local repro.
---

# Pre-PR checklist (repo-specific)

You are the pre-PR gate for a Spine Event Engine project. You compose the
existing reviewers and the documented release rules into a single pass that
must succeed before a pull request is opened.

The authoritative standards live in `.agents/`:

- `.agents/version-policy.md` — "PRs without a version bump fail CI."
- `.agents/running-builds.md` — which Gradle target to run (`build`,
  `clean build`, or `dokka`) based on what changed.
- `.agents/safety-rules.md` and `.agents/advanced-safety-rules.md` — hard
  constraints checked by the reviewers.
- The reviewer skills/agents themselves: `kotlin-reviewer` (Claude agent),
  `review-docs` (skill + Claude agent), `dependency-auditor` (Claude agent).

## Procedure

Execute the steps in order. If a step fails, stop, write a `FAIL` sentinel
(see step 6), and report the failure — do not run the remaining steps.

### 1. Determine scope

- Base ref: `master` unless the user provides a different one.
- Diff command: `git diff <base>...HEAD --name-only` for the file list,
  `git diff <base>...HEAD --stat` for the summary.
- Classify the changes:
  - **proto** — any `*.proto` file changed.
  - **code** — any `*.kt`, `*.kts`, or `*.java` file changed.
  - **docs** — any `*.md` file or doc-only edits inside sources changed.
  - **deps** — any file under `buildSrc/src/main/kotlin/io/spine/dependency/`
    changed.

### 2. Version-bump check

- Read `version.gradle.kts` at HEAD and at `<base>`.
- Confirm the version string is strictly greater (semver + Spine snapshot
  rules — see `.agents/version-policy.md`).
- If unchanged or decreased, stop with a Must-fix: "Run `/bump-version`."

### 3. Build

Pick the target per `.agents/running-builds.md`:

- **proto** changed → `./gradlew clean build`
- Else **code** changed → `./gradlew build`
- Else **docs**-only → `./gradlew dokka` (tests not required)

Run the chosen target. Surface the first failing module/task. On build
failure, stop and write a `FAIL` sentinel.

### 4. Reviewers (run in parallel)

Dispatch the relevant reviewers concurrently and collect their verdicts:

- Always: `kotlin-reviewer` (if **code** changed) and `review-docs` (if
  **docs** or KDoc changed).
- If **deps** changed: `dependency-auditor`.

Each reviewer is read-only and emits a Must-fix / Should-fix / Nits
report plus a one-line verdict (`APPROVE`, `APPROVE WITH CHANGES`, or
`REQUEST CHANGES`).

### 5. Aggregate

- Overall **PASS** when:
  - Version bump check passed,
  - Build succeeded,
  - Every dispatched reviewer returned `APPROVE` or `APPROVE WITH CHANGES`
    *and* no Must-fix items remain unaddressed in this session.
- Otherwise **FAIL**.

### 6. Sentinel

Write `.git/pre-pr.ok` at the repo root (NOT under `.claude/` — the
sentinel must travel with the local clone, not be checked in). Format:

```
head=<full HEAD SHA>
branch=<current branch>
status=PASS|FAIL
timestamp=<ISO-8601 UTC>
build=<the gradle target that was run, or "skipped">
reviewers=<comma-separated reviewer names that were invoked>
```

The `gh pr create` hook (`.claude/scripts/pre-pr-gate.sh`) checks this
file. The sentinel is invalidated automatically when HEAD advances —
the hook compares the recorded `head=` against the current HEAD SHA.

## Output format

Report in this shape:

```
## Pre-PR checklist (<branch> vs <base>)

| Check         | Status | Notes                                  |
|---------------|--------|----------------------------------------|
| Version bump  | …      | <old> → <new>                          |
| Build         | …      | <gradle target>                        |
| kotlin-review | …      | <verdict + count of Must/Should>       |
| review-docs   | …      | <verdict + count of Must/Should>       |
| dep audit     | …      | <verdict + count of Must/Should>       |

**Overall: PASS|FAIL**
Sentinel: .git/pre-pr.ok (status=PASS|FAIL, head=<SHA>)
```

On `PASS`, end with: "You can now run `gh pr create`."
On `FAIL`, end with the specific blocker and the next action.

## Notes

- This skill must NOT create the PR itself. It only gates whether the
  workspace is ready.
- The sentinel lives under `.git/` (untracked by definition) so it is
  per-clone and never committed.
- Each reviewer remains the source of truth for its own checks; this
  skill does not duplicate their rules — it only orchestrates and
  aggregates.
