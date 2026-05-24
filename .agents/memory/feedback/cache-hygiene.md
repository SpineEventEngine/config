---
name: cache-hygiene
description: Batch edits to shared config files to protect prompt cache hit rates across all sibling repos.
metadata:
  type: feedback
  since: 2026-05-24
---

Every edit to `CLAUDE.md`, `quick-reference-card.md`, or any skill's
`SKILL.md` invalidates the cached prompt prefix in every active session
across all ~40 sibling repos simultaneously. The `migrate` script copies
these files verbatim, so all repos share the same cache entry — and bust
it together on every config release.

**Why:** With two developers and concurrent agent sessions, cache hits
depend on stable content. Frequent small edits reset the 1-hour TTL
window repeatedly, turning potential cache reads (0.1× cost) into
repeated cache writes (1.25–2× cost).

**How to apply:**
- Collect unrelated tweaks to shared config files and release them in a
  single PR rather than committing each one individually.
- Target ≤ 2 shared-config releases per week during active development
  periods.
- After merging to master, run `./config/pull` in sibling repos promptly
  so they all land on the same config version within the same cache window.
- Per-session or per-task edits inside a sibling repo (code, tests, docs)
  do not affect the shared prefix — only changes to files that `migrate`
  copies matter.

Related: [[cache-warm-window]]
