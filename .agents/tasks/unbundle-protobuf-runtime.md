---
slug: unbundle-protobuf-runtime
branch: declare-protobuf-runtime
owner: claude
status: in-progress
started: 2026-10-16
---

## Goal

`config` provides one function, `Project.unbundleProtobufRuntime()`, which makes
a fat JAR declare the Protobuf runtime (`Protobuf.libs`) as a `runtime`
dependency instead of bundling it. tool-base and compiler then use it instead
of their own copies.

## Context

Fat JARs bundling the unrelocated Protobuf runtime pin the runtime of the build
that made them on the classpath of their consumers; newer generated code then
fails with `ProtobufRuntimeVersionException`. The runtime cannot be relocated,
as it depends on the names of its classes. tool-base#196 (`protobuf-setup-plugins`,
`jvm-tool-plugins`) and compiler#89 (`compiler-cli-all`) address this in their
open PRs with two copies of the same code: tool-base's repo-owned
`buildSrc/…/shade/ProtobufRuntime.kt` and a block of `cli/build.gradle.kts`.
They differ only in the name of the artifact-only publication whose POM
declares the runtime.

The function is opt-in rather than part of `uber-jar-module`: the IntelliJ
Platform uber JARs use that script too, bundle no Protobuf, and must not
declare it.

## Plan

- [x] `buildSrc/…/publish/ProtobufRuntime.kt`: `unbundleProtobufRuntime(publication)`.
- [x] `ProtobufRuntimeIgTest`: the JAR, its manifest, the POM, and the Gradle
  module metadata of the shadowed variant.
- [x] `./gradlew :buildSrc:test --tests '*ProtobufRuntimeIgTest' detekt`.
- [x] Reviews (`spine-code-review`, `kotlin-engineer`, `gradle-review`, `review-docs`)
  applied: one call per publication, Shadow's name constants, merging into
  an existing `dependencies` element, and tests for these.
- [x] Re-run the test on the reviewed version (9/9); mark the PR ready.
- [ ] After the merge: `./config/pull` in tool-base#196 and compiler#89, and
  replace their copies with the call.

## Log

- 2026-10-16 — implemented; `ProtobufRuntimeIgTest` 5/5, `detekt` clean.
- 2026-10-16 — review fixes pushed as draft config#796; `ProtobufRuntimeIgTest`
  9/9 and `detekt` clean on them; marked ready.
