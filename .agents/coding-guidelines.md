# 🧾 Coding guidelines

## Core principles

- Adhere to [Spine Event Engine Documentation][spine-docs] for coding style.
- Generate code that compiles cleanly and passes static analysis.
- Respect existing architecture, naming conventions, and project structure.
- Write clear, incremental commits with descriptive messages.
- Include automated tests for any code change that alters functionality.

## Kotlin best practices

### ✅ Prefer
- **Kotlin idioms** over Java-style approaches:
  - Extension functions
  - `when` expressions 
  - Smart casts
  - Data classes and sealed classes
  - Immutable data structures
- **Simple nouns** over composite nouns (`user` > `userAccount`)  
- **Generic parameters** over explicit variable types (`val list = mutableList<Dependency>()`)  
- **Java interop annotations** only when needed (`@file:JvmName`, `@JvmStatic`)
- **Kotlin DSL** for Gradle files

### ❌ Avoid
- Mutable data structures
- Java-style verbosity (builders with setters)
- Redundant null checks (`?.let` misuse)
- Using `!!` unless clearly justified
- Type names in variable names (`userObject`, `itemList`)
- String duplication (use constants in companion objects)
- Mixing Groovy and Kotlin DSLs in build logic
- Reflection unless specifically requested

## Documentation & comments

### KDoc style
- Write concise descriptions for all public and internal APIs.
- Start parameter descriptions with capital letters.
- End parameter descriptions with commas.
- Use inline code with backticks for code references (`example`).
- Format code blocks with fences and language identifiers:
  ```kotlin
  // Example code
  fun example() {
      // Implementation
  }
  ```

### Commenting guidelines
- Avoid inline comments in production code unless necessary.
- Inline comments are helpful in tests.
- When using TODO comments, follow the format on [dedicated page][todo-comments].
- File and directory names should be formatted as code.

### Tex width
- Wrap `.md` text to 80 characters for readability.
- Wrap KDoc comments at 75 characters. 

### Using periods
- Use periods at the end of complete sentences.
- Use periods for full or multi-clause bullets.
- Use NO periods for short bullets.
- Use NO periods for fragments.
- Use NO periods in titles and headers.
- Use NO periods in parameter descriptions in Javadoc.
- DO USE periods in parameter and property descriptions in KDoc.
- Be consistent within the list!

## Text formatting
 - ✅ Remove double empty lines in the code.
 - ✅ Remove trailing space characters in the code.

[spine-docs]: https://github.com/SpineEventEngine/documentation/wiki
[todo-comments]: https://github.com/SpineEventEngine/documentation/wiki/TODO-comments
