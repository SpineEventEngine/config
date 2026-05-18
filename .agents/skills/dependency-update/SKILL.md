---
name: dependency-update
description: >
  Walk every dependency declaration under
  `buildSrc/src/main/kotlin/io/spine/dependency/`, discover the latest released
  (non-snapshot, non-pre-release) version of each artifact from the URL hinted
  in its file (or from Maven Central if no URL is present), and update the
  `version` constant in place. Use when asked to refresh dependency versions,
  bump libraries, run a dependency audit, or "see what's stale".
---

# Update external dependencies

## Goal

Bring every dependency object under
`buildSrc/src/main/kotlin/io/spine/dependency/` to its latest **released**
version. Snapshots, release candidates, milestones, alpha/beta, EAP, and
`-dev` builds are **excluded**.

The authoritative version source for each artifact is the web page already
referenced in its file. When the file has no URL, Maven Central is the
fallback — and the discovered Maven Central URL is **added back to the file**
as a line comment so the next run has a hint.

## Inputs

- No arguments → scan all of `buildSrc/src/main/kotlin/io/spine/dependency/`.
- One or more paths or sub-package names (`lib`, `local`, `test`, `build`,
  `kotlinx`, `boms`) → restrict the scan to those.
- `--dry-run` → discover and report, but do not edit.

## Pre-flight

1. Run `git status --short`. If the worktree is dirty in files this skill will
   touch, stop and ask the user. Otherwise preserve unrelated changes.
2. Confirm `buildSrc/src/main/kotlin/io/spine/dependency/` exists.
3. Note the current branch — every change this skill makes is a candidate for
   a single `chore(deps): refresh external versions` commit at the end; the
   skill itself does NOT commit. The user decides.

## Per-file workflow

For each `*.kt` file in scope:

### 1. Parse the file

A dependency file declares one or more Kotlin `object`s, typically extending
`Dependency` or `DependencyWithBom`. The shape is:

    object Kotest {
        const val version = "6.1.11"
        const val group   = "io.kotest"
        const val assertions = "$group:kotest-assertions-core:$version"
        // …
    }

Extract:

- `objectName` — the outer `object` identifier.
- `version` — the literal version string. Some files have **multiple** version
  constants (`runtimeVersion`, `embeddedVersion`, `annotationsVersion`); treat
  each separately. The one driving the artifact is typically `override val
  version = …` or the `const val version = …` declared at the top.
- `group` — the Maven group.
- `module` artifact names — each `const val foo = "$group:foo:$version"` line
  contributes one artifact name. Use the first one to query Maven Central if
  needed.
- `versionUrl` — a URL hint. Look in this order:
  1. Line comments above the object: `^//\s*(https?://\S+)`.
  2. KDoc `@see <a href="(https?://[^"]+)">…</a>` inside the object's KDoc.
  3. Plain `@see https?://…` inside the KDoc.
  4. If none: leave `versionUrl` empty and use the Maven Central fallback below.

Skip files that contain only abstract base classes or helpers (`Dependency.kt`,
`DependencyWithBom.kt`, `BomsPlugin.kt`, anything without a concrete artifact
declaration).

### 2. Find the latest released version

The discovery rule depends on the URL shape.

**A. GitHub repository URL** (`https://github.com/<owner>/<repo>`):

- Resolve `https://github.com/<owner>/<repo>/releases/latest`. GitHub redirects
  to the latest non-prerelease tag. Read the redirected location or the
  rendered HTML to extract the tag.
- Tags often have a `v` prefix. Strip it.
- If the repo publishes per-component tags (e.g.
  `kotlinx-coroutines-1.10.2`), prefer the tag whose name matches the
  artifact's module identifier. Otherwise take the topmost release.

**B. Maven Central artifact URL**
(`https://search.maven.org/artifact/<group>/<artifact>` or
`https://repo1.maven.org/maven2/<groupPath>/<artifact>/`):

- Hit Maven Central's REST API:
  `https://search.maven.org/solrsearch/select?q=g:<group>+AND+a:<artifact>&rows=20&core=gav`
- Filter the `response.docs[].v` values by the pre-release rule (below).
- Take the highest by semver comparison.

**C. Project homepage** (e.g. `https://kotest.io/`, `https://junit.org/`,
`https://www.detekt.dev/`):

- Try to find a "latest release" or "download" link on the page. If the page
  is a thin landing page with no usable version data, fall through to D.

**D. No URL or unusable URL — Maven Central fallback**:

- Query Maven Central as in B using the file's `group` and the first module
  artifact name (the part after `$group:`).
- If the query returns results, **also insert a line comment**
  `// https://search.maven.org/artifact/<group>/<artifact>` above the object
  declaration (after any existing copyright header). This back-fills the URL
  hint for next time. Match the existing comment style (one line, no trailing
  punctuation).
