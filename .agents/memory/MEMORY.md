# Team memory index

One line per memory. Scan at the start of every session.
See [README.md](README.md) for the format and routing rules.

## Feedback (validated patterns & corrections)

- [copilot-review-request](feedback/copilot-review-request.md) — GraphQL `requestReviews` with `botIds: ["BOT_kgDOCnlnWA"]`; REST endpoint silently no-ops on re-requests.

## Project (durable context & rationale)

- [spine-compiler-replaces-protodata](project/spine-compiler-replaces-protodata.md) — ProtoData is archived; Spine Compiler (`compiler` repo, `io.spine.compiler`) supersedes it as the active code-generation plugin.
- [core-jvm-compiler-replaces-mc-java](project/core-jvm-compiler-replaces-mc-java.md) — McJava (`mc-java`, `io.spine.mc-java`) was removed from config's buildSrc; CoreJvm Compiler (`io.spine.core-jvm`) is the active JVM code-generation plugin.
- [porting-buildsrc-from-consumer-repos](project/porting-buildsrc-from-consumer-repos.md) — To back-port buildSrc improvements from a consumer repo, diff after its last "Update `config`" commit; consumer-owned files never port.
- [config-build-verification](project/config-build-verification.md) — config root has no `build` task; verify buildSrc via `./gradlew :buildSrc:test detekt` with JAVA_HOME exported.
- [plugin-testkit-assertions-live-in-tool-base](project/plugin-testkit-assertions-live-in-tool-base.md) — Generic Gradle-plugin functional-test assertions (testkit-truth) belong in tool-base/plugin-testlib, not per-plugin `*-testlib` modules.
- [gradle-10-third-party-deprecations](project/gradle-10-third-party-deprecations.md) — Three deprecation nags under Gradle 9.6 come from Detekt, Kover, and Gradle Doctor, not our build logic; no released fixes yet — don't chase them in `buildSrc`.

## Reference (external systems)

- [cache-warm-window](reference/cache-warm-window.md) — How prompt cache entries are shared between sibling-repo sessions and how to maximise overlap.
- [anthropic-api-caching](reference/anthropic-api-caching.md) — Pattern and pricing for adding prompt caching to any direct Anthropic API call.
