---
name: update-copyright
description: >
  Update source file copyright headers from the IntelliJ IDEA copyright profile
  in `.idea/copyright/profiles_settings.xml`, resolving the default profile XML,
  reading its `notice` option, and replacing `today.year` with the current year.
  Automatically apply this skill when source files are modified in a change set.
---

# Copyright Update

Use this skill when asked to add, refresh, or normalize source file copyright
statements according to the repository's IntelliJ IDEA copyright profile.

## Workflow

1. Preflight.
   - Run `git status --short`; treat existing changes as user-owned.
   - Identify the requested scope: explicit files, directories, or all tracked
     source files when no scope is given.

2. Resolve the notice text.
   - Read `.idea/copyright/profiles_settings.xml`.
   - In the `settings` tag, read the `default` attribute.
   - Convert that profile name to the XML filename by replacing each run of
     non-alphanumeric characters with `_`, trimming leading/trailing `_`, and
     appending `.xml`.
     Example: `TeamDev Open-Source` becomes `TeamDev_Open_Source.xml`.
   - Read `.idea/copyright/<profile-file>`.
   - Use the `value` attribute of the `option` tag whose `name` is `notice`.
   - Decode XML/HTML character references, then replace `${today.year}`,
     `$today.year`, or bare `today.year` with the current year.

3. Update files.
   - Prefer running `scripts/update_copyright.py`; it resolves the notice,
     detects common source comment styles, replaces an existing top copyright
     block when present, and inserts one when missing.
   - Pass explicit paths when the user gave a scope:

     ```bash
     python3 .agents/skills/update-copyright/scripts/update_copyright.py path/to/File.kt scripts/
     ```

   - With no paths, the script updates tracked source-like files, excluding
     generated/build/tooling directories and Gradle wrapper files:

     ```bash
     python3 .agents/skills/update-copyright/scripts/update_copyright.py
     ```

   - Use `--dry-run` first for broad changes:

     ```bash
     python3 .agents/skills/update-copyright/scripts/update_copyright.py --dry-run
     ```

4. Verify.
   - Re-run `git status --short` and inspect the changed files.
   - For broad updates, spot-check at least one block-comment file, one
     hash-comment file, and one XML file if present.
   - Run a targeted search such as `rg -n "Copyright [0-9]{4}, TeamDev"` when
     the profile follows the TeamDev notice format.

## Report

Return: `NoticeSource[]`, `UpdatedFiles[]`, `Verification[]`, `Risks[]`.
