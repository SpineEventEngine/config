---
slug: api-discovery
branch: improve-api-discovery
owner: claude
status: in-review
started: 2026-05-21
---

## Goal

Make Spine API discovery fast and token-efficient by directing agents
to read library sources from local sibling clones first, and from a
one-time-extracted sources-JAR cache otherwise — never via repeated
`unzip` against Gradle-cache JARs.

## Context

Investigation transcripts (see
`~/Desktop/gradle-caches-scanning-by-claude.png`) show agents running
dozens of `find ~/.gradle/caches` + `unzip -l` + `unzip -p` calls per
query. Each call decompresses the JAR; token usage is dominated by
path noise and JAR listings. The user keeps every Spine repo cloned
as a sibling under `/Users/sanders/Projects/Spine/`, so the raw
sources are already on disk for ~14 of the 16 Spine local deps and
just need to be reached directly.

Detailed design in `~/.claude/plans/mellow-juggling-yeti.md`.

## Plan

- [x] Draft plan + design review (Plan agent)
- [x] Write task file
- [x] Implement `lib/common.sh` (shared bash helpers)
- [x] Implement `discover` (main entry)
- [x] Implement `extract-sources` (one-shot JAR extraction, race-safe)
- [x] Implement `clean-cache` (manual pruning)
- [x] Write `README.md` + `.gitignore` for `.agents/scripts/api-discovery/`
- [x] Write `SKILL.md` for `.agents/skills/api-discovery/`
- [x] Add one bullet to `CLAUDE.md` under Workflow Rules
- [x] Smoke tests (#1–#7 from plan)
- [ ] Human review / merge; delete file on merge to master

## Log

- 2026-05-21 — drafted plan; Plan-agent reviewed and pushed back on
  over-engineering (manifest, sibling repo, exit codes). Adopted
  simplifications.
- 2026-05-21 — cache location: `<workspace>/.agents/caches/api-discovery/`
  with first-use bootstrap prompt (approve / alt root / non-cached).
  Scripts and skill live in the consumer repo's `.agents/`.
- 2026-05-21 — implementation begins.
- 2026-05-21 — implementation complete. All 7 smoke tests pass.
  Resolved all 24 Spine artifacts end-to-end (Base/Change/Logging KMP/
  multi-module ProtoData/Validation/Tool-base/Mc-java). Extension-cache
  path tested with Jackson and Guava — concurrent extractions race
  safely on atomic `mv` with no `.tmp.*` leftovers. `STALE` warning
  fires for validation-java (declared .433 vs sibling .440). KMP
  source sets (`src/commonMain`, `src/jvmMain`, …) recognized in
  addition to plain `src/main`. Status flipped to `in-review` for
  human merge; task file will be deleted on merge to `master`.
- 2026-05-21 — code-review pass applied six fixes:
  (1) `extract-sources`: pre-test `[ -e "$target" ]` plus post-mv
      nested-debris cleanup. Previous version was unsafe because
      `mv tmp target` into an existing directory silently moves tmp
      INSIDE target on macOS/Linux instead of failing.
  (2) `discover`: replaced `target=$(...); status=$?` with
      `target=$(...) || exit $?` so `set -e` cannot terminate
      between assignment and status check.
  (3) `clean-cache`: added `prune_empty_parents()` and call it on
      both removal and "no entries match" paths (skipped under
      `--dry-run`). Empty `<group>/<artifact>/` dirs are now
      reclaimed.
  (4) `read_declared_version`: anchored regex at line start (with
      optional access modifier) to avoid matching `const val version`
      strings in KDoc / comments / nested code.
  (5) Removed dead `find_dep_file_for_artifact` (callers all use
      `find_local_dep_file_for_artifact`).
  (6) `find_local_dep_file_for_artifact`: validate artifact name
      against `[A-Za-z0-9._-]` and ERE-escape it via new
      `escape_ere()` before grep, blocking regex-metachar injection.
  All seven smoke tests still pass; race-safety verified by parallel
  extractions of `guava-testlib:33.5.0-jre` (both exit 0, no `.tmp.*`
  remnants, no nested `target/v.tmp.PID/` debris).
