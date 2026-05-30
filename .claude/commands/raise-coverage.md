---
description: Localize coverage gaps with JaCoCo and generate missing unit tests for a module or path.
argument-hint: "<:module | path | --triage>"
allowed-tools: Read, Edit, Write, Grep, Glob, Bash(./gradlew:*), Bash(git status:*), Bash(find:*)
---

Follow the `raise-coverage` skill exactly:

- Skill: `.agents/skills/raise-coverage/SKILL.md`
- Target: $ARGUMENTS — a Gradle module (e.g. `:base`), a source path, or
  `--triage` to only produce the ranked JaCoCo gap report without generating tests.
- Order: localize gaps with JaCoCo → propose concrete test cases and **wait for
  confirmation** → generate → re-run `:<module>:jacocoTestReport` to verify the
  gap closed.
- Honor `.agents/testing.md` (stubs not mocks; Kotlin → Kotest, Java → Google
  Truth) and `.agents/coding-guidelines.md` for Kotlin/Java idioms.
- Target human-written `src/main` code only — never generated code, `examples`,
  or existing tests.
- Never weaken a `.codecov.yml` target or add a mocking dependency to make a
  check pass. Tests-only changes do not require a version bump.
