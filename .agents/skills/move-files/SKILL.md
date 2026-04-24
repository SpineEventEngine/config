---
name: move-files
description: >
  Move, rename, or reorganize files and directories safely in a repository.
  Use when an agent is asked to relocate source files, tests, documentation,
  assets, modules, packages, or folders; preserve git history with `git mv`
  where appropriate; update imports, package declarations, build metadata,
  documentation links, and path references; and verify no stale paths remain.
---

# Move Files

## Workflow

Use this skill to move files without losing history, overwriting user work, or
leaving broken references behind.

1. Understand the requested move.
   - Identify each source path and destination path.
   - Identify whether the task is a simple move, a rename, a package/module
     relocation, or a directory reorganization.
   - If multiple files are moving, write down the source-to-destination mapping
     before editing.

2. Inspect the current tree.
   - Run `git status --short` first and treat existing changes as user-owned.
   - Confirm each source exists and whether it is tracked.
   - Confirm each destination does not already exist unless the user explicitly
     requested a merge or overwrite.
   - Use `rg --files` or targeted directory listings to understand neighboring
     naming and placement conventions.

3. Find references before moving.
   - Search for old paths, filenames, package names, module names, resource
     paths, and documentation links with `rg`.
   - Check build files such as `settings.gradle.kts`, `build.gradle.kts`, and
     `buildSrc` when moving modules, source sets, generated fixtures, or tests.
   - For Kotlin and Java files, check package declarations and imports. Update
     package declarations only when the move changes the intended package.

4. Move the files.
   - Prefer `git mv` for tracked files inside the same repository.
   - Use normal filesystem moves only for untracked files, generated artifacts,
     or moves outside git control.
   - Create destination parent directories before moving.
   - For case-only renames on case-insensitive filesystems, move through a
     temporary intermediate name.
   - Do not overwrite, delete, or merge existing destination content unless the
     user explicitly asked for that exact operation.

5. Repair references.
   - Update imports, package declarations, build includes, docs links, resource
     paths, test fixtures, sample paths, and scripts that mention the old path.
   - Prefer precise edits over broad replacements. Inspect each search hit when
     the old name is generic.
   - Re-run `rg` for old paths and names until only intentional references
     remain.

6. Verify the result.
   - Run `git status --short` and confirm the changed files match the requested
     move.
   - Run focused tests, compilation, documentation checks, or repository build
     tasks appropriate to the files moved.
   - If validation cannot be run, state exactly what was not run and why.

## Repo-Specific Notes

- Follow `.agents/project-structure-expectations.md` when moving project
  modules, source sets, tests, or Gradle files.
- Follow `.agents/safety-rules.md` and never revert unrelated user changes.
- Prefer Kotlin source under `src/main/kotlin` and tests under
  `src/test/kotlin` unless the surrounding module uses a different convention.

## Final Response

Report:

- What moved from where to where.
- What references or metadata were updated.
- What verification ran and the result.
- Any intentional stale references or follow-up risks.
