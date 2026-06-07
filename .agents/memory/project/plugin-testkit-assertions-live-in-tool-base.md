---
name: plugin-testkit-assertions-live-in-tool-base
description: Generic Gradle-plugin functional-test assertions (testkit-truth) belong in tool-base/plugin-testlib, not per-plugin *-testlib modules.
metadata:
  type: project
  since: 2026-06-07
---

Generic Gradle-plugin functional-test assertions — the Google Truth Subjects for
Gradle TestKit `BuildResult`/`BuildTask` from `com.autonomousapps:testkit-truth` —
belong in **`tool-base`'s `plugin-testlib`**, the plugin-agnostic test-support
library every Spine Gradle plugin depends on. Not in `compiler-testlib` (which is
code-generation-specific), and not duplicated per plugin.

**Why:** `plugin-testlib` is the lowest common layer for "testing any Gradle
plugin". Wiring `testkit-truth` there as an `api` dependency (matching how
`gradleTestKit()` is already exposed) reaches compiler, core-jvm-compiler,
validation, mc-java, ProtoTap, etc. through a single declaration; codegen-specific
assertions then compose on top in `compiler-testlib`. Putting it in
`compiler-testlib` would be a layering inversion — a general testing concern owned
by a specialized module and invisible to non-codegen plugins.

**How to apply:** When adding shared Gradle-plugin test tooling, add it at
`tool-base/plugin-testlib` (as `api`); `config`'s role is only to pin the version
in `buildSrc` (`io/spine/dependency/test/TestKitTruth.kt`). This convention was
agreed 2026-06-07; the `tool-base` wiring may still be pending — confirm it is
present before relying on it.
