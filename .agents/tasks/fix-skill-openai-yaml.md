---
slug: fix-skill-openai-yaml
branch: HEAD
owner: codex
status: in-review
started: 2026-05-30
---

## Goal
Add missing `agents/openai.yaml` metadata files so every local skill in `.agents/skills/` has UI-facing discovery metadata.

## Context
The read-only skill audit found that eight skills have `SKILL.md` frontmatter but no `agents/openai.yaml`.

## Plan
- [x] Identify missing skill metadata sidecars.
- [x] Add concise `interface` metadata for each missing skill.
- [x] Verify every skill now has `agents/openai.yaml`.

## Log
- 2026-05-30 19:46 WEST - Started metadata-only fix after audit findings.
- 2026-05-30 19:46 WEST - Added eight missing `openai.yaml` files and verified 15/15 skills have sidecars.
