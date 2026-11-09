# Java Compatibility Testing Implementation Plan
**For SpineEventEngine/config Shared Configuration**

**Objective**: Extend the shared CI configuration to test Java 17 (baseline) + Java 21 (and optionally 25) compatibility across the SDK family, particularly for SpineEventEngine SDK products including on Google Cloud Platform.

**Status**: Current workflows pin to Java 17 (Zulu) with `actions/setup-java` + `gradle/actions/setup-gradle`.

**Priority**: High — Aligns with enterprise/GCP adoption trends while maintaining broad compatibility.

---

## 1. Preparation (1-2 hours)

### 1.1 Review Current State
- **Java baseline**: Java 17 (see `gradle.properties`, `build-on-ubuntu.yml`, `publish.yml`).
- **Toolchains**: Already enabled via `org.gradle.java.installations.auto-detect=true` and `auto-download=true`.
- **Workflows**: Located in `.github-workflows/` (copied to consumer `.github/workflows/` via `./config/pull`).
- **Key files to update**:
  - `.github-workflows/build-on-ubuntu.yml` (main CI)
  - `.github-workflows/publish.yml` (keep Java 17 for publishing)
  - `buildSrc/` (if custom tasks/plugins need toolchain awareness)
  - `gradle.properties` (optional tweaks)
  - `build.gradle.kts` (in `config` and consumers)

**Action**: Clone this repo locally, run `./gradlew tasks --all` and inspect `buildDependants` task.

---

## 2. Gradle Configuration Updates

### 2.1 Update `build.gradle.kts` (in `config` and propagate)
Add or enhance toolchain and test tasks:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// Register compatibility test tasks (optional but useful)
tasks.register("testJava21", Test::class) {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }
    // Group with other test tasks
}
```

**Action**: Add similar blocks where needed. Test locally with `./gradlew testJava21`.

---

## 3. GitHub Actions Workflow Updates (Core Change)

### 3.1 Update `build-on-ubuntu.yml` → Matrix Strategy

Replace the single-job setup with:

```yaml
name: Ubuntu CI

on:
  push:
    branches: [master]
  pull_request:

jobs:
  test:
    name: Test on Java ${{ matrix.java }}
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: ['17', '21']  # Add '25' later
      fail-fast: false

    steps:
      - uses: actions/checkout@v6
        with:
          submodules: 'true'

      - name: Setup Java ${{ matrix.java }}
        uses: actions/setup-java@v5
        with:
          distribution: 'temurin'  # Preferred for broad compatibility (or keep 'zulu')
          java-version: ${{ matrix.java }}
          # NO cache: 'gradle' — handled by setup-gradle

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6
        with:
          cache-read-only: ${{ matrix.java != '17' }}  # Primary version owns cache writes

      - name: Build & Test
        run: ./gradlew build dokkaGenerate --stacktrace

      - name: Publish Test Report
        uses: mikepenz/action-junit-report@v4.0.3
        if: always()
        with:
          report_paths: '**/build/test-results/**/TEST-*.xml'
          require_tests: true

      - name: Upload code coverage
        uses: codecov/codecov-action@v4
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
          fail_ci_if_error: true
```

**Notes**:
- Keep publishing job on Java 17 only.
- Consider adding `macos-latest` or Windows matrix later for full cross-platform.

---

## 4. Implementation Steps (Actionable Checklist)

1. **Branch & Edit**  
   - Create branch `feature/java-21-compatibility` in `config`.

2. **Update Shared Workflows** (`.github-workflows/`)  
   - Modify `build-on-ubuntu.yml` as above.  
   - Review `publish.yml` (keep pinned to 17).  
   - Update any other workflows referencing Java version.

3. **Enhance Gradle**  
   - Add toolchain + test tasks in `build.gradle.kts`.  
   - Document supported versions in `README.md`.

4. **Local Validation**  
   - `./gradlew clean build` (Java 17)  
   - `./gradlew testJava21`  
   - Run `./gradlew buildDependants` to validate against `base`, `base-types`, `core-jvm`.

5. **Test in Consumer Repo**  
   - In a test SDK repo: Update submodule → `./config/pull` → Push PR and verify matrix runs.

6. **Merge & Propagate**  
   - Merge to `master`.  
   - Update all SDK repos via `pull` script.  
   - Monitor first few PRs.

7. **GCP-Specific Testing (Optional Phase 2)**  
   - Add job deploying test service to Cloud Run with Java 21 runtime.  
   - Use Temurin/Corretto distributions.

---

## 5. Risks & Mitigations

- **Breaking changes 17→21**: Monitor deprecations (Security Manager, etc.). Use `--release 17` for compilation.
- **Cache conflicts**: Strict use of `cache-read-only`.
- **Build time**: Matrix increases time — acceptable for compatibility.
- **Third-party libs**: Ensure dependencies support Java 21.

---

## 6. Success Criteria
- All matrix jobs pass on PRs.
- SDK runs on Java 17 and 21+ (verified locally + GCP).
- Documentation updated with compatibility matrix.
- No regression in existing Java 17 consumers.

**Estimated Effort**: 4-8 hours + validation time.

**Next Steps**: Start with workflow update and test locally.

---
*Document generated for SpineEventEngine/config — Markdown format.*
