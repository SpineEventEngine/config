# Gradle Tasks – Conventions for Spine SDK

## Purpose

The `group` property on a Gradle `Task` is **metadata only**. It controls how tasks are organized and displayed in:

- `./gradlew tasks`
- IntelliJ IDEA Gradle tool window
- Other build tools

It has **no impact** on task execution or dependencies.

## Official Gradle & Plugin Behavior

- Core tasks such as `compileJava`, `compileKotlin`, `processResources`, etc. are intentionally placed in the **`other`** group.
- This is by design from the Gradle and Kotlin plugin teams to keep the default task list clean.
- High-level tasks use groups like `build`, `verification`, `documentation`, `publishing`.

## Spine SDK Conventions

**All custom tasks** created by Spine plugins **must** declare a clear `group`.

### Recommended Group Name

```kotlin
group = "spine"
```

### Example

```kotlin
tasks.register("generateSpineModel") {
    group = "spine"
    description = "Generates Spine model classes from .proto definitions"
    // ...
}
```

### For Existing Tasks

When your plugin adds or configures tasks, set the group explicitly:

```kotlin
tasks.withType<YourTaskType>().configureEach {
    group = "spine"
    description = "..." 
}
```

## Why This Matters

- Improves developer experience in large multi-plugin projects.
- Makes Spine-specific tasks easy to discover in IDE and CLI.
- Follows patterns used by Dokka, Ktlint, Shadow, etc.

**Rule of thumb:** Every public or useful task added by Spine plugins should belong to the `spine` group.
