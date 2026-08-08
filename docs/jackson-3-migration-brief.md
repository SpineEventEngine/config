# Jackson 2.22.x → 3.2.x Migration Brief

**Audience:** Claude Code working inside SpineEventEngine repositories. **Status of this document:**
authoritative task brief for the migration. Read fully before editing any file. **Compiled:**
2026-08-07.

---

## 1. Target state

| Item          | Value                                                                                                                                                                                                                      |
|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Current       | `com.fasterxml.jackson:jackson-bom:2.22.1`                                                                                                                                                                                 |
| Target        | `tools.jackson:jackson-bom:3.2.1` (latest published 3.2.x as of 2026-07-10)                                                                                                                                                |
| Kotlin module | `tools.jackson.module:jackson-module-kotlin` (version managed by BOM)                                                                                                                                                      |
| Java baseline | 17 (3.x requires it; 2.x required 8)                                                                                                                                                                                       |
| LTS note      | 3.1.x is the current designated LTS; 3.0.x was transitional. **We are deliberately targeting 3.2.x now and will move to the next LTS after 3.2.x when it is published.** Do not "helpfully" downgrade the target to 3.1.x. |

Before pinning, verify that `3.2.1` is still the newest `3.2.x`:

```
https://repo1.maven.org/maven2/tools/jackson/jackson-bom/
```

If a newer `3.2.z` exists, use it and note the change in the final report. Do **not** jump to
`3.3.x` or any other minor line without asking.

---

## 2. Rules of engagement

1. **Do not change wire format silently.** Several 3.x defaults alter serialized output (property
   order, enum representation, date representation). Any such change must be either explicitly
   preserved or explicitly approved. See §8.
2. **Do not touch `com.fasterxml.jackson.annotation`.** That package and group ID stay on 2.x by
   design in Jackson 3. Blanket search-and-replace across the whole tree is a bug, not a shortcut.
3. **One repository per change set.** Jackson 2.x and 3.x coexist on a classpath (different group
   IDs and packages), so migration is incremental. Do not attempt an org-wide big bang.
4. **Prefer failing loudly over restoring 2.x behaviour by reflex.** When a default changed, first
   understand whether the new default is actually better for us; only then decide to restore.
5. **Ask before deleting a test.** A test that starts failing after this migration is evidence, not
   noise.
6. Keep commits reviewable: mechanical renames in their own commit, behavioural decisions in
   separate, individually justified commits.

---

## 3. Phase 0 — inventory (do this before editing)

Produce a written inventory and show it before making changes:

1. Every module that declares a Jackson dependency (direct or via version catalog).
2. Every third-party dependency that pulls Jackson transitively, and which major version it wants.
   Use `./gradlew :module:dependencies --configuration runtimeClasspath` and grep for both
   `com.fasterxml.jackson` and `tools.jackson`.
3. **Persistence and API boundaries.** Every place Jackson output is written somewhere durable or
   crosses a public contract: stored snapshots, projections, archived event payloads, message queue
   bodies, HTTP response bodies, config files on disk, golden/approval test fixtures. This list
   drives §8 and is the highest-risk part of the migration.
4. Every custom `JsonSerializer` / `JsonDeserializer` / `Module` / `BeanSerializerModifier`
   implementation.
5. Every `catch` clause that catches `IOException` around a Jackson call (see §9 — this is the
   Kotlin-specific silent hazard).

---

## 4. Phase 1 — build files

### Gradle version catalog / dependency config

```kotlin
// platform
implementation(platform("tools.jackson:jackson-bom:3.2.1"))

// modules, versions omitted — BOM manages them
implementation("tools.jackson.core:jackson-databind")
implementation("tools.jackson.module:jackson-module-kotlin")
implementation("tools.jackson.dataformat:jackson-dataformat-yaml")
```

### Rules

- Replace group ID `com.fasterxml.jackson.*` → `tools.jackson.*` for all artifacts **except**
  anything under `com.fasterxml.jackson.core:jackson-annotations`.
