---
name: pom-report-per-project-collectors
description: generatePom must never resolve other projects' configurations; capturing rootComponent Providers does not help — per-project collector tasks are the working design.
metadata:
  type: project
  since: 2026-08-13
---

The `generatePom` report collects the versions selected by dependency resolution
through per-project `collectResolvedVersions` tasks (`ResolvedVersions.kt` in
`buildSrc`), each resolving only the configurations of its own project and writing
a file the root task merges. Do not "simplify" this back to resolving from the
root task, and do not replace it with `incoming.resolutionResult.rootComponent`
`Provider`s captured at configuration time: a `Provider` resolves lazily, so its
first `.get()` from the root task still performs cross-project resolution and
fails Gradle's exclusive-lock check (`IllegalResolutionException`, hard error
since Gradle 9.x).

**Why:** the pre-2026-08 implementation resolved subproject configurations inside
the root task's `doLast`, swallowed the lock failure at `info`, and fell back to
declared versions — producing a `pom.xml` that differed between `gradle build`
and `gradle generatePom` and emitting false "several versions" warnings
(discovered in `compiler`, task `pom-report-cross-project-resolution`). Both the
direct and the captured-`Provider` variants were disproved empirically on Gradle 9.6.1.

**How to apply:** when changing the pom report or porting it, keep resolution
inside each project's own task. Keep the full `isCanBeResolved` configuration
scope: declared dependencies are collected from *all* configurations, so
narrowing resolution to the source-set classpaths reintroduces declared-version
fallbacks for plugin-owned configurations (e.g. `kotlin-build-tools-impl` on
`kotlinBuildToolsApiClasspath`) and with them the false warnings. Do not wrap
`resolutionResult.allComponents` in a defensive catch: reading the graph is
lenient (unresolvable modules, `failOnVersionConflict()` casualties, and
crashing resolution rules all become `UnresolvedDependencyResult` edges — probed
on Gradle 9.6.1), so a catch is dead code that can only hide real bugs. The
regression guard is `PomGeneratorIgTest` (Gradle TestKit).
