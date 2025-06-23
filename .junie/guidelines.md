# Spine Tool-Base Project Guidelines

This document provides concise guidance for developers working on the Spine Tool-Base project
with Junie AI assistance.

## Project Overview

Spine Tool-Base is a collection of modules that provide foundational code for development tools
in the Spine SDK ecosystem. It primarily uses Kotlin with some Java components and is
built with Gradle using Kotlin DSL for build configuration.

## Tech Stack

- **Languages**: Kotlin (primary), Java (secondary)
- **Build System**: Gradle with Kotlin DSL
- **Architecture**: Event-driven Command Query Responsibility Segregation (CQRS)
- **Testing**: JUnit 5, Kotest Assertions
- **Code Quality**: detekt, ErrorProne, Checkstyle, PMD, Codecov
- **Documentation**: Dokka
- **Tools**: IntelliJ IDEA Platform, KSP, KotlinPoet

## Project Structure

```
.
├── `buildSrc`/                  # Build configuration and dependencies
├── `config`/                    # Project configuration files
├── `gradle-plugin-api`/         # API for tool/library authors
├── `gradle-root-plugin`/        # Gradle plugins with root extensions
├── `jvm-util`/                  # Utilities for JVM project tools
├── `plugin-base`/               # Abstractions for Gradle plugins
├── `plugin-testlib`/            # Test fixtures for Gradle plugins
├── `psi`/                       # Utilities for IntelliJ Platform PSI
├── `tool-base`/                 # Common components for build-time tools
├── `build.gradle.kts`           # Root build configuration
├── `settings.gradle.kts`        # Project structure and settings
├── `version.gradle.kts`         # Project version declaration
└── `README.md`                  # Project overview
```

Each module follows a standard structure:
```
`module-name`/
├── `src`/
│   ├── `main`/
│   │   ├── `kotlin`/           # Kotlin source files
│   │   └── `java`/             # Java source files (if any)
│   └── `test`/
│       └── `kotlin`/           # Test files
└── `build.gradle.kts`          # Module-specific build configuration
```

## Running Tests

- **Run all tests**:
  ```bash
  ./gradlew test
  ```

- **Run tests for a specific module**:
  ```bash
  ./gradlew :`module-name`:test
  ```

- **Run a specific test class**:
  ```bash
  ./gradlew :`module-name`:test --tests "fully.qualified.TestClassName"
  ```

## Building the Project

- **Regular build**:
  ```bash
  ./gradlew build
  ```

- **Clean build (required after Protobuf changes)**:
  ```bash
  ./gradlew clean build
  ```

- **Documentation only**:
  ```bash
  ./gradlew dokka
  ```

## Version Management

- Version follows semantic versioning (semver) and is stored in `version.gradle.kts`
- Increment the patch version for each PR (e.g., `2.0.0-SNAPSHOT.009` → `2.0.0-SNAPSHOT.010`)
- After version changes, run `./gradlew clean build` and commit updated `pom.xml` and
  `dependencies.md`

## Naming Guidelines

### Avoid using type names in variable names 
| DO                                 | DON'T                                        |
|------------------------------------|----------------------------------------------|
| `val user = getUser()`             | `val userObject = getUser()`                 |
| `val items = getItems()`           | `val itemList = getItems()`                  | 
| `val gradleWrapper: IvyDependency` | `val gradleWrapperDependency: IvyDependency` |

## Coding Best Practices

- Prefer Kotlin idioms over Java-style approaches.
- Use immutable data structures.
- Apply Java interop only when needed.
- Use Kotlin DSL for Gradle files.
- Write clear, incremental commits with descriptive messages.
- Include automated tests for functionality changes.
- Keep pull requests focused and small.

### Avoid duplication of strings in the code
- Use constants in companion objects instead.
- If a string contains Kotlin interpolation, it should be a property instead.

### Prefer generic parameters over explicit variable types
| DO                                      | DON'T                                               |
|-----------------------------------------|-----------------------------------------------------|
| `val list = mutableList<Deppendency>()` | `val list: MutableList<Dependency> = mutableList()` |

## Code Formatting Guidelines
- Start parameter descriptions with a capital letter.
- In-line code fragments are always surrounded with back ticks. E.g., `code`.
- File and directory names are code and should be formatted as such.
- Block code fragments in documentation and diagnostic messages must be surrounded
  with code fences (```).
- Code fences that are part of the code come with extra backtick:
  ```text
     Here's how you put the nested code fences:
     ````kotlin
     // Nested code example.
     ````
  ```
- Descriptions of parameters, properties, and exceptions in KDoc must be terminated with a comma.
- When creating `.md` files wrap the text so that it is not wider than 80 characters.
- Put periods at the end of sentences.
- Do not put periods if a line of text is a fragment.
- Avoid in-place comments in the code unless specifically asked.

## Testing Guidelines

- Use stubs instead of mocks for test doubles.
- Prefer Kotest assertions over JUnit or Google Truth.
- Ensure tests cover edge cases and typical scenarios.
- When writing tests, avoid comments that explain the code. Prefer self-explanatory code.

## Junie Assistance Tips

When working with Junie AI on the Spine Tool-Base project:

1. **Project Navigation**: Use `search_project` to find relevant files and code segments.
2. **Code Understanding**: Request file structure with `get_file_structure` before editing.
3. **Code Editing**: Make minimal changes with `search_replace` to maintain project consistency.
4. **Testing**: Verify changes with `run_test` on relevant test files.
5. **Documentation**: Follow KDoc style for documentation.
6. **Kotlin Idioms**: Prefer Kotlin-style solutions over Java-style approaches.
7. **Version Updates**: Remember to update `version.gradle.kts` for PRs.

## Common Tasks

- **Adding a new dependency**: Update relevant files in `buildSrc` directory.
- **Creating a new module**: Follow existing module structure patterns.
- **Documentation**: Use KDoc style for public and internal APIs.
- **Testing**: Create comprehensive tests using Kotest assertions.
