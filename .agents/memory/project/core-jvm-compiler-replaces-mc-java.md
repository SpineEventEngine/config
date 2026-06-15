---
name: core-jvm-compiler-replaces-mc-java
description: McJava (`mc-java`, `io.spine.mc-java`) was removed from config's buildSrc; CoreJvm Compiler (`io.spine.core-jvm`) is the active JVM code-generation plugin.
metadata:
  type: project
  since: 2026-06-15
---

The `McJava` dependency object (the `mc-java` plugin, `io.spine.mc-java`, repo
`SpineEventEngine/mc-java`) was removed from `config`'s `buildSrc`
(`io/spine/dependency/local/McJava.kt`), together with the `mcJava` shortcut in
`BuildExtensions.kt` and its entries in `scripts/buildSrc-migration.kts`. The
active JVM code-generation plugin is **CoreJvm Compiler** (`io.spine.core-jvm`,
artifact `core-jvm-plugins`), kept as the `CoreJvmCompiler` object and the
`coreJvmCompiler` shortcut.

**Why:** `config` no longer distributes the mc-java plugin object; CoreJvm
Compiler is its successor for JVM code generation. Treating mc-java as current
leads to recommendations and dependency choices aimed at a superseded tool.
Mirrors the [[spine-compiler-replaces-protodata]] succession.

**How to apply:** Prefer `CoreJvmCompiler` for JVM codegen. `migrate` now
deletes `McJava.kt` (and `ProtoData.kt`) from consumer `buildSrc` on each
`./config/pull`, since `cp -R` never removes files dropped from `config`; treat
any lingering `McJava` references in consumer repos or Git history as stale.
