---
slug: maven-central-throttling
branch: maven-central-throttling
owner: unassigned
status: draft
started: 2026-08-20
related-memories:
  - projectbuilder-in-memory-caches   # lives in `core-jvm-compiler`; promote to `.agents/shared`
---

## Goal

No Spine SDK build — local or CI — fails because Maven Central refuses to
serve us. Remote artifact traffic becomes *per machine, once* instead of
*per build run*, so that our aggregate footprint stays far below Sonatype's
consumption limits, and a throttled repository can no longer abort a build
whose artifacts are already on disk.

## Context

### What changed upstream

In May 2026 Sonatype tightened the consumption limits of Maven Central.
Traffic is now measured per organisation / network / egress address, and
the highest-volume consumers receive `429 Too Many Requests` — CI runner
pools also see `403 Forbidden`. Per the [Sonatype FAQ][faq]:

- During a block **every** request from that egress fails, including
  artifacts that would otherwise be served from the CDN edge cache.
- Repeated requests **extend** the block, up to 24 hours. Retrying harder
  is the one reaction guaranteed to make it worse.
- Ephemeral environments and tools that bypass dependency caches are named
  explicitly as the traffic pattern that triggers enforcement.

This is an industry-wide adjustment, not an outage and not a Spine problem:
Gradle, Trivy, Renovate, and TeamCity all carry public issues about it, and
Bitrise now runs dedicated Gradle mirrors for its customers.

### What we observed (2026-08-19/20, `core-jvm-compiler`)

- **Local.** `:gradle-plugin:test` failed 17 specs; every failure bottomed
  out in `429` for a single POM. The block covered the whole machine —
  unrelated builds included — and lasted about three hours.
- **CI.** `Build on Windows` failed 69 seconds in, resolving
  `kotlin-stdlib` for the `:buildSrc` classpath: `403 Forbidden` from
  `repo.maven.apache.org`, before a line of project code ran. A re-run on
  a different runner passed.
- `status.maven.org` reported all systems operational throughout — the
  block was ours, and the diagnosis has to start with that assumption.

### Why our builds are exposed

Four independent causes, each fixable on its own:

1. **`ProjectBuilder` cannot reuse the dependency cache — ever.**
   `ProjectBuilderImpl` builds its services from `TestGlobalScopeServices`,
   which overrides `createCacheFactory` to return
   `TestInMemoryCacheFactory`. Every "persistent" cache, the module
   metadata store included, is an empty in-memory map in each test JVM.
   `withGradleUserHomeDir(...)` does not change this — resolver ids, the
   user home, and the on-disk metadata all look correct while resolution
   still reports a miss. Any spec that resolves a real dependency graph
   therefore re-downloads it in full on **every run**.

2. **Production plugin code resolves at configuration time.**
   Paths such as `CoreJvmCompilerSettings.buildClasspath()` materialise a
   project's `compileClasspath` eagerly, which is what drags resolution
   into unit tests at all — and, for consumers, into IDE sync.

3. **A repository *error* aborts resolution.** Unlike a miss, an error
   from one repository fails the whole resolution even when an earlier
   repository already holds the module. One throttled `mavenCentral()`
   entry breaks builds that do not need Central. Several of our TestKit
   settings templates also omitted the Spine registry and only worked
   because `~/.m2` happened to be warm.

4. **CI has no consolidation point.** Every job on every runner is a cold
   cache facing Central directly, including the `buildSrc` bootstrap that
   precedes any project configuration.

### Reference implementation

