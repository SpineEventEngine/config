---
name: gradle-10-third-party-deprecations
description: Gradle 9.6–9.8 deprecation nags from Detekt, Gradle Doctor, Dokka, Kover, and the Protobuf plugin (incl. `Configuration.setVisible`) are not our build logic — don't chase them in `buildSrc`.
metadata:
  type: project
  since: 2026-07-02
---

Building with `--warning-mode all` on Gradle 9.6.1 still prints two
deprecation warnings that originate in third-party plugins, not in
`buildSrc`. Both become hard errors in Gradle 10:

- `ReportingExtension.file(String)` — Detekt 1.23.8
  (`DetektPlugin.apply`). Fixed on `main`, but maintainers state no
  further 1.23.x releases are planned; the fix ships with Detekt 2.0
  (alpha as of 2026-07, new plugin ID `dev.detekt`).
  See [detekt#8452][detekt-8452].
- `Project.getProperties` — Gradle Doctor 0.12.1
  (`RemoteCacheEstimation`); no newer release available and no upstream
  issue filed as of 2026-07.

A third nag — Kover's "Using a Project object as a dependency notation"
(`PrepareKover.kt`) — is **resolved**: Kover 0.9.9 fixes it
([kotlinx-kover#818][kover-818], released 2026-07-17), and this branch
bumped `Kover.version` 0.9.8 → 0.9.9. Verified by building `buildSrc`
with `--warning-mode all`: the warning is present at 0.9.8 and gone at
0.9.9.

Gradle 9.8 deprecates `Configuration.setVisible(boolean)`, which Gradle 11
removes. `help` in `tool-base` reports 36 calls, all from plugin bytecode.
Status on 2026-10-12 — no stable release fixes it in any of them:

- Dokka 2.2.0 (22 hits) — fixed by [dokka#4601][dokka-4601], not in
  a stable release yet; 2.3.0-Beta is the only newer version.
- Kover 0.9.11 — unfixed, even on `main`; [kotlinx-kover#836][kover-836]
  is open. 9 hits were counted at 0.9.9; 0.9.10 and 0.9.11 still call it.
- Protobuf plugin 0.10.0 (3 hits) — fixed by
  [protobuf-gradle-plugin#816][protobuf-816]; not released yet.
- detekt 1.23.8 (2 hits) — fixed by [detekt#9577][detekt-9577] on `main`
  (2.0 line) only.

**Why:** branch `address-gradle-deprecations` removed every deprecated
usage from our own build logic (Kotlin DSL delegated accessors,
`PropertyDelegate`). The residual nags mislead agents into "fixing" our
scripts or bumping plugins to pre-releases. Safety rules forbid
auto-updating dependencies outside a dedicated update task, and the
`dependency-update` skill rejects pre-releases.

**How to apply:** treat these warnings as known and out of our hands
at the versions currently pinned; do not chase them in `buildSrc`.
Re-check upstream during each `dependency-update` run: Detekt 2.0 final
(mind the plugin ID change and config migration), Gradle Doctor > 0.12.1,
and the `setVisible` fixes above reaching stable releases. Remove each
entry once resolved. Before Gradle 10 (Gradle 11 for `setVisible`), these
must be resolved or the plugins dropped.

[detekt-8452]: https://github.com/detekt/detekt/issues/8452
[kover-818]: https://github.com/Kotlin/kotlinx-kover/issues/818
[dokka-4601]: https://github.com/Kotlin/dokka/pull/4601
[kover-836]: https://github.com/Kotlin/kotlinx-kover/issues/836
[protobuf-816]: https://github.com/google/protobuf-gradle-plugin/pull/816
[detekt-9577]: https://github.com/detekt/detekt/pull/9577