- **Remove these dependencies entirely** — they are now built into `jackson-databind` in 3.x and
  must not be declared or registered:
    - `jackson-module-parameter-names`
    - `jackson-datatype-jdk8`
    - `jackson-datatype-jsr310`
- Do **not** pin `jackson-annotations` manually. Let the BOM decide; 3.x deliberately consumes a 2.x
  annotations artifact. Report which version the BOM resolves to.
- `jackson-module-jsonSchema` was dropped in 3.0 but restored in 3.1. Use
  `tools.jackson.module:jackson-module-jsonSchema`, or the `-jakarta` variant; the BOM manages
  both.
- `jackson-datatype-hibernate` is available: the BOM manages `hibernate4`, `hibernate5`,
  `hibernate5-jakarta`, `hibernate6`, and `hibernate7` under `tools.jackson.datatype`.
- Ensure the Java toolchain / `jvmTarget` is 17 or above in every affected module, and check that no
  downstream consumer is still on 11.

---

## 5. Phase 2 — mechanical package rename

The base transformation across `.java` and `.kt` sources:

```
com.fasterxml.jackson.  →  tools.jackson.
```

**Exclusion (must be honoured):** `com.fasterxml.jackson.annotation.*` stays exactly as is.
`@JsonProperty`, `@JsonIgnore`, `@JsonCreator`, `@JsonTypeInfo`, `@JsonSubTypes`, `@JsonInclude`,
`@JsonAutoDetect`, `@JsonPropertyOrder`, `@JsonTypeName`, `@JsonView`, `@JsonAnyGetter/Setter`,
`@JsonValue`, `@JsonAlias` — all unchanged.

**Exception to the exclusion:** annotations that live in *databind* (not the annotations artifact)
**do** move:

| 2.x                                                         | 3.x                                                 |
|-------------------------------------------------------------|-----------------------------------------------------|
| `com.fasterxml.jackson.databind.annotation.JsonSerialize`   | `tools.jackson.databind.annotation.JsonSerialize`   |
| `com.fasterxml.jackson.databind.annotation.JsonDeserialize` | `tools.jackson.databind.annotation.JsonDeserialize` |
| `com.fasterxml.jackson.databind.annotation.JsonNaming`      | `tools.jackson.databind.annotation.JsonNaming`      |
| `com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder` | `tools.jackson.databind.annotation.JsonPOJOBuilder` |
| XML/format-specific annotations (e.g. `JacksonXmlProperty`) | move under `tools.jackson.dataformat.xml.*`         |

A safe ordering for the sed pass:

1. Rewrite `com.fasterxml.jackson.databind.` → `tools.jackson.databind.` (covers the databind
   annotations correctly).
2. Rewrite `com.fasterxml.jackson.core.`, `.dataformat.`, `.datatype.`, `.module.` →
   `tools.jackson.…`.
3. Do **not** run a generic `com.fasterxml.jackson.` → `tools.jackson.` pass; it will corrupt
   annotation imports.
4. Grep afterwards for any remaining `com.fasterxml.jackson` and confirm every hit is
   `com.fasterxml.jackson.annotation`.

### On tooling

OpenRewrite publishes `org.openrewrite.java.jackson.UpgradeJackson_2_3`, which handles package
changes, dependency updates, and class/method renames. **It is a Java recipe.** Our sources are
predominantly Kotlin, so treat it as useful for Java modules only and do the Kotlin work explicitly.
Do not assume a recipe run means the job is done — always verify with a grep pass and a compile.

---

## 6. Phase 3 — API renames

### `jackson-core`

