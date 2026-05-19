---
name: version-bumped
description: >
  Verify the current branch has bumped `version.gradle.kts` strictly above
  the base ref when it contains publishable changes; invoke `/bump-version`
  to auto-recover if not. Composable: other modifying skills
  (`dependency-update`, `bump-gradle`, `java-to-kotlin`, `move-files`) call
  this as their final step so a `./gradlew build` or
  `publishToMavenLocal` can never overwrite a previously published Maven
  Local artifact that integration tests in consumer repos depend on.
---

# Ensure version is bumped

This skill is the agent-facing wrapper around
`.agents/scripts/version-bumped.sh`. The script is the source of truth for
"is this branch safe to publish from"; this skill just runs it and, if it
fails, invokes `/bump-version` and re-runs to confirm.

The same logic is enforced as a hook
(`.claude/scripts/publish-version-gate.sh`) that fires before any
`./gradlew … (build|publish|publishToMavenLocal)` invocation, so even
direct gradle calls cannot bypass it. This skill exists for the cooperative
path — other skills calling it before they finish, so the user is never
surprised by a blocked gradle command later.

## When to use

- Automatically: as the final step of any skill that may modify
  publishable files (Kotlin/Java/proto sources, `buildSrc/**`,
  `gradle/wrapper/**`, `gradlew*`, `version.gradle.kts` itself, or
  `docs/dependencies/**`).
- Manually (`/version-bumped`): before running `./gradlew build` or
  `./gradlew publishToMavenLocal` on a feature branch when you are not
  sure whether the version has already been bumped.

## Procedure

1. Run the deterministic check:

   ```bash
   .agents/scripts/version-bumped.sh
   ```

   Honor `VERSION_BUMPED_BASE` if the user has set a non-default base ref
   (e.g. `origin/master`, or a release branch).

2. Interpret the exit code:

   - **0** — Done. Either the repository has no root `version.gradle.kts`
     (the version check is `N/A`), the branch has no publishable changes
     vs the base, or the version is already strictly greater. Report a
     one-line confirmation and stop.
   - **1** — Block. The script's stderr explains which check failed.
     Proceed to step 3.
   - **2** — Configuration error (no merge-base, parse failure on
     `version.gradle.kts`). Do **not** invoke `/bump-version`
     automatically. Surface the script's stderr to the user and stop.

3. On exit 1, invoke `/bump-version` to perform the actual bump. That
   skill owns the policy (snapshot numbering, the commit subject, the
   rebuild, dependency-report regeneration, and the conflict rule). Do
   not duplicate its work here.

4. After `/bump-version` finishes, re-run the deterministic check. If it
   now passes, report the new version on the branch. If it still fails,
   surface the stderr unchanged and stop — do not loop.

## Why this skill is separate from `/bump-version`

`/bump-version` is the **action** (it edits `version.gradle.kts`,
commits, rebuilds, may commit reports). `/version-bumped` is the
**guard** (read-only check, optional auto-recovery). Skills that want to
say "make sure the branch is in a publishable state" should call
`/version-bumped`, not `/bump-version`, because the guard is a no-op when
the bump is already done — calling `/bump-version` unconditionally would
double-bump on every chained skill invocation.

## See also

- `.agents/version-policy.md` — when the version gate applies.
- `.agents/skills/bump-version/SKILL.md` — the bump procedure itself.
- `.agents/skills/pre-pr/SKILL.md` — uses the same check at PR time
  (step 2).
- `.agents/scripts/version-bumped.sh` — the deterministic check.
- `.claude/scripts/publish-version-gate.sh` — the hook that enforces the
  rule on `./gradlew` invocations.
