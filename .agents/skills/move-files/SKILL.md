---
name: move-files
description: >
  Move or rename any files/directories in a repo: preserve history, update all
  references and build metadata, verify no stale paths remain.
---

# Move Files

## Workflow

1. Preflight.
   - Run `git status --short`; treat existing changes as user-owned.
   - Map each `source -> destination`.
   - Confirm sources exist and destinations do not, unless merge/overwrite was
     explicit.
   - Classify scope: simple same-module moves stay targeted; package, module, or
     cross-module moves need broader inspection.

2. Search before moving.
   - Search exact old paths, filenames, package/module names, resource paths, and
     docs links.
   - For Gradle/module/source-set moves, check `settings.gradle.kts`,
     `build.gradle.kts`, and `buildSrc`.
   - For Kotlin/Java, update package declarations only when package intent
     changes.

3. Move safely.
   - Prefer `git mv` for tracked files in the repo.
   - Use filesystem moves only for untracked/generated/out-of-git files.
   - Create parent directories first.
   - For case-only renames, move through a temporary name.
   - Ask before ambiguous mappings, destination conflicts, or unclear semantic
     package/module changes.

4. Repair references.
   - Update imports, package declarations, build metadata, docs links, resource
     paths, fixtures, samples, and scripts.
   - Start search scope narrow: affected directory, then module, then repo-wide.
   - Prefer precise edits; avoid broad replacements on generic names.

5. Verify.
   - Re-run targeted searches for old tokens.
   - Run `git status --short` and confirm the delta matches the move.
   - Run focused validation for moved files, or state what could not run.

## Repo Notes

Follow `.agents/project-structure-expectations.md` for module/source-set/test
moves. Prefer `src/main/kotlin` and `src/test/kotlin` unless the module differs.

## Report

Return: `Moved[]`, `UpdatedRefs[]`, `Verification[]`, `Risks[]`.
