---
slug: testcontainers-2
branch: master
owner: claude
status: in-review
started: 2026-08-05
---

## Goal

`Testcontainers.kt` in `buildSrc` pins `2.0.5` with the 2.x artifact names,
so config floats stop reverting consumers that already upgraded
(gcloud-jvm PR #202) and stop breaking their Testcontainers-2.x-based sources.

## Context

- Testcontainers 2.x renamed every module artifact with the `testcontainers-`
  prefix; only the core library keeps the plain `testcontainers` name.
  Verified on Maven Central at 2.0.5: `junit-jupiter`, `gcloud`, and `mysql`
  return 404, while `testcontainers-junit-jupiter`, `testcontainers-gcloud`,
  `testcontainers-mysql`, and `testcontainers-postgresql` exist.
- gcloud-jvm already pins 2.0.5 locally (its PR #202); every `Update config`
  commit reverts the pin and breaks `testlib` (`EmulatorContainer`).
- jdbc-storage's local `buildSrc` copy adds a `postgresql` constant used by
  `rdbms/build.gradle.kts`; config's copy must carry it, or the next float
  breaks that build script with an unresolved reference.

## Plan

- [x] Bump `version` to `2.0.5`; rename module artifacts to the 2.x names.
- [x] Add the `postgresql` constant so config's copy is a superset of
      consumer usages (gcloud-jvm and jdbc-storage).
- [x] Compile config's `buildSrc`.
- [x] Sync gcloud-jvm's `buildSrc` copy to the canonical file; compile there.
- [x] Run `dependency-audit` on the diff.

## Log

- 2026-08-05 — started; scope prescribed by the user request. Commit and
  push are left to the human — changes are staged only.
- 2026-08-05 — done and verified: config `./gradlew help` OK;
  gcloud-jvm `:testlib:classes` OK against the synced canonical file
  (byte-identical, so the next float is a no-op there);
  `dependency-audit` verdict APPROVE, no findings.
  jdbc-storage build scripts keep compiling (all referenced constants
  present); its test sources still target the 1.x API and need their own
  2.x migration when the float lands.
