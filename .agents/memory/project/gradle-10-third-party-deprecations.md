---
name: gradle-10-third-party-deprecations
description: Three deprecation nags under Gradle 9.6 come from Detekt, Kover, and Gradle Doctor, not our build logic; no released fixes yet — don't chase them in `buildSrc`.
metadata:
  type: project
  since: 2026-07-02
---

Building with `--warning-mode all` on Gradle 9.6.1 still prints three
deprecation warnings that originate in third-party plugins, not in
`buildSrc`. All of them become hard errors in Gradle 10:

- `ReportingExtension.file(String)` — Detekt 1.23.8
  (`DetektPlugin.apply`). Fixed on `main`, but maintainers state no
  further 1.23.x releases are planned; the fix ships with Detekt 2.0
  (alpha as of 2026-07, new plugin ID `dev.detekt`).
  See [detekt#8452][detekt-8452].
- "Using a Project object as a dependency notation" — Kover 0.9.8
  (`PrepareKover.kt`), the latest release as of 2026-07. Tracked in
  [kotlinx-kover#818][kover-818].
- `Project.getProperties` — Gradle Doctor 0.12.1
  (`RemoteCacheEstimation`); no newer release available and no upstream
  issue filed as of 2026-07.

**Why:** branch `address-gradle-deprecations` removed every deprecated
usage from our own build logic (Kotlin DSL delegated accessors,
`PropertyDelegate`). The residual nags mislead agents into "fixing" our
scripts or bumping plugins to pre-releases. Safety rules forbid
auto-updating dependencies outside a dedicated update task, and the
`dependency-update` skill rejects pre-releases.

**How to apply:** treat exactly these three warnings as known and
out of our hands; do not chase them in `buildSrc`. Re-check upstream
during each `dependency-update` run: Detekt 2.0 final (mind the plugin
ID change and config migration), Kover > 0.9.8, Gradle Doctor > 0.12.1.
Remove this memory once all three are resolved. Before Gradle 10, these
must be resolved or the plugins dropped.

[detekt-8452]: https://github.com/detekt/detekt/issues/8452
[kover-818]: https://github.com/Kotlin/kotlinx-kover/issues/818
