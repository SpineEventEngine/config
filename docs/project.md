# Project: config

## Overview

`config` is the shared build, CI, and AI-agent configuration for the Spine SDK
organisation (~40 repositories). Each consuming project adds it as a Git
submodule under `config/` and runs `./config/pull` to receive the latest shared
files. Centralising this configuration keeps dependency versions, build logic,
CI workflows, and agent guidance consistent across the whole SDK from a single
source of truth.

## Architecture

Role in the org: **build/CI/agent-configuration infrastructure** (not a runtime
library). It is itself a JVM/Gradle project (its `buildSrc` and `ConfigTester`
are written in Kotlin).

What `pull`/`migrate` distribute to each consumer:

- `buildSrc/` — common Gradle build logic and the `io.spine.dependency`
  declarations; the consumer's own `module.gradle.kts` is preserved.
- Gradle Wrapper, `gradle.properties`, `.idea/` settings, `.codecov.yml`,
  `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, and the GitHub workflows under
  `.github-workflows/` (merged into `.github/workflows/`).
- AI-agent configuration: `AGENTS.md`, `CLAUDE.md`, and the Claude/Junie/Codex
  config files.

Shared agent **skills, scripts, and guidelines** are *not* copied. They live in
the [`SpineEventEngine/agents`][agents-repo] repository, mounted as a floating Git
submodule at `.agents/shared` (tracking `master`) and exposed through symlinks
(`.agents/skills`, `.agents/scripts`, `.agents/guidelines`, `.claude/commands`,
`.claude/agents`, plus `.claude/skills` and `.junie/skills`, which alias
`.agents/skills`). This eliminates per-repo file churn — skills update everywhere
via `git submodule update --remote` with no commits in consumer repos. `config`
itself consumes this submodule (it dogfoods the same setup).

Per-repo content stays local and is never overwritten: `docs/project.md`,
`.agents/memory/`, and `.agents/tasks/`.

Read [`.agents/guidelines/jvm-project.md`](../.agents/guidelines/jvm-project.md) for
build stack, coding style, tests, and versioning.

[agents-repo]: https://github.com/SpineEventEngine/agents
