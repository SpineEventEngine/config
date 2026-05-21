---
name: api-discovery
description: >
  Resolve the on-disk location of a Maven artifact's source code,
  so you can `Grep`/`Read` it directly instead of running `unzip`
  against JARs in the Gradle cache. Use this whenever you need to
  inspect a library's API or implementation — definitions of public
  types, method signatures, KDoc, internal helpers, etc.
---

# API discovery

Before reading library source code, run the `discover` script in
`.agents/scripts/api-discovery/`. It returns a path you can hand
straight to `Grep`, `Read`, or `Glob`.

Do **not** run `find ~/.gradle/caches` or `unzip` against cache JARs.
Each `unzip` decompresses the archive afresh — slow and token-heavy.

## How to call it

From the consumer repository root:

```bash
.agents/scripts/api-discovery/discover <query>
```

Where `<query>` is one of:

| Form | Example | Notes |
|---|---|---|
| `group:artifact:version` | `io.spine:spine-base:2.0.0-SNAPSHOT.390` | Most explicit |
| `group:artifact` | `io.spine:spine-base` | Version inferred from `buildSrc` |
| `artifact` | `spine-base` | Spine-only; group inferred from `buildSrc` |

The script writes the absolute resolved path to **stdout**, and any
freshness/diagnostic warnings to **stderr**. Always read stderr — a
silent stdout means clean resolution; a noisy stderr means caveats
the user should know about.

## Exit codes

| Code | Meaning | What you do |
|---|---|---|
| `0` | Path on stdout is usable. | Pass it to `Grep`/`Read`/`Glob`. If stderr is non-empty, surface the warning to the user before relying on the path. |
| `1` | Unresolvable (no sibling AND no JAR). | Report the failure. **Do not** fall back to `unzip ~/.gradle/caches/...`. |
| `10` | Cache directory not initialized. | Run the **bootstrap flow** below. |

## Bootstrap flow (exit 10)

On the first run in a fresh workstation the per-workstation cache
directory does not yet exist. The script exits `10` and names the
path it would create. Ask the user:

> The shared cache directory `<workspace-root>/.agents/caches/api-discovery/`
> does not exist yet. How would you like to proceed?
>
> 1. **Approve** — create the directory at the default path.
> 2. **Alternative root** — pick a different parent for the shared
>    `.agents/` directory (e.g., `~/SpineWorkspace`, `/srv/spine`).
> 3. **Non-cached** — skip the extraction cache. Sibling-first
>    discovery still works for Spine artifacts; non-Spine deps will
>    not be served by `api-discovery` in this repo.

Then act on the user's reply:

- **Approve** → `mkdir -p <workspace-root>/.agents/caches/api-discovery/sources`,
  then re-run the original `discover` query.
- **Alternative root** → ask for the absolute path `<alt-root>`, then:
  ```bash
  mkdir -p "<alt-root>/.agents/caches/api-discovery/sources"
  printf '%s\n' "<alt-root>" \
      > .agents/scripts/api-discovery/.workspace-root
  ```
  (the pointer file is gitignored). Then re-run `discover`.
- **Non-cached** → record the choice in **per-developer auto-memory**
  (project memory, type `feedback`), `name: api-discovery-cache-disabled`,
  describing the user's choice and giving the "How to apply" rule:
  *do not invoke `extract-sources` in this repo; for non-Spine deps
  fall back to other investigation tools*. Then proceed with
  sibling-first only.

Check that memory at session start. If it exists, skip cache-touching
paths entirely.

## Workflow

1. **Always** call `discover` before reading library source.
2. Use the returned path with `Grep`/`Read`/`Glob` directly. Do **not**
   `cd` into the directory — that adds path-prefix noise to tool calls
   and makes line citations harder to read.
3. If stderr contains `STALE: ...`, the sibling repo on disk is at a
   different version than what `buildSrc` declares. The sources are
   close to but not identical to the published artifact. Mention this
   when reporting findings.
4. If the script exits `1`, report the failure with its stderr
   message and stop. Do not try `unzip` as a workaround.

## Anti-patterns

Stop doing these — they are exactly what this skill exists to replace:

- `find ~/.gradle/caches/modules-2/files-2.1/ -name '*-sources.jar'`
- `unzip -l <jar>` to list classes
- `unzip -p <jar> path/in/jar` to read a file
- Any chain of `unzip` + `grep` against a Gradle-cache JAR

If you find yourself wanting to do those, run `discover` instead.

## Examples

**Spine artifact, fresh sibling on disk:**

```text
$ .agents/scripts/api-discovery/discover io.spine:spine-base
/Users/<you>/Projects/Spine/base-libraries/base
$ echo $?
0
```

Tool calls then look like:

- `Glob` pattern `**/*.kt`, path
  `/Users/<you>/Projects/Spine/base-libraries/base`.
- `Grep` pattern `class Identifier`, path the same.

**Spine artifact, stale sibling:**

```text
$ .agents/scripts/api-discovery/discover io.spine.tools:validation-java
api-discovery: STALE: validation-java declared 2.0.0-SNAPSHOT.433 in Validation.kt but sibling publishes 2.0.0-SNAPSHOT.440
api-discovery: sources at /Users/<you>/Projects/Spine/validation/java may differ from the published artifact
/Users/<you>/Projects/Spine/validation/java
```

Surface the `STALE` line to the user and proceed.

**Non-Spine artifact, first use (extraction):**

```text
$ .agents/scripts/api-discovery/discover com.google.guava:guava:33.5.0-jre
api-discovery: extracted com.google.guava:guava:33.5.0-jre -> .../guava/33.5.0-jre
/Users/<you>/Projects/Spine/.agents/caches/api-discovery/sources/com.google.guava/guava/33.5.0-jre
```

Second call returns the same path with no stderr (already cached).

**Unresolvable:**

```text
$ .agents/scripts/api-discovery/discover io.spine:does-not-exist:9.9.9
api-discovery: io.spine:does-not-exist:9.9.9 is not in the local Gradle cache
api-discovery: run './gradlew dependencies' (or rebuild) to fetch it, then retry
$ echo $?
1
```

Report the failure verbatim; do not try `unzip` as a workaround.

## Related

- Implementation reference: `.agents/scripts/api-discovery/README.md`.
- Manual cache pruning: `.agents/scripts/api-discovery/clean-cache`.