| 2.x                                                  | 3.x                                                   |
|------------------------------------------------------|-------------------------------------------------------|
| `JsonProcessingException`                            | `JacksonException` (root; now unchecked)              |
| `JsonParseException`                                 | `StreamReadException`                                 |
| `JsonGenerationException`                            | `StreamWriteException`                                |
| `JsonEOFException`                                   | `UnexpectedEndOfInputException`                       |
| `JsonStreamContext`                                  | `TokenStreamContext`                                  |
| `JsonLocation`                                       | `TokenStreamLocation`                                 |
| `ObjectCodec`                                        | split into `ObjectReadContext` / `ObjectWriteContext` |
| `JsonToken.FIELD_NAME`                               | `JsonToken.PROPERTY_NAME`                             |
| `JsonParser.getText()` / `getTextCharacters()`       | `getString()` / `getStringCharacters()`               |
| `JsonParser.getCurrentName()`                        | `currentName()`                                       |
| `JsonParser.getCurrentValue()` / `setCurrentValue()` | `currentValue()` / `assignCurrentValue()`             |
| `JsonParser.getCurrentLocation()`                    | `currentLocation()`                                   |
| `JsonParser.getTokenLocation()`                      | `currentTokenLocation()`                              |
| `JsonGenerator.writeObject()`                        | `writePOJO()`                                         |
| `JsonGenerator`/`JsonParser` `getCodec()`            | `objectWriteContext()` / `objectReadContext()`        |
| `*Field*` in streaming method names                  | `*Property*`                                          |

`JsonFactory` API was extracted as `TokenStreamFactory`; the implementation moved under
`tools.jackson.core.json`.

### Feature enum splits

| 2.x                                                | 3.x                                                                 |
|----------------------------------------------------|---------------------------------------------------------------------|
| `JsonParser.Feature`                               | `StreamReadFeature` (general) + `JsonReadFeature` (JSON-specific)   |
| `JsonGenerator.Feature`                            | `StreamWriteFeature` (general) + `JsonWriteFeature` (JSON-specific) |
| `YAMLParser.Feature` / `YAMLGenerator.Feature`     | `YAMLReadFeature` / `YAMLWriteFeature`                              |
| `CsvParser.Feature` / `CsvGenerator.Feature`       | `CsvReadFeature` / `CsvWriteFeature`                                |
| `FromXmlParser.Feature` / `ToXmlGenerator.Feature` | `XmlReadFeature` / `XmlWriteFeature`                                |

(Same pattern for Avro, CBOR, Smile, Ion.)

### `jackson-databind`

| 2.x                                                   | 3.x                                                                                           |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| `JsonSerializer` / `JsonDeserializer`                 | `ValueSerializer` / `ValueDeserializer`                                                       |
| `SerializerProvider`                                  | `SerializationContext`                                                                        |
| `JsonMappingException`                                | `DatabindException`                                                                           |
| `Module`                                              | `JacksonModule` (avoids clash with JDK `Module`)                                              |
| `JsonSerializable`                                    | `JacksonSerializable`                                                                         |
| `TextNode`                                            | `StringNode`                                                                                  |
| `BeanSerializerModifier` / `BeanDeserializerModifier` | `ValueSerializerModifier` / `ValueDeserializerModifier`                                       |
| `ContainerSerializer`                                 | `StdContainerSerializer`                                                                      |
| `ContextualSerializer` / `ContextualDeserializer`     | **removed** — `createContextual()` is now a method on `ValueSerializer` / `ValueDeserializer` |
| `ResolvableSerializer` / `ResolvableDeserializer`     | **removed** — `resolve()` is now a method on the base classes                                 |
| `ObjectMapper.getRegisteredModuleIds()`               | `registeredModules()` (return type also changed)                                              |
| `ObjectMapper.DefaultTyping`                          | moved to `tools.jackson.databind.DefaultTyping`                                               |

Many `JsonNode` methods were renamed and number accessors extended (JSTEP-3). Where a `JsonNode`
accessor previously returned `null` or a default on failure, 3.x may now throw.

---

## 7. Phase 4 — mapper construction (immutability)

`ObjectMapper` and `JsonFactory`/`TokenStreamFactory` and all subtypes are **fully immutable** in
3.x. No setters. All configuration goes through builders.

