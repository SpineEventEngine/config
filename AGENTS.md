## Table of Contents
## Table of Contents
1. [Purpose](#-purpose)
2. [Project overview](#-project-overview)
3. [Agent responsibilities](#-agent-responsibilities)
4. [Coding guidelines for Agents](#-coding-guidelines-for-agents)
5. [Running builds](#running-builds)
6. [Version policy](#version-policy)
7. [Project structure expectations](#-project-structure-expectations)
8. [Documentation tasks](#-documentation-tasks)
9. [Testing](#-testing)
10. [Safety rules for Agents](#-safety-rules-for-agents)
11. [Interaction tips – key to effective collaboration!](#-interaction-tips--key-to-effective-collaboration)
12. [LLM Goals](#-llm-goals)
13. [Welcome, Agents!](#-welcome-agents)

## 🧠 Purpose

This document explains how to use **ChatGPT** and **Codex** effectively in this Kotlin/Java project.

It outlines:

- Agent responsibilities (who does what).
- Coding and architectural guidelines agents must follow.
- Instructions for creating and testing agent-generated outputs.

Whether you are a developer, tester, or contributor, this guide will help you collaborate
with AI to maintain a high-quality codebase.

### Terminology
- **LLM**: Refers to the general category of language models (e.g., ChatGPT, Codex, Claude).
- **Agents**: A broader term for LLMs collaborating on this project. 
- Use specific names (**ChatGPT**, **Codex**) when they excel at different tasks 
  (e.g., scaffolding versus explanation).

---

## 🛠️ Project overview

- **Languages**: Kotlin (primary), Java (secondary).
- **Build tool**: Gradle with Kotlin DSL.
- **Architecture**: Event-driven Command Query Responsibility Segregation (CQRS).
- **Static analysis**: detekt, ErrorProne, Checkstyle, PMD. 
- **Testing**: JUnit 5, Kotest Assertions, Codecov.
- **Tools used**: Gradle plugins, IntelliJ IDEA Platform, KSP, KotlinPoet, Dokka. 

---

## 🤖 Agent responsibilities

| Task/Feature                      | Primary Agent | Supporting Agent | Notes                                      |
|-----------------------------------|---------------|------------------|--------------------------------------------|
| Writing documentation (like KDoc) | ChatGPT       | Codex            | Use for readable, structured docs.         |
| Explaining APIs and architecture  | ChatGPT       | -                | Great for clarity in team workflows.       |
| Code generation (e.g., tests)     | Codex         | ChatGPT          | Codex produces quick scaffolding.          |
| Code refactoring suggestions      | ChatGPT       | Codex            | Use ChatGPT for design-level improvements. |
| Completing functions or classes   | Codex         | -                | Codex is better for direct completions.    |
| Debugging and test suggestions    | ChatGPT       | Codex            | ChatGPT suggests missing scenarios.        |


### Tagging pull request messages

Use PR tags for clarity:
```text
feat(chatgpt): Updated README with clearer KDoc examples
fix(codex): Completed missing `when` branches in tests
```
#### Why tag pull requests?
Tagging PRs helps the team:
  - Track which agent contributed to specific changes.
  - Understand whether a PR needs extra human review based on the agent's role.
  - Make decisions about multi-agent collaboration in reviews.
---

## 🧾 Coding guidelines for Agents

### ✅ Preferred

1. Kotlin idioms are **preferred** over Java-style approaches, including:
   - Extension functions
   - `when` expressions
   - Smart casts
   - Data classes
   - Sealed classes

2. Immutable data structures. 

3. Apply **Java interop** only when needed (e.g., using annotations or legacy libraries).

4. Use **Kotlin DSL** when modifying or generating Gradle files.

5. Generate code that **compiles cleanly** and **passes static analysis**.

6. Respect **existing architecture**, naming conventions, and project structure.

7. Use `@file:JvmName`, `@JvmStatic`, etc., where appropriate.

### ❌ Avoid

- Mutable data structures
- Java-style verbosity (e.g., builders with setters)
- Redundant null checks (`?.let` misuse)
- Using `!!` unless clearly justified
- Mixing Groovy and Kotlin DSLs in build logic
- Using reflection unless requested

### General guidance
- Adhere to the [Spine Event Engine Documentation][spine-docs]
  for coding style and contribution procedures. 

- The conventions on the [Spine Event Engine Documentation][spine-docs]
  page and other pages in this Wiki area **take precedence over** standard Kotlin or
  Java conventions.

- Write clear, incremental commits with descriptive messages.
- Include automated tests for any code change that alters functionality.
- Keep pull requests focused and small.

### Naming convention for variables
- Prefer simple nouns over composite nouns. E.g., `user` is better than `userAccount`.

### Naming Guidelines

#### Avoid using type names in variable names 
| DO                                 | DON'T                                        |
|------------------------------------|----------------------------------------------|
| `val user = getUser()`             | `val userObject = getUser()`                 |
| `val items = getItems()`           | `val itemList = getItems()`                  | 
| `val gradleWrapper: IvyDependency` | `val gradleWrapperDependency: IvyDependency` |

#### Avoid duplication of strings in the code
- Use constants in companion objects instead.
- If a string contains Kotlin interpolation, it should be a property instead.

#### Prefer generic parameters over explicit variable types
| DO                                      | DON'T                                               |
|-----------------------------------------|-----------------------------------------------------|
| `val list = mutableList<Deppendency>()` | `val list: MutableList<Dependency> = mutableList()` |

### Code Formatting Guidelines
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

#### Using periods in the texts
- Put periods at the end of sentences.
- Do not put periods if a line of text is a fragment.

#### Commenting Guildeines
- Avoid in-place comments in the code unless specifically asked.

### Safety Rules Checklist
- ✅ Ensure all generated code compiles and passes static analysis.
- ❌ Avoid unnecessary reflection or unsafe code (e.g., `!!` in Kotlin).
- ✅ Do not auto-update external dependencies unless explicitly allowed.

---
## Version policy

### We use semver
The version of the project is kept in the `version.gradle.kts` file in the root of the project.

The version numbers in these files follow the conventions of
[Semantic Versioning 2.0.0](https://semver.org/).

IMPORTANT: ALWAYS increment the version when a new branch is created.

### Quick Checklist for Versioning
1. Increment the patch version in `version.gradle.kts`.
   Retain zero-padding if applicable:
    - Example: `"2.0.0-SNAPSHOT.009"` → `"2.0.0-SNAPSHOT.010"`
2. Commit the version bump separately with this comment:
   ```text
   Bump version → `$newVersion`
   ``` 
3. Rebuild using `./gradlew clean build`.
4. Update `pom.xml`, `dependencies.md` and commit changes with: `Update dependency reports`

Remember: PRs without version bumps will fail CI (conflict resolution detailed above).

### Resolving conflicts in `version.gradle.kts`
A branch conflict over the version number should be resolved as described below.
 * If a merged branch has a number which is less than that of the current branch, the version of
   the current branch stays.
 * If the merged branch has the number which is greater or equal to that of the current branch,
   the number should be increased by one.
---

## Running builds

1. When modifying code, run:
   ```bash
   ./gradlew build
   ```

2. If Protobuf (`.proto`) files are modified run:
   ```bash
   ./gradlew clean build
   ````

3. Documentation-only changes run:
   ```bash
   ./gradlew dokka
   ```
   Documentation-only changes do not require running tests!
---

## 📁 Project structure expectations

```yaml
.github
buildSrc/
<module-1>
  src/
  ├── main/
  │ ├── kotlin/ # Kotlin source files
  │ └── java/ # Legacy Java code
  ├── test/
  │ └── kotlin/ # Unit and integration tests
  build.gradle.kts # Kotlin-based build configuration
<module-2>
<module-3>
build.gradle.kts # Kotlin-based build configuration
settings.gradle.kts # Project structure and settings
README.md # Project overview
AGENTS.md # LLM agent instructions (this file)
version.gradle.kts # Declares the project version. 
```
---

## 📄 Documentation tasks

- Suggest better **names** and **abstractions**.

- Help format inline comments and design rationale.

#### Documentation Checklist
1. Ensure all public and internal APIs have KDoc examples.
2. Add in-line code blocks for clarity.
3. Use `TODO` comments with agent names for unresolved logic sections:
    - Example: `// TODO(chatgpt): Refactor `EventStore` for better CQRS compliance.`

---

## 🧪 Testing

### Guidelines
- Do not use mocks, use stubs.
- Prefer [Kotest assertions][kotest-assertions] over
  assertions from JUnit or Google Truth.

### Responsibilities

#### Codex
- Generate unit tests for APIs (handles edge cases/scenarios).
- Supply scaffolds for typical Kotlin patterns (`when`, sealed classes).

#### ChatGPT
- Suggest test coverage improvements.
- Propose property-based testing or rare edge case scenarios.

---

## 🚨 Safety rules for Agents

- Do **not** auto-update external dependencies without explicit request.
- Do **not** inject analytics or telemetry code.
- Flag any usage of unsafe constructs (e.g., reflection, I/O on the main thread).
- Avoid generating blocking calls inside coroutines.

---

## ⚙️ Refactoring Guidelines
- Do not replace Kotest assertions with standard Kotlin's Built-In Test Assertions.

---

## 💬 Interaction tips – key to effective collaboration!

- Human programmers may use inline comments to guide agents:
  ```kotlin
    // ChatGPT: Suggest a refactor for better readability.
    // Codex: Complete the missing branches in this `when` block.
    // ChatGPT: explain this logic.
    // Codex: complete this function.
   ```
- Agents should ensure pull request messages are concise and descriptive:
  ```text
  feat(chatgpt): suggested DSL refactoring for query handlers  
  fix(codex): completed missing case in sealed class hierarchy
  ```
- Encourage `// TODO:` or `// FIXME:` comments to be clarified by ChatGPT.

- When agents or humans add TODO comments, they **must** follow the format described on
  the [dedicated page][todo-comments].

---

## 🧭 LLM Goals

These goals guide how agents (ChatGPT, Codex) are used in this project to:
- Help developers move faster without sacrificing code quality.
- Provide language-aware guidance on Kotlin/Java idioms.
- Lower the barrier to onboarding new contributors.
- Enable collaborative, explainable, and auditable development with AI.

---

## 📋 Common Tasks

- **Adding a new dependency**: Update relevant files in `buildSrc` directory.
- **Creating a new module**: Follow existing module structure patterns.
- **Documentation**: Use KDoc style for public and internal APIs.
- **Testing**: Create comprehensive tests using Kotest assertions.

--- 

## 👋 Welcome, Agents!
 - You are here to help.
 - Stay consistent, stay clear, and help this Kotlin/Java codebase become more robust,
   elegant, and maintainable.

<!-- External links -->
[spine-docs]: https://github.com/SpineEventEngine/documentation/wiki
[kotest-assertions]: https://kotest.io/docs/assertions/assertions.html
[todo-comments]: https://github.com/SpineEventEngine/documentation/wiki/TODO-comments
