---
slug: fix-config-skip-rule
branch: HEAD
owner: codex
status: in-review
started: 2026-05-30
---

## Goal
Make review and pre-PR skills apply the config-distributed file skip list only in consumer repositories, while reviewing config-owned sources inside the `config` repository itself.

## Context
The skill audit found that `pre-pr`, `kotlin-review`, and `review-docs` applied the consumer-repository skip list too broadly. `AGENTS.md` says `config` itself must review these files normally except `gradlew` and `gradlew.bat`.

## Plan
- [x] Locate affected skill instructions.
- [x] Patch the affected skills with repo-aware skip rules.
- [x] Verify no unconditional config-distributed skip instructions remain.

## Log
- 2026-05-30 19:50 WEST - Started targeted docs fix for config-aware review filtering.
- 2026-05-30 19:50 WEST - Updated `pre-pr`, `kotlin-review`, and `review-docs`; verified remaining config-distributed fast paths are scoped to consumer repos.