### Kotlin idiom

```kotlin
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule

val mapper = jsonMapper {
    addModule(kotlinModule())
    enable(SerializationFeature.INDENT_OUTPUT)
}
```

or `jacksonObjectMapper()` from `tools.jackson.module.kotlin` for the default-configured case.

### Translation table

| 2.x                                                                 | 3.x                                                                                                                         |
|---------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `mapper.setDateFormat(fmt)`                                         | `.defaultDateFormat(fmt)` on the builder                                                                                    |
| `mapper.setTimeZone(tz)`                                            | `.defaultTimeZone(tz)` — **note: default is now UTC, not the JVM default zone**                                             |
| `mapper.setSerializationInclusion(NON_NULL)`                        | `.changeDefaultPropertyInclusion { it.withValueInclusion(NON_NULL) }` (and `withContentInclusion` if needed)                |
| `mapper.disable(MapperFeature.AUTO_DETECT_FIELDS)`                  | `.changeDefaultVisibility { it.withFieldVisibility(Visibility.NONE) }` — the `AUTO_DETECT_*` MapperFeatures are removed     |
| `mapper.copy()`                                                     | **removed** — cache a `Builder`, or `mapper.rebuild().build()`                                                              |
| `mapper.setConfig(config.withView(...))`                            | `.defaultSerializationView(...)` / `.defaultDeserializationView(...)` (3.1+)                                                |
| `new ObjectMapper(new YAMLFactory())`                               | **illegal** — use `YAMLMapper()` / `YAMLMapper.builder()`; same for XML, CSV, Smile, CBOR                                   |
| `activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ...)` | builder method + a `BasicPolymorphicTypeValidator.builder()` validator — `LaissezFaireSubTypeValidator` is no longer public |

Reconfiguring an existing instance: `mapper.rebuild().enable(...).build()`.

New in 3.x: `JsonMapper.shared()` (and `YAMLMapper.shared()`, etc.) gives a safe global
default-configured instance. Consider it where we currently hold a private default-configured
singleton, but **not** where our mapper carries custom configuration.

Prefer passing `JsonMapper` rather than `ObjectMapper` in signatures, unless format-agnostic
handling is genuinely required.

---

## 8. Phase 5 — changed defaults (highest risk)

These change runtime behaviour without any compile error. Cross-reference each against the Phase 0
persistence inventory.

| Setting                                                                       | 2.x | 3.x         | Consequence                                                                  |
|-------------------------------------------------------------------------------|-----|-------------|------------------------------------------------------------------------------|
| `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY`                                | off | **on**      | property order in output changes                                             |
| `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` (moved to `DateTimeFeature`) | on  | **off**     | dates serialize as ISO strings, not numbers                                  |
| `SerializationFeature.WRITE_ENUMS_USING_TO_STRING` (moved to `EnumFeature`)   | off | **on**      | **enum wire representation changes**                                         |
| `DeserializationFeature.READ_ENUMS_USING_TO_STRING` (moved to `EnumFeature`)  | off | **on**      | enum parsing changes                                                         |
| `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`                           | on  | **off**     | typos/renames stop failing — can mask real bugs                              |
| `DeserializationFeature.FAIL_ON_TRAILING_TOKENS`                              | off | **on**      | stricter parsing; small throughput cost                                      |
| `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES`                          | off | **on**      | `@JsonCreator` with primitive params can start failing on missing values     |
| `MapperFeature.DEFAULT_VIEW_INCLUSION`                                        | on  | **off**     | changes `@JsonView` behaviour                                                |
| `MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS`                                | on  | **off**     | `final` fields are no longer reflectively overwritten                        |
| `MapperFeature.USE_GETTERS_AS_SETTERS`                                        | on  | **off**     | collection/map properties without a setter or creator stop populating        |
| `MapperFeature.FIX_FIELD_NAME_UPPER_CASE_PREFIX`                              | off | **on**      | property-name deduction changes for leading-uppercase / `iPhone`-style names |
| `MapperFeature.SORT_CREATOR_PROPERTIES_BY_DECLARATION_ORDER`                  | off | **removed** | behaves as if enabled                                                        |
| `MapperFeature.USE_STD_BEAN_NAMING`                                           | —   | **removed** | behaves as if enabled                                                        |
| `String.intern()` on parsed property names                                    | on  | **off**     | improvement for high-cardinality keys; configurable                          |

