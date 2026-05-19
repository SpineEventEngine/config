# CLAUDE.md — Project Conventions

## Project Guidelines

- Quick-reference baseline: `.agents/quick-reference-card.md` — always read this first.
- For specific tasks (code review, PR prep, dependency updates, docs, etc.): discover and use
  skills from `.agents/skills/`.
- Full standards reference: `.agents/_TOC.md` — consult when a skill doesn't cover the needed
  context.

## Workflow Rules

- Always start non-trivial tasks (3+ steps, architecture, refactoring) in Plan mode.
- Write detailed plan to `.agents/tasks/todo.md` before coding.
- If something goes wrong — STOP and re-plan immediately.
- Use subagents liberally for research, verification, and parallel work.
- One focused task per subagent.

## Self-Improvement Loop

- After any correction: update `.agents/tasks/lessons.md` (or this file) with the rule.
- Review relevant lessons at the start of every session.
- Ruthlessly iterate until mistakes stop repeating.
- Keep this file short (<120 lines / ~2.5k tokens).

## Verification & Quality

- Never mark a task done without proof (tests, logs, diff vs main).
- Ask: "Would a senior/staff engineer approve this?"
- For non-trivial changes: pause and consider a more elegant solution.
- Fix bugs autonomously — find root cause, no hand-holding, no band-aids.

## Core Principles

- Simplicity first: minimal code impact, minimal surface area.
- No laziness: always find root causes.
- Minimal side effects: avoid new bugs.
- Prefer early returns and clear naming.
- Challenge your own work before presenting it.

## Task Flow

1. Plan → `.agents/tasks/todo.md`
2. Show plan before implementing.
3. Execute + track progress.
4. Verify + summarize changes.
5. Update lessons.

## Final Rule

This is living team memory. Update it regularly and keep it concise.