Causes 1 and 3 are already solved in `core-jvm-compiler` (merged in
[core-jvm-compiler#111]). The pattern to generalise:

| File                                          | Role                                                                                                                      |
|-----------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `base/src/testFixtures/.../StubResolution.kt` | `forbidNetworkResolution()` puts a stub project in offline mode; `stubRepository` locates the local fixture repo          |
| `gradle-plugin/build.gradle.kts`              | `stubRepoDeps` configuration + `prepareStubRepo` task mirror the needed artifacts out of the enclosing build's warm cache |
| `gradle-plugin/src/test/.../StandardRepos.kt` | serves that directory first, with `metadataSources { artifact() }`                                                        |

The result was verified under an **active** Central block: the previously
failing specs passed with zero network access. Offline mode is the load-
bearing part — a gap fails loudly with "No cached version available for
offline mode" and names the artifact to add, instead of silently reaching
for the network.

## Plan

### Phase 1 — CI stops facing Central cold  (`config`)

- [ ] Audit the JVM workflows `config` distributes: confirm what
      `gradle/actions/setup-gradle` caches today and whether the
      `buildSrc` bootstrap classpath is covered by it.
- [ ] Evaluate seeding runners from a read-only dependency cache
      (`GRADLE_RO_DEP_CACHE`) — designed for exactly this
      "ephemeral environment reuses a warm cache" case.
- [ ] Make the re-run policy explicit in the workflow docs: a `403`/`429`
      from Central is an infrastructure signal; re-run the job once (a new
      runner means a new egress), never loop.
- [ ] (deferred 2026-08-20) Give the Windows CI job a cache to restore from.
      Diagnosed and costed; held pending evidence that it is needed.
      - `windows-latest` runs in exactly one workflow — `build-on-windows.yml`,
        triggered `on: pull_request` only. Every workflow that runs on the
        default branch (`build-on-ubuntu`, `publish`, `revalidate-versions`)
        is `ubuntu-latest`.
      - GitHub Actions caches are readable across branches only from the
        default branch, and `gradle/actions/setup-gradle` saves entries only
        from the default branch. With no Windows job on `master`, no shared
        Windows entry is ever written, so every Windows PR job resolves the
        `buildSrc` bootstrap from Central cold. That is precisely the
        2026-08-19 `403` on `kotlin-stdlib`, 69 seconds in.
      - Options weighed: (a) a scheduled Windows run on `master` that seeds the
        cache and doubles as the safety net; (b) `cache-read-only: false` on the
        PR job — same-PR reuse only, and per-PR entries risk LRU-evicting the
        Ubuntu entry from the 10 GB repo budget; (c) `GRADLE_RO_DEP_CACHE`,
        which still needs a seeded cache and therefore depends on (a);
        (d) running Windows CI less often.
      - **Decision.** Change nothing for now. `master` builds exist to publish;
        their non-publishing part is a safety net, and standing up a Windows
        run purely to seed a cache is not worth the spend after a single
        incident. Observe CI instead.
      - **Re-open when** another Central `403`/`429` fails a CI job. Option (a)
        is the first move; the `setup-gradle` read-only default is worth
        confirming from a real run's Gradle job summary at that point.

### Phase 2 — Shared offline stub fixture  (`tool-base`)

- [ ] Promote the `StubResolution` pattern into `plugin-testlib`, so that
      `compiler`, `validation`, and `core-jvm-compiler` share one
      implementation: offline enforcement, the stub-repository property,
      and a reusable `prepareStubRepo`-style task.
- [ ] Document the failure mode in the fixture's KDoc — the
      `TestInMemoryCacheFactory` fact is non-obvious and cost this session
      several hours to isolate.
- [ ] Migrate `core-jvm-compiler` onto the shared fixture and delete its
      local copy.

#### Considered and set aside: migrating `ProjectBuilder` tests to TestKit

Examined 2026-08-20 as an alternative to the stub fixture. TestKit would cure
the re-download pathology — it runs a real build with a real on-disk Gradle
user home, so `TestInMemoryCacheFactory` does not apply — but it does not fit
these specs:

- **CI traffic is not fixed, only moved.** `plugin-testlib` pins the TestKit
  dir to `<repo-root>/.gradle-test-kit` (`RootProject.testKitTempDir()`),
  which `setup-gradle` does not cache. On an ephemeral runner that cache is
  cold every run, so migrated specs would resume per-run full downloads from
  Central — through a different directory. Locally it also duplicates the
  whole graph once per checkout.
- **Assertion power is lost.** The affected specs are white-box: they assert
  on the in-process `Project` model (extensions, task wiring). TestKit is
  black-box across the Tooling API process boundary — task outcomes and
  output only — and each spec pays real-build startup.
- **No guarantee.** TestKit reduces traffic but leaves it unbounded and
  silent; the stub fixture is provably zero-network and fails loudly on
  a gap.

Gradle's docs draw the same line implicitly: `ProjectBuilder` is for
"lightweight, isolated" unit tests, TestKit for behavior "in a real build" —
and say nothing about resolution or caches in `ProjectBuilder` tests,
because in the intended taxonomy such tests never resolve a real graph.
The in-memory-cache behavior is documented nowhere public (searched
2026-08-20); this task's write-up appears to be the only record of it.
Per-test rule going forward: specs asserting on configuration stay on
`ProjectBuilder` + stub fixture; specs needing real build behavior belong
in TestKit anyway.

### Phase 3 — Repository ordering discipline  (`config`, then consumers)

- [ ] In the shared repository helpers (`standardToSpineSdk()`,
      `standardSpineSdkRepositories()`), fix the order as
      `mavenLocal()` → Spine registry → Central, so Central is consulted
      only for what genuinely lives there.
- [ ] Sweep TestKit settings/build templates across repos for
      `pluginManagement` blocks that omit the Spine registry — they resolve
      Spine artifacts through Central's 404 path today, and break outright
      when Central errors.

### Phase 4 — Less configuration-time resolution  (plugin repos)

- [ ] Convert eager `Configuration.getFiles()` calls in plugin production
      code into lazy `Provider`s resolved at execution time. This shrinks
      what tests can trigger, and doubles as configuration-cache
      readiness work.

### Phase 5 — Escalation, only if Phases 1–4 are not enough

- [ ] Stand up a caching repository manager (Nexus/Artifactory) as the
      single Central-facing consumer for the org, injected through an
      init script distributed by `config`. Sonatype names repository
      managers as the sanctioned consolidation point. Held as an
      infrastructure card because it adds operational surface the
      test-design fixes avoid.

### Guardrails

- [ ] Promote the `projectbuilder-in-memory-caches` memory from
      `core-jvm-compiler` into `.agents/shared`, so every repo's agents
      inherit it.
- [ ] Add a short "429 discipline" note to the shared guidelines: check
      `status.maven.org` first; never poll or retry during a block; a
      block is per-egress, so a green build elsewhere does not disprove it.

## Acceptance

1. `./gradlew build` in `core-jvm-compiler`, `compiler`, and `validation`
   completes with the network firewalled off after one warm-up run.
2. A test suite run twice performs **zero** remote requests on the second
   run (verify with `--offline`, or by watching the runner's egress).
3. CI green on a runner whose Gradle cache is cold, without Central
   serving the `buildSrc` bootstrap.

## Diagnostics — is it us?

```bash
curl -sS -D - -o /dev/null https://repo.maven.apache.org/maven2/\
com/google/auto/service/auto-service-annotations/1.1.1/\
auto-service-annotations-1.1.1.pom | head -3
```

`429`/`403` plus `server: cloudflare` and no `Retry-After` is a
consumption block. Cross-check `status.maven.org` to rule out an actual
incident. Then **stop making requests** from that machine and wait.

## Log

- 2026-08-20 — drafted from the incident during
  [core-jvm-compiler#111]; Phase 2's pattern is already implemented and
  proven there, the rest is untouched.
- 2026-08-20 — Phase 1: traced the CI half of the incident to the Windows
  job never running on the default branch, so no shared Gradle cache entry
  can exist for it. Windows workflow changes **deferred by decision** —
  watch CI and revisit on the next Central-caused CI failure.
- 2026-08-20 — Phase 2: considered migrating the affected `ProjectBuilder`
  specs to TestKit instead of the stub fixture; set aside (see the note under
  Phase 2). Confirmed upstream context: [gradle/gradle#37880] tracks the
  repository-error-aborts-resolution behavior (cause 3), and no public Gradle
  source documents the `ProjectBuilder` in-memory-cache behavior (cause 1).

[faq]: https://central.sonatype.org/faq/429-error/
[core-jvm-compiler#111]: https://github.com/SpineEventEngine/core-jvm-compiler/pull/111
[gradle/gradle#37880]: https://github.com/gradle/gradle/issues/37880
