---
name: plugin-testkit-assertions-live-in-tool-base
description: A convention for a future need — TestKit Truth assertions, if adopted, go in tool-base's plugin-testlib. Never wired; nothing uses them.
metadata:
  type: project
  since: 2026-06-07
---

If Spine's Gradle plugins adopt the Google Truth subjects for Gradle TestKit
`BuildResult`/`BuildTask` from `com.autonomousapps:gradle-testkit-truth`, add them
once, as an `api` dependency of **`tool-base`'s `plugin-testlib`**. Not in
`compiler-testlib`, which is specific to code generation, and not per plugin.

**Status (2026-10-14):** a convention for a future need, not a description of
current code. The wiring was never done: `plugin-testlib` exposes `gradleApi()`,
`gradleTestKit()`, `TestLib`, and JUnit Jupiter only. No Spine repository has
ever used TestKit Truth outside the `TestKitTruth` dependency object in
`buildSrc`, and no feature depends on it. The convention was recorded in passing
during unrelated work (config#682, removing ProtoData), not because a test needed
it. Its broken coordinates (`testkit-truth:1.20.0`, never published) went
unnoticed from 2023 until config#792 fixed them, because nothing resolved them.

**Why:** `plugin-testlib` is the lowest common layer for testing any Gradle
plugin, and it already exposes `gradleTestKit()` the same way. One declaration
there would reach the Compiler, CoreJvm Compiler, Validation, ProtoTap, and the
rest; assertions specific to code generation would build on it in
`compiler-testlib`. Owning a general testing concern in `compiler-testlib` would
invert the layering and hide it from plugins that do not generate code.

**How to apply:** Do not assume TestKit Truth is on any test classpath. When the
functional tests of a plugin first need these assertions, add `TestKitTruth.lib`
to `tool-base/plugin-testlib` as `api` instead of to that plugin's tests.
`config` only declares the coordinates and version in `buildSrc`
(`io/spine/dependency/test/TestKitTruth.kt`). Since nothing depends on the
convention yet, revisit it freely if a better home turns up.