- If Maven Central has no result, leave the file untouched and add it to the
  `Manual review` section of the final report.

### 3. Filter pre-releases

Reject any version string matching, case-insensitively:

    -SNAPSHOT$
    -RC[\d\-.]*$           e.g. -RC1, -RC.2
    -M\d+$                 e.g. -M3
    -alpha[\d\-.]*$
    -beta[\d\-.]*$
    -EAP[\d\-.]*$
    -pre[\d\-.]*$
    -dev[\d\-.]*$
    \.Beta\d*$             Spring-style trailing tokens
    \.Alpha\d*$
    \.RC\d*$
    \.M\d+$

Apply the regex to the **suffix after the numeric version**. The version
`2.0.0-SNAPSHOT.182` used by Spine itself is a snapshot and must be rejected
as a target — but the same file's `version.gradle.kts` may legitimately hold
such a value. This skill only edits dependency files, never `version.gradle.kts`
(that belongs to the `bump-version` skill).

### 4. Compare versions

Use semver comparison:

- Split on `.` and `-`.
- Numeric segments compare numerically; non-numeric segments compare
  lexicographically.
- A version without any pre-release suffix is greater than one with the same
  numeric prefix but a pre-release suffix.

Only update when `latest > current`. Equal or lower → no change.

### 5. Apply the edit

- Replace the `version` literal with the new value. Use a precise replacement
  anchored on the full line (`const val version = "<old>"` →
  `const val version = "<new>"`). Do not blindly replace the version string,
  because the same string can appear in module URLs constructed via
  interpolation (`"$group:…:$version"`) — those will pick up the new value
  automatically.
- If the file uses a renamed version constant (`runtimeVersion`,
  `compilerVersion`, etc.) that feeds `override val version = compilerVersion`,
  update the **source** constant, not the alias.
- For `DependencyWithBom` objects, verify the `bom` line still resolves
  correctly. The conventional shape is
  `override val bom = "$group:<artifact>-bom:$version"`, in which case no
  separate edit is needed. If the BOM version is hard-coded, update it too.
- Preserve indentation, comment style, and surrounding blank lines exactly.

### 6. Watch for `local/` artifacts

`local/` holds Spine SDK dependencies (Base, CoreJvm, ModelCompiler, …) that
are published from sibling Spine repos. Apply the same rule (latest released,
no snapshots), but **flag every `local/` update in the report** so the user
can decide whether to bump the SDK in lockstep with the rest of the project.
Spine SDK artifacts often need to move together; one-off bumps can cause
runtime ABI mismatches.

## Report

When the run completes, emit a Markdown report with these sections:

- **Updated** — table of `file | objectName | old → new | source URL`.
- **Already current** — file/object pairs whose version was already the
  newest non-prerelease.
- **Skipped (no URL, Maven Central empty)** — manual review needed.
- **Filtered pre-releases** — newer versions found but rejected because they
  were RC/SNAPSHOT/alpha/etc. Useful signal.
- **`local/` bumps to confirm** — every `local/` change called out
  separately.

End with the suggested next steps:

1. Review the diff (`git diff buildSrc/src/main/kotlin/io/spine/dependency/`).
2. Run `./gradlew build` (or `./gradlew clean build` if `.proto` files
   participate).
3. If any `local/` artifacts moved, run `./gradlew buildDependants` (the
   `ConfigTester` task) to confirm downstream repos still build.
4. Commit. The conventional message is
   `chore(deps): refresh external versions` (or a more specific subject if
   the diff is small).

## Safety

- Do not commit. Do not push. Editing files is the limit of this skill's
  authority.
- Never edit `version.gradle.kts` — that's the `bump-version` skill's
  responsibility.
- Never auto-resolve a Maven Central query that returns multiple matching
  artifacts with different groups (e.g. a library that exists under both
  `io.netty` and `io.netty.incubator`). Ask the user.
- If a discovered "latest" version is more than one **major** ahead of the
  current value (e.g. `1.x` → `3.x`), flag it as a major bump in the report
  and apply the edit only if the user confirms, or only when running
  non-interactively with `--include-majors`. Major bumps frequently break
  ABI.

## Failure modes to expect

- **GitHub rate limit** on the unauthenticated REST API. The `/releases/latest`
  HTML page does not require auth and is the preferred fallback.
- **Per-component tags** in a monorepo. Match by artifact name, don't take the
  topmost tag blindly.
- **Repositories that publish to JCenter only** — JCenter is sunset; if Maven
  Central is empty, the dependency may need migration. Flag it.
- **Vendor-specific version schemes** (e.g. dates: `2025.10.01`) — the
  semver comparator above will still order these correctly; just don't
  mis-classify them as pre-releases.