### Prescribed strategy

1. First pass: build the mapper with `JsonMapper.builderWithJackson2Defaults()` to get the codebase
   green with minimal behavioural drift. This restores *some* — **not all** — legacy defaults; it is
   a scaffold, not a destination.
2. Then remove it and re-enable settings one at a time, **one commit per setting**, each with a
   short rationale in the commit message and the affected tests updated in the same commit.
3. For anything on the persistence inventory, do not rely on unit tests alone: add round-trip tests
   that read a fixture serialized by 2.22.1 and assert it still deserializes correctly under 3.2.x.
4. Report, explicitly, any setting where we chose the new default and the wire format therefore
   changed.

---

## 9. Phase 6 — exception handling (Kotlin-specific hazard)

In 3.x, `JacksonException` extends `RuntimeException` and **no longer extends `IOException`**.

In Java this produces compile errors on now-unreachable catch clauses. **In Kotlin it produces
nothing.** Code like:

```kotlin
try {
    mapper.readValue<Foo>(json)
} catch (e: IOException) {          // silently stops catching Jackson failures
    handle(e)
}
```

still compiles and now lets parse failures propagate. Same problem with
`runCatching {}.recoverIf<IOException>` patterns and with `@Throws(IOException::class)` on Kotlin
functions exposed to Java callers.

**Required action:** grep every Kotlin (and Java) `catch` / `@Throws` mentioning `IOException`
within Jackson call sites and rewrite to `JacksonException` (or catch both during a transition).
This is a mandatory checklist item — report the full list of sites found and how each was handled.

`throws IOException` declarations that existed *only* because of Jackson can be dropped in Java
sources.

---

## 10. Phase 7 — Kotlin module specifics

Coordinates: `tools.jackson.module:jackson-module-kotlin`, package `tools.jackson.module.kotlin`.

Behavioural changes to check:

- **`StrictNullChecks` is enabled by default in 3.x** (was off in 2.x). JSON containing `null`
  inside `List<String>`, `Map<String, String>`, arrays, or other generic type arguments will now
  throw instead of quietly producing a value with nulls in a non-null-typed collection. This is
  correct behaviour but it *will* surface latent data problems. Expect failures; treat them as
  findings, not as a reason to disable the feature without discussion.
- **`SingletonSupport` is enabled by default in 3.x.** Kotlin `object` declarations deserialize to
  the singleton instance rather than a new instance. Check any identity comparisons.
- `NewStrictNullChecks` was removed and folded into `StrictNullChecks` as of 3.2.0 — do not
  reference the old option.
- **`isRequired` no longer overrides `JacksonAnnotationIntrospector`.**
  `@JsonProperty(required = true)` on a nullable parameter is now treated as required.
- The module ships a real `module-info.java` (JSTEP-11). If any of our modules are on the module
  path or run split-package checks, verify JPMS resolution.
- Kotlin version floor: 3.2.x is fine on current Kotlin; note that 3.3.0 raises the minimum to
  Kotlin 2.2. Record our Kotlin version in the report so the next LTS bump is a known quantity.

Useful 3.x feature for our sealed hierarchies: subtypes of a `@JsonTypeInfo`-annotated **sealed**
class are now detected automatically, so explicit `@JsonSubTypes` lists can be deleted. Do this as a
separate cleanup commit, after the migration is green — not during it.

---

## 11. Phase 8 — removals with no drop-in replacement

Stop and report if any of these are in use; they need a design decision, not a mechanical
substitution:

