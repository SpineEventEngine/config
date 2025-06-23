## General Guidelines for Jnunie and AI Assistant

Read the `AGENTS.md` file at the root of the project to understand:
 - the agent responsibilities,
 - project overview, 
 - coding guidelines, 
 - other relevant topics.

Also follow the rules described below.

## Naming Guidelines

### Avoid using type names in variable names 
| DO                                 | DON'T                                        |
|------------------------------------|----------------------------------------------|
| `val user = getUser()`             | `val userObject = getUser()`                 |
| `val items = getItems()`           | `val itemList = getItems()`                  | 
| `val gradleWrapper: IvyDependency` | `val gradleWrapperDependency: IvyDependency` |

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
