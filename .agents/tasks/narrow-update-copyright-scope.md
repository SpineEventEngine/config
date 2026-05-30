---
slug: narrow-update-copyright-scope
branch: HEAD
owner: codex
status: in-review
started: 2026-05-30
---

## Goal
Clarify that automatic `update-copyright` runs target only changed source files, avoiding unrelated repository-wide header churn.

## Context
The skill audit found that `update-copyright` says to apply automatically after source edits, but its no-path command path updates all tracked source files.

## Plan
- [x] Inspect current `update-copyright` skill wording and UI metadata.
- [x] Patch the skill to separate automatic changed-file scope from explicit repo-wide scope.
- [x] Verify the new wording and metadata are valid.

## Log
- 2026-05-30 22:37 WEST - Started targeted update-copyright scope fix.
- 2026-05-30 22:37 WEST - Updated `update-copyright` instructions and default prompt to target changed source files by default.