- **Format auto-detection** (`DataFormatDetector` and `com.fasterxml.jackson.core.format.*`) —
  dropped entirely, no replacement.
- `ObjectMapper.canSerialize()` / `canDeserialize()` — removed.
- `MappingJsonFactory` — removed.
- `ObjectCodec` — replaced by the `ObjectReadContext` / `ObjectWriteContext` pair; any custom
  streaming integration needs rework.
- Everything `@Deprecated` as of 2.20 is gone. Before starting, consider building against 2.22.1
  with deprecation warnings escalated — the 2.20+ Javadocs name the replacements.
- `JsonFactory.getCodec()` / `setCodec()` — removed; no replacement, since the codec is no longer
  associated with the factory.

---

## 12. Phase 9 — verification

Do not report the migration as complete until all of these pass:

1. `./gradlew build` clean across every touched module.
2. Grep for residual `com.fasterxml.jackson` — every hit must be `com.fasterxml.jackson.annotation`.
3. Dependency report shows no unintended 2.x Jackson on the runtime classpath (except
   `jackson-annotations`), or, if a third-party library forces one, the coexistence is documented
   and deliberate.
4. **Wire-format regression tests**: for every item on the Phase 0 persistence inventory, a fixture
   produced under 2.22.1 round-trips under 3.2.x, and current output is diffed against a 2.22.1
   baseline. Any diff is either approved and documented, or fixed.
5. Every `IOException` catch site from §9 resolved.
6. YAML note: 3.x switched `jackson-dataformat-yaml` from SnakeYAML to **snakeyaml-engine**. If we
   read or write YAML, re-verify parsing of our real config files, not just synthetic tests —
   behaviour and supported YAML dialect differ.
7. No test was deleted or `@Ignore`d to make the build pass.

---

## 13. Performance knobs (only if measured)

Two 3.x defaults can cost throughput relative to 2.x:

- `FAIL_ON_TRAILING_TOKENS` is now on. Keep it on unless profiling shows a real regression on
  trusted input.
- 3.x defaults to a **deque-based** `RecyclerPool`; 2.x used `JsonRecyclerPools.threadLocalPool()`.
  To match old characteristics:

```kotlin
val factory = JsonFactory.builder()
    .recyclerPool(JsonRecyclerPools.threadLocalPool())
    .build()
val mapper = JsonMapper.builder(factory).build()
```

Benchmark before changing. The deque pool exists partly for virtual-thread friendliness;
`threadLocalPool()` is not automatically the right answer.

---

## 14. Deliverable

At the end of the session, produce a report containing:

- Resolved versions: `jackson-bom`, `jackson-databind`, `jackson-module-kotlin`, and the
  transitively resolved `jackson-annotations` (2.x).
- The Phase 0 persistence inventory and, for each entry, whether the wire format changed.
- Every changed default, with the decision made (restored vs. accepted) and why.
- The full list of `IOException` catch sites found and how each was handled.
- Anything from §11 encountered and left unresolved.
- Anything deferred to the next-LTS bump (e.g. Kotlin version floor for 3.3.x).

---

## 15. References

- Jackson 3 Migration
  Guide — https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md
- Jackson 3.0 release notes — https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0
- JSTEP index (rationale for the major
  changes) — https://github.com/FasterXML/jackson-future-ideas/wiki
    - JSTEP-1 package/group rename · JSTEP-2 default config changes · JSTEP-3 `JsonNode` API ·
      JSTEP-4 unchecked exceptions · JSTEP-6 renames · JSTEP-8 feature splits · JSTEP-9 deprecated
      modules · JSTEP-13 LTS policy
- OpenRewrite recipe (Java
  only) — https://docs.openrewrite.org/recipes/java/jackson/upgradejackson_2_3
- jackson-module-kotlin 3.x release
  notes — https://github.com/FasterXML/jackson-module-kotlin/blob/3.x/release-notes/VERSION
- BOM version listing — https://repo1.maven.org/maven2/tools/jackson/jackson-bom/
