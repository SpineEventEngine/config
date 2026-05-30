---
description: Ensure the repo is on Kover (migrate from JaCoCo if needed), then localize coverage gaps and generate missing unit tests for a module or path.
argument-hint: "<:module | path | --triage>"
allowed-tools: Read, Edit, Write, Grep, Glob, Bash(./gradlew:*), Bash(git status:*), Bash(find:*)
---

Follow the `raise-coverage` skill exactly:

- Skill: `.agents/skills/raise-coverage/SKILL.md`
- Target: $ARGUMENTS — a Gradle module (e.g. `:base`), a source path, or
  `--triage` to only produce the ranked Kover gap report without generating tests.
- First-time setup: the skill enforces Kover. If vanilla JaCoCo is found
  anywhere, the skill proposes a repo-wide migration and **waits for your
  approval**. See `.agents/skills/raise-coverage/references/migrate-to-kover.md`.
- Order: localize gaps from Kover's JaCoCo-format XML → propose concrete test
  cases and **wait for confirmation** → generate → re-run
  `:<module>:koverXmlReport` to verify the gap closed.
- Honor `.agents/testing.md` (stubs not mocks; Kotlin → Kotest, Java → Google
  Truth) and `.agents/coding-guidelines.md` for Kotlin/Java idioms.
- Target human-written `src/main` code only — never generated code, `examples`,
  or existing tests.
- Never weaken a `.codecov.yml` target or add a mocking dependency to make a
  check pass. Tests-only changes do not require a version bump.
