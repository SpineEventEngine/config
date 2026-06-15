---
name: config-build-verification
description: config root has no `build` task — verify buildSrc changes with `./gradlew :buildSrc:test detekt`, exporting JAVA_HOME first.
metadata:
  type: project
  since: 2026-06-11
---

The root project of `config` applies no Java/Kotlin plugin, so the generic
`./gradlew build` from `running-builds.md` fails with "Task 'build' is
ambiguous". The Kotlin code lives in `buildSrc`, and Gradle 8+ no longer
runs `buildSrc` tests as part of the main build.

**Why:** `running-builds.md` is shared guidance from the floating
`.agents/shared` submodule and cannot encode per-repo exceptions. CI in
this repo runs only `detekt` (plus link and wrapper checks), so there is
no workflow file documenting a local test command either.

**How to apply:** to verify `buildSrc` changes in `config`, run
`./gradlew :buildSrc:test detekt`. In a CLI session, export `JAVA_HOME`
first (e.g. `export JAVA_HOME=$(/usr/libexec/java_home)`) — the Gradle
Doctor plugin fails the configuration phase without it.
