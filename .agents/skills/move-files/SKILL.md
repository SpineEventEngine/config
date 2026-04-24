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

## Complexity Classifier

Classify the request before doing broad inspection:

- `SIMPLE`: one or a few file moves/renames within the same module/package
  intent, no destination conflicts, no module/build layout changes.
- `STRUCTURAL`: package relocations, source-set reshuffles, many-file
  reorganizations, module moves, or cross-module moves.
- `RISKY`: destination exists, merge/overwrite ambiguity, case-only rename on
  case-insensitive filesystems, incomplete mapping, or unclear user intent.

Use this class to choose the workflow depth and token budget.

## Workflow

Use this skill to move files without losing history, overwriting user work, or
leaving broken references behind.

### Fast Path (`SIMPLE`)

1. Preflight (minimal).
   - Identify source-to-destination mapping.
   - Run `git status --short` first and treat existing changes as user-owned.
   - Confirm each source exists.
   - Confirm destination does not exist.
2. Move.
   - Prefer `git mv` for tracked files inside the same repository.
   - Use filesystem moves only for untracked files or outside git control.
3. Repair only affected references.
   - Run targeted searches for exact old path/name/package tokens.
   - Update only concrete hits in nearby module/files first.
4. Verify by delta.
   - Re-run targeted checks for old tokens.
   - Run `git status --short` and confirm expected move-only delta.

### Full Path (`STRUCTURAL` and `RISKY`)

1. Understand and map.
   - Identify each source path and destination path.
   - Determine move type: rename, package/module relocation, or reorganization.
   - For multiple files, write down the source-to-destination mapping before
     editing.
2. Inspect current tree and boundaries.
   - Run `git status --short` first and treat existing changes as user-owned.
   - Confirm each source exists and whether it is tracked.
   - Confirm each destination does not already exist unless explicitly approved.
   - Inspect neighboring naming and placement conventions.
3. Find references before moving.
   - Search for old paths, filenames, package names, module names, resource
     paths, and docs links.
   - Check build files such as `settings.gradle.kts`, `build.gradle.kts`, and
     `buildSrc` for module/source-set/test moves.
   - For Kotlin and Java, update package declarations only when intended package
     changes.
4. Move files safely.
   - Prefer `git mv` for tracked files inside the same repository.
   - Use filesystem moves only for untracked files, generated artifacts, or
     outside git control.
   - Create destination parent directories before moving.
   - For case-only renames on case-insensitive filesystems, move via temporary
     intermediate name.
   - Do not overwrite/delete/merge destination content unless explicitly asked.
5. Repair references comprehensively.
   - Update imports, package declarations, build includes, docs links, resource
     paths, test fixtures, sample paths, and scripts mentioning old paths.
   - Prefer precise edits over broad replacements.
6. Verify result.
   - Run `git status --short` and confirm changed files match the requested
     move.
   - Run focused validation relevant to moved files.
   - If validation cannot be run, state exactly what was not run and why.

## Escalation Rules

- Start reference search in the smallest relevant scope:
  affected directory -> affected module -> repository-wide.
- Escalate only if lower-scope checks leave unresolved references.
- Stop and ask the user before continuing when:
  - destination already exists and merge/overwrite intent is unclear,
  - source-to-destination mapping is incomplete/ambiguous,
  - move implies semantic package/module intent not specified by user.
- If uncertainty remains after escalation, report uncertainty explicitly and
  avoid destructive actions.

## Token Budget Guidance

- Prefer targeted checks over broad repository scans.
- Use exact tokens (full old path/name/package) before fuzzy searches.
- Avoid repeating the same search once checks are clean.
- Keep intermediate status concise unless user requests detail.

## Compact Result Schema

Report with compact sections:

- `Moved[]`: `source -> destination`
- `UpdatedRefs[]`: key references/metadata changed
- `Verification[]`: checks run and outcomes
- `Risks[]`: intentional stale refs, unresolved ambiguity, or follow-ups

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
