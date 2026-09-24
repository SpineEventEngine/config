---
name: plugin-publications-created-late
description: Gradle's `java-gradle-plugin` creates `pluginMaven` and `*PluginMarkerMaven` in its own afterEvaluate, after config's publication handlers run — reach publications with `configureEach`, never a `forEach` snapshot, and never rename a plugin marker.
metadata:
  type: project
  since: 2026-09-24
---

Gradle's `java-gradle-plugin` (also applied by `com.gradle.plugin-publish`) creates
the `pluginMaven` publication and a `<declaration>PluginMarkerMaven` publication per
declared plugin in an `afterEvaluate` action of its own. The `spinePublishing`
extension applies its publication handlers in `afterEvaluate` too, and for a module
listed in `modulesWithCustomPublishing`, that action is added by the root script, so
it runs first — before either publication exists. A marker is identified by the
plugin: its `groupId` is the plugin ID, its `artifactId` is `<id>.gradle.plugin`, and
its POM name and description come from the plugin declaration.

**Why:** `CustomPublicationHandler` iterated `publications.forEach { }`, a snapshot,
so every Spine plugin POM went out without `<licenses>`, `<scm>`, and
`<inceptionYear>` (SBOMs of consumers showed `NOASSERTION`). In the opposite order,
the same loop reached the markers and replaced their `groupId` with the project
group. Fixed on 2026-09-24 (task `plugin-pom-metadata`).

**How to apply:** in `buildSrc` publishing code (handlers, checks, reports), assume
a publication may appear after your `afterEvaluate` action: configure publications
with `withType<MavenPublication>().configureEach { }`, or read the container at
execution time. Give a marker only the project-wide POM attributes, never
coordinates or a description; recognize it with `MavenPublication.isPluginMarker`
(the `PluginMarkerMaven` name suffix, which `com.gradle.plugin-publish` relies on
as well). Regression guard: `CustomPublicationHandlerIgTest` (Gradle TestKit).
