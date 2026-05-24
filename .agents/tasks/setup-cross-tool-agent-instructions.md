# Task: Set Up Cross-Tool AI Agent Instructions

## Goal

Establish a single canonical source of AI coding agent instructions that works across Claude Code, GitHub Copilot, and OpenAI Codex. Eliminate duplicate, drift-prone instruction files.

## Strategy

`AGENTS.md` at the repository root is the canonical source of truth. Tool-specific files thin-wrap or import it so each agent picks up the same content natively.

## Prerequisites

Before making changes, check whether any of these files already exist:

- `AGENTS.md` (repo root)
- `CLAUDE.md` (repo root)
- `.github/copilot-instructions.md`
- `.github/instructions/*.instructions.md`

If any exist, read each one first. Merge their content into `AGENTS.md` rather than overwriting. If the existing files contain conflicting rules, surface the conflicts in a summary to the user and stop. Do not silently resolve them.

## Steps

### 1. Create or update `AGENTS.md` at the repo root

Plain Markdown. Include these sections, omitting any that do not apply to this project:

- **Project overview** — one or two sentences describing what this codebase is and its core constraint.
- **Tech stack** — language(s) and major frameworks with versions where versions matter.
- **Build, test, lint commands** — exact CLI commands the agent should use. For a Kotlin/Gradle project, e.g. `./gradlew build`, `./gradlew test`, `./gradlew ktlintCheck`.
- **Conventions** — concrete rules. "Prefer `val` over `var`", "no wildcard imports", "expression bodies for single-expression functions". Avoid vague guidance like "write clean code".
- **Project layout** — where modules, tests, and key files live. Only include if non-obvious from the directory tree.
- **PR / commit conventions** — title format, required checks before commit, branching rules.
- **What not to do** — destructive or out-of-scope behaviors. E.g. "Never modify files under `build/`", "Do not rotate secrets".

Keep the file under 300 lines. Every line must change agent behavior — if it would not, delete it.

### 2. Create `CLAUDE.md` at the repo root

Make it minimal. Use Claude Code's import syntax to pull in `AGENTS.md`:

```markdown
@AGENTS.md

## Claude Code-specific notes

<!-- Optional. Add Claude Code-only overrides here. Leave empty if none. -->
```

Rationale: Claude Code does not yet read `AGENTS.md` natively. The `@AGENTS.md` import is the official workaround.

### 3. Create `.github/copilot-instructions.md`

Create the `.github/` directory if it does not exist. Then create a one-line pointer file:

```markdown
See [AGENTS.md](../AGENTS.md) at the repository root.
```

Rationale: Copilot reads `AGENTS.md` natively, but a pointer at the conventional Copilot path makes the setup obvious to anyone inspecting the repo. Do **not** duplicate the contents of `AGENTS.md` here unless explicitly instructed — duplication causes drift.

### 4. (Optional) Add path-specific Copilot rules

Only do this step if the user has indicated they need language- or directory-scoped Copilot rules that do not fit cleanly in `AGENTS.md`. Otherwise skip.

For each scope:

1. Create `.github/instructions/<descriptive-name>.instructions.md`.
2. Add YAML frontmatter with an `applyTo` glob. Example for Kotlin files:
   ```markdown
   ---
   applyTo: "**/*.kt,**/*.kts"
   ---
   ```
3. Below the frontmatter, write the rules that apply only to files matching the glob.

Note: this scoping mechanism is Copilot-specific. Codex and Claude Code do not read `.github/instructions/`. If the rules are important for all three tools, put them in `AGENTS.md` (or a nested `AGENTS.md` in the relevant subdirectory) instead.

### 5. Verify the setup

Run these checks and report results:

- `AGENTS.md` exists at the repo root and is non-empty.
- `CLAUDE.md` exists at the repo root and its first non-empty line is `@AGENTS.md`.
- `.github/copilot-instructions.md` exists and references `AGENTS.md`.
- All created or modified files are tracked by git (no relevant entries under "Untracked files" in `git status`).
- The `AGENTS.md` content is under 300 lines (`wc -l AGENTS.md`).

### 6. Commit

Stage only the files created or modified by this task. Use this commit message:

```
chore: set up cross-tool AI agent instructions

Establish AGENTS.md as the canonical source of agent instructions.
CLAUDE.md and .github/copilot-instructions.md thin-wrap it so the
same content is used by Claude Code, Copilot, and Codex.
```

## Acceptance Criteria

- Editing `AGENTS.md` is the only required change to update agent behavior across all three tools.
- No instruction content is duplicated between files.
- `AGENTS.md` is under 300 lines.
- All checks in step 5 pass.

## Notes for the Agent

- The path `.agents/tasks/` (where this task file lives) is a local convention, not an industry standard. No tool auto-discovers files in this directory.
- `AGENTS.md` is the open standard at https://agents.md/, supported natively by Codex, Copilot (since Aug 2025), Cursor, Factory, GitLab Duo, and others.
- Native `AGENTS.md` support in Claude Code is requested but not yet shipped: https://github.com/anthropics/claude-code/issues/6235. The `@AGENTS.md` import is the current workaround.
- Skills (folders under `.github/skills/` or `.claude/skills/`) are a separate mechanism for repeatable multi-step workflows with bundled assets. They complement `AGENTS.md`; they do not replace it. This task does not set up skills.
- If the user asks to use a different filename or layout (e.g. `.cursorrules`, `GEMINI.md`), apply the same thin-wrapper principle: one canonical file, all others point to it.
