---
name: porting-buildsrc-from-consumer-repos
description: To back-port buildSrc improvements from a consumer repo, diff after its last "Update `config`" commit; consumer-owned files never port.
metadata:
  type: project
  since: 2026-06-11
---

Consumer repos receive `buildSrc` from `config` via `./config/pull`, recorded
as "Update `config`" commits. Improvements made in a consumer repo are exactly
its `git diff <last-update-config-commit>..HEAD -- buildSrc/`; a raw tree diff
overstates the delta.

**Why:** A tree diff between the consumer repo and `config` mixes four
unrelated things: genuine improvements, `config` having moved ahead since the
sync, consumer-owned files, and stale leftovers the pull script never deletes
(e.g. ProtoTap kept `io/spine/dependency/local/ProtoData.kt` after `config`
removed it).

**How to apply:** When asked to "bring buildSrc improvements from repo X to
`config`", find X's newest "Update `config`" commit and diff `buildSrc/` from
there to HEAD. Never port consumer-owned files: `module.gradle.kts` (a
template stub in `config` by design — same list as the AGENTS.md code-review
filter) and repo-local convention plugins/helpers living in X's `buildSrc`
(e.g. ProtoTap's `version-to-resources.gradle.kts`,
`PatchGeneratedTemplateString.kt`). Cross-check direction on each remaining
file — if `config` HEAD is newer (version bumps), the consumer copy is stale,
not improved.

Two port-time adaptations recur (both hit the validation#317 port):

- `config` runs detekt over `buildSrc`; consumer repos do not. A port that
  was green in the consumer repo can fail here (e.g. `TooManyFunctions`,
  top-level threshold 11 per file). Fix by refactoring — merge copy-paste
  helpers, reuse an existing `config` helper the consumer-side author did
  not know about (e.g. `consumesCoverageBinaryReports()` from
  `SiblingCoverage.kt`) — never by suppressing.
- Generalize consumer-specific KDoc (module names such as `java`/`context`,
  plugin names such as `JavaValidationPlugin`): `config` distributes these
  files to every repo, where such references mean nothing.
