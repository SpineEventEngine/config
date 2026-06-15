---
slug: pom-version-null
branch: which-that
owner: claude
status: in-review
started: 2026-06-15
---

## Goal

The aggregated dependency report (`docs/dependencies/pom.xml` in consumer repos)
must never emit `<version>null</version>`. A BOM-managed coordinate that carries
no explicit version must omit the `<version>` element entirely, producing valid
Maven XML.

## Context

Surfaced by a Copilot review on `SpineEventEngine/compiler` PR #69
(discussion `r3415608846`). Pre-existing on `master`; not introduced by that PR.
Affected artifacts observed in the generated report: `junit-platform-launcher`,
`io.grpc:grpc-protobuf`, `io.grpc:grpc-stub`, `jackson-dataformat-yaml`.

Root cause: `buildSrc/src/main/kotlin/io/spine/gradle/report/pom/DependencyWriter.kt`
wrote `"version" { xml.text(dependency.version) }` unconditionally. For a
version-less dependency `dependency.version` is `null`, and the
`MarkupBuilder.text` helper (`MarkupExtensions.kt`) does `value.toString()`,
turning `null` into the literal string `"null"`. The data layer already knew a
null version was possible — `deduplicate()` guards it with `it.version ?: ""` —
only the emission site did not.

Config-owned: the `report/pom/*.kt` files are byte-identical across all consumer
repos and cannot be durably fixed there. Fixed here; floats via the `config`
submodule + `pull`. Relates to the vendored-`buildSrc` audit follow-ups tracked
in config#691 (a 6th, self-contained item — fixed directly rather than deferred).

## Plan

- [x] Guard the version element in `DependencyWriter.writeXmlTo`: emit
      `<version>` only when `dependency.version != null`
      (`dependency.version?.let { … }`), mirroring the existing conditional for
      the optional `<scope>` element. Chose "omit the element" (option a) over
      "resolve the effective BOM-managed version" (option b): the generator
      walks declared deps across all configurations, including non-resolvable
      ones, so the resolved version is not readily available; an omitted
      `<version>` is also the correct Maven representation for a managed version.
- [x] Add `DependencyWriterSpec` regression test
      `omit the version of a dependency that declares none`: a version-less
      `io.grpc:grpc-stub` (BOM-managed) plus a versioned `spine-base` prove the
      literal `null` is gone while versioned entries still emit `<version>`.
- [x] Verify: `./gradlew :buildSrc:test detekt` green; regression proof
      (test fails without the fix); detekt clean.

## Log

- 2026-06-15 — drafted from the user's directive; plan approved.
- 2026-06-15 — implemented Changes 1–2. No version bump or `docs/dependencies`
      regeneration: `config` has no root `version.gradle.kts` and no such
      directory; both happen consumer-side on the next `pull` + build. Both
      source files already carry the 2026 copyright year.
- 2026-06-15 — verified: `./gradlew :buildSrc:test detekt` BUILD SUCCESSFUL
      (JDK 17). New `DependencyWriterSpec` test green (top-level suite 3 → 4
      tests, 0 failures); detekt clean. Regression proof: reverting the guard
      makes the new test fail at the `<version>null</version>` assertion
      (`DependencyWriterSpec.kt:268`); fix restored. Change set staged for the
      user to review and commit.
