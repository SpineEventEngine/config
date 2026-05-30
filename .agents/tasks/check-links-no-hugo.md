---
slug: check-links-no-hugo
branch: HEAD
owner: codex
status: in-review
started: 2026-05-30
---

## Goal
Make `check-links` and `pre-pr` skip cleanly when a repository has no Hugo documentation site under `docs/` or `site/`.

## Context
The skill audit found that repositories like `config` can have `lychee.toml` without a Hugo site, causing `check-links` to fail with a setup error instead of reporting the link check as not applicable.

## Plan
- [x] Inspect current `check-links` and `pre-pr` site detection.
- [x] Patch both skills to treat missing Hugo site roots as not applicable.
- [x] Verify the wording and diff for review.

## Log
- 2026-05-30 22:41 WEST - Started no-Hugo-site link-check fix.
- 2026-05-30 23:10 WEST - Updated `check-links` and `pre-pr`; verified this repo's site detector returns not applicable.
