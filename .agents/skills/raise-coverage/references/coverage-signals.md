# Coverage signals — localization & verification

Mechanical reference for the `raise-coverage` skill. The `SKILL.md` says *what to
do*; this file says *how to read the numbers*.

Coverage is computed by the **JaCoCo engine** in every Spine repo, but it is
exposed through one of **two frontends**. Detect which the target repo uses, run
the matching report task, then parse the XML — which is **JaCoCo-format either
way**, so all the parsing rules below are shared.

## Two coverage frontends

### Kover — the consumer-repo norm

Repos that consume `config`'s `buildSrc` apply **Kover** per module via the
distributed `module.gradle.kts`:

```kotlin
plugins { /* … */ id("org.jetbrains.kotlinx.kover") }
kover {
    useJacoco(version = Jacoco.version)   // compute coverage with the JaCoCo engine
    reports.total.xml.onCheck = true       // emit XML on `check`
}
```

`useJacoco(...)` is a **Kover** DSL call — the tasks are Kover's, but the engine
and the XML format are JaCoCo's.

- Per-module report task: `:<module>:koverXmlReport`
- XML path: `<module>/build/reports/kover/report.xml`
  (JVM-only variant: `koverXmlReportJvm` → `reportJvm.xml`)

### Raw JaCoCo — the `config` repo itself

`config` applies the `jacoco` plugin plus
`io.spine.gradle.report.coverage.JacocoConfig`:

- Per-module report task: `:<module>:jacocoTestReport`
  → `<module>/build/reports/jacoco/test/jacocoTestReport.xml`
- Aggregate root task: `jacocoRootReport`
  → `build/reports/jacoco/jacocoRootReport/jacocoRootReport.xml`
- `JacocoConfig.applyTo` throws on a single-module project.

### Detecting which frontend a repo uses

```bash
./gradlew :<module>:tasks --all --console=plain | grep -iE 'koverXmlReport|jacocoTestReport'
```

Prefer **`koverXmlReport`** when present (consumer repos); otherwise use
**`jacocoTestReport`** / `jacocoRootReport` (the `config` repo). If unsure of the
output path, find it (both reports carry the JaCoCo `report.dtd`):

```bash
find <module>/build -name '*.xml' \( -path '*kover*' -o -path '*jacoco*' \)
```

## Generating a report

```bash
# Kover (consumer repos) — runs the module tests, then writes report.xml
./gradlew :<module>:koverXmlReport

# Raw JaCoCo (the config repo)
./gradlew :<module>:test :<module>:jacocoTestReport     # one module
./gradlew jacocoRootReport                              # whole repo (slow)
```

## Reading the XML

Both frontends emit the same structure: `report > package > class > method`, each
with `<counter>` elements, plus `<sourcefile>` elements carrying per-line data.

- `<counter type="INSTRUCTION|BRANCH|LINE|METHOD|CLASS|COMPLEXITY"
  missed="N" covered="M"/>` — totals at each level.
- `<line nr="L" mi="missedInstr" ci="coveredInstr" mb="missedBranches"
  cb="coveredBranches"/>` — per source line (inside `<sourcefile>`).

### Gap rules

- **Uncovered line**: `ci == 0` (and `mi > 0`).
- **Partially covered branch**: `mb > 0` (regardless of `cb`).
- **High-value targets**: methods whose `BRANCH` (or `LINE`) counter has
  `missed > 0` — enumerate these first in the `SKILL.md` step-4 list.

### Non-actionable gaps (recognize and skip)

Some lines show as uncovered but cannot gain coverage from *any* test — do not
propose tests for them; report them as non-actionable:

- **Kotlin `inline` / `inline reified` functions.** The compiler inlines the body
  into every call site, so the engine credits the caller, not the definition. The
  definition lines stay `ci=0` even when fully exercised. (Verified on
  `base-libraries`: `parse<T>(...)` reified overloads remained `ci=0` after a
  passing round-trip test.)
- **Unreachable guards.** `require` / `check` / `error` branches the public API
  cannot trigger (e.g. an invariant guaranteed by construction) — the gap is real
  but unclosable from outside.

### Extracting gaps for a class

The XML carries a `DOCTYPE` pointing at a public DTD, so always pass `--nonet` to
`xmllint` (or use the Python recipe) — parsing must never reach the network. The
report has no XML namespace, so the XPath is plain:

```bash
xmllint --nonet \
  --xpath '//class[@name="io/spine/.../MyType"]//line[@ci="0" or @mb > 0]' \
  <module>/build/reports/kover/report.xml          # or the jacoco path
```

Python (robust for large reports; reads both class/method counters and
sourcefile lines):

```python
import xml.etree.ElementTree as ET
root = ET.parse("report.xml").getroot()
for pkg in root.findall("package"):
    for sf in pkg.findall("sourcefile"):
        gaps = [l.get("nr") for l in sf.findall("line")
                if l.get("ci") == "0" or int(l.get("mb", "0")) > 0]
        if gaps:
            print(pkg.get("name"), sf.get("name"), gaps)
```

## What is in scope

Only human-written `src/main` code. Two filters already exclude the rest — honor
both, and never count an excluded file as a gap:

- **`JacocoConfig` human-produced filter** (config repo) — any file whose absolute
  path contains `generated` is treated as generated and dropped (covers Protobuf
  and `protoc-gen-kotlin` output). Anonymous/nested classes fold into their
  top-level class.
- **`.codecov.yml`** — `ignore` removes `**/generated/**`, `**/examples/**`,
  `**/test/**`; coverage status applies only to `src/main/**`.

## KMP / Kotlin-JVM modules

For Kotlin-JVM and KMP modules, `koverXmlReport` already targets the JVM
compilation; `koverXmlReportJvm` is the JVM-only variant. Add tests under the
module's test source set (`src/test`, or `src/jvmTest` / `src/commonTest` for
KMP) to match. In the raw-JaCoCo `config` repo, the `jacoco-kotlin-jvm` /
`jacoco-kmm-jvm` script plugins keep exec data at `build/jacoco/jvmTest.exec` and
classes under `build/classes/kotlin/jvm/`.

## Verification (SKILL.md step 6)

After generating tests, re-run the **same** report task and re-parse the XML for
the targeted class: the previously listed `nr` values should no longer be gaps,
and the method/class `BRANCH` + `LINE` counters should show `missed` reduced.
Cross-check the module total against the relevant `.codecov.yml` `project` target
so nothing regresses.

---

## Appendix — future: Codecov triage tier (deferred)

v1 is local-report-only. A later iteration may add a Codecov triage tier to pick
targets across repos without a local build. If added:

- Base `https://api.codecov.io/api/v2`; `service = github`,
  `owner = SpineEventEngine`, `repo = <name>`; auth header
  `Authorization: Bearer $CODECOV_API_TOKEN`.
- Useful endpoints: per-file `totals`, line-by-line `report`, single
  `file_report`, and `commits` (for trend). Filters: `path`, `flag`,
  `component_id`.
- Read the per-line hit/miss/partial encoding from live JSON once — do not
  hardcode it; it is easy to get backwards.
- Always degrade gracefully to the local report (above) when the token is absent.

Until that lands, do everything from the local Kover/JaCoCo report.
