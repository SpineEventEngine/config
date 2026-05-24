# 👋 Welcome, Agents!

## Orientation

If `.agents/project.md` exists in this repository, read it first — it describes
the language, architecture, and role of this specific repo within the Spine SDK
organisation.

For full guidelines and reference material, see
**[Agent Documentation](./.agents/_TOC.md)**.

## Commit and history safety

**Do not commit, push, tag, rebase, merge, or otherwise write to git history**
unless one of the following is true *right now*:

1. The currently active skill's `SKILL.md` has a `## Commit authorization` section
   that explicitly permits the operation.
2. The user's *current* prompt explicitly requests the operation.

Authorization does not carry over between turns or sessions. When in doubt: stage
changes, show the diff, and stop — let the user commit.

See [`.agents/safety-rules.md`](.agents/safety-rules.md) → *Commits and history-writing*.

## Other safety rules

- All code must compile and pass static analysis.
- Do not auto-update external dependencies outside a dedicated update task.
- No analytics, telemetry, or tracking code.
- No reflection or unsafe code without explicit approval.

See [`.agents/safety-rules.md`](.agents/safety-rules.md) for the full list.

## Moving files

When moving or renaming tracked files, always use `git mv`. Do not simulate a
move by deleting the old file and creating a new one — preserve Git history
unless the user explicitly asks for a fresh replacement.

If `git mv` fails due to permissions or sandbox restrictions, request approval;
do not fall back to delete/create.
