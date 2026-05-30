---
slug: cover-remaining-skill-findings
branch: HEAD
owner: codex
status: in-review
started: 2026-05-30
---

## Goal
Remove the remaining skill-pickup ambiguity found in the audit and commit the completed skill updates.

## Context
The remaining findings are tool-dialect wording (`Read`/`Grep`/`Glob`, slash-style skill invocations) and unclear `pre-pr` reviewer orchestration.

## Plan
- [x] Inspect remaining ambiguous skill wording.
- [x] Patch skill instructions to use portable tool and skill references.
- [x] Clarify `pre-pr` reviewer execution for a single Codex session.
- [x] Verify, stage, and commit the completed changes.

## Log
- 2026-05-30 23:10 WEST - Started final skill-audit cleanup.
- 2026-05-30 23:10 WEST - Replaced tool-dialect and slash-style skill wording; clarified single-agent `pre-pr` reviewer execution.
