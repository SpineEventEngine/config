---
name: spine-compiler-replaces-protodata
description: ProtoData is archived; Spine Compiler (`compiler` repo, `io.spine.compiler`) supersedes it as the active code-generation plugin.
metadata:
  type: project
  since: 2026-06-07
---

ProtoData is archived. The active Protobuf-based code-generation toolchain for
the Spine SDK is **Spine Compiler**, developed in the `compiler` repository and
published as the `io.spine.compiler` Gradle plugin (`compiler-gradle-plugin`).

**Why:** ProtoData's lineage was folded into Spine Compiler; the ProtoData repo
no longer receives work. Treating it as current leads to recommendations and
dependency choices aimed at a dead project.

**How to apply:** When reasoning about the SDK's codegen plugins, prefer Spine
Compiler (`io.spine.compiler`). The `ProtoData` dependency object was removed
from `config`'s `buildSrc` (`io/spine/dependency/local/ProtoData.kt`) and its
entries dropped from `scripts/buildSrc-migration.kts`. Treat any remaining
ProtoData references in consumer repos or Git history as stale.
