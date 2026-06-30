---
slug: fix-temp-dirs-update-gh-pages
branch: claude/vibrant-heyrovsky-f88e0c
owner: claude
status: in-review
started: 2026-06-30
---

## Goal

Stop the `updateGitHubPages` task from leaking the `javadoc`, `html`, and
`repoTemp` temporary directories. They must be removed when the JVM shuts down,
even if the build fails before the existing eager cleanup runs
(GitHub issue [#240](https://github.com/SpineEventEngine/config/issues/240)).

## Context

`LazyTempPath` created temp dirs directly in `java.io.tmpdir` with no
shutdown-time cleanup, so a failed build (or a killed Gradle daemon) left them
behind forever. `File.deleteOnExit()` can't help — it does not delete non-empty
directories.

Approach mirrors base PR
[SpineEventEngine/base#671](https://github.com/SpineEventEngine/base/pull/671):
group every temp dir under one recognizable base directory and remove that base
dir recursively via a single JVM shutdown hook.

## Plan

- [x] Add `buildSrc/.../io/spine/gradle/fs/SpineTempDir.kt` — lazily-created base
  dir `<java.io.tmpdir>/io.spine.gradle.fs`, deleted recursively by one JVM
  shutdown hook.
- [x] Route `LazyTempPath` through `SpineTempDir.path`
  (`createTempDirectory(SpineTempDir.path, prefix)`); update KDoc.
- [x] Leave eager cleanup (`UpdateGitHubPages.cleanup()`, `Repository.close()`)
  as the primary mechanism.
- [x] Add tests: `fs/LazyTempPathSpec.kt` (4) and `fs/SpineTempDirSpec.kt` (2).
- [x] Verify: `-p buildSrc build` (whole suite green, 6/6 fs tests).
  Note: detekt is not applied to `buildSrc`'s own sources, so it is not a gate here.
- [x] Pre-PR reviewers (`kotlin-engineer`, `spine-code-review`, `review-docs`) all APPROVE.

## Log

- 2026-06-30 — drafted plan, approved, implemented files.
- 2026-06-30 — `-p buildSrc build` SUCCESSFUL (needs JDK 17 — `JAVA_HOME` defaults
  to Corretto 11 via jenv on this machine).
- 2026-06-30 — reviewers APPROVE; applied their feedback: log on shutdown-hook
  delete failure, KDoc "folder"→"directory", split `SpineTempDir` test into its
  own spec. Rebuilt green.
