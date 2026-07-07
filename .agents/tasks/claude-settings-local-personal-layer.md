# Stop `config` from clobbering the Claude `settings.local.json` personal layer

## Context

`config/migrate` distributes and overwrites `.claude/settings.local.json` into every
consumer on each `./config/pull`. In Claude Code, `settings.local.json` is the
**gitignored, per-developer personal-override layer** (merge precedence:
`user < project < local`, local wins). Treating it as a distributed file inverts that
contract twice:

1. Any personal override a developer puts there is silently wiped on the next pull
   (the non-Hugo branch `cp`s config's copy over it).
2. It is forced into Git, contrary to its "local, not committed" role — and the
   Hugo-only branch even `rm -f`s the developer's personal file.

The genuinely org-wide permissions the file carried (`Skill(pre-pr)` and the
`version-bumped` guard) belong in the **shared** layer (`settings.json` /
`settings-hugo.json`), not the personal one. The remaining entry
(`Bash(echo "exit=$?")`) is captured session cruft.

Outcome: `config` treats `settings.local.json` as strictly personal — a pull never
creates, overwrites, or deletes it — while preserving the useful auto-approvals in the
shared templates, gitignoring the file in consumers, and un-tracking `config`'s own copy
so the repo dogfoods the contract. Same-PR follow-on (confirmed with the user): add
`"plansDirectory": ".agents/tasks"` to both templates so plan/status docs default into
the repo-local, non-clobbered task dir org-wide.

## Changes

### 1. `migrate` — stop touching `settings.local.json` (lines ~185–202)
- Delete the Hugo-branch `rm -f ../.claude/settings.local.json` (~line 195).
- Delete the else-branch `cp .claude/settings.local.json ../.claude/settings.local.json` (~line 198).
- Rewrite the block comment (~lines 185–188) to explain that `settings.local.json` is
  the per-developer personal layer a pull must never create/overwrite/delete, and that
  org-wide permissions live in the two shared templates.
- **Keep** `rm -f ../.claude/settings-hugo.json` (~line 202) — that removes a stale
  *Hugo template*, unrelated to the personal file, still correct.

Resulting branch logic:
```bash
if [ "$IS_HUGO_DOCS" = "true" ] && [ "$IS_JVM" = "false" ]; then
    cp .claude/settings-hugo.json ../.claude/settings.json
else
    cp .claude/settings.json ../.claude/settings.json
fi
rm -f ../.claude/settings-hugo.json
```

### 2. Move the genuinely-shared permissions into the shared templates
Drop `Bash(echo "exit=$?")`. Distribute the rest:
- `.claude/settings.json` `allow`: add `Bash(.agents/skills/version-bumped/scripts/version-bumped.sh)`
  (next to the existing `.agents/skills/.../update_copyright.py` entry) **and** `Skill(pre-pr)`,
  `Skill(pre-pr:*)` (appended after `Bash(./config/migrate)`).
- `.claude/settings-hugo.json` `allow`: add `Skill(pre-pr)`, `Skill(pre-pr:*)` only.
  Not `version-bumped` — a pure-Hugo repo has no `version.gradle.kts`; a Hugo **and** JVM
  repo takes the else branch and receives `settings.json`, so it still gets the guard.

### 3. Gitignore `settings.local.json` in consumers
`.gitignore` (config's dual-purpose baseline that `scripts/update-gitignore.sh` distributes
in its managed block): add `/.claude/settings.local.json` beside the existing
`/.claude/worktrees/` under the "Claude working files" comment. The edit is far from the
`# Secrets` / `!*.gpg` span, so `scripts/test-update-gitignore.sh` invariants are untouched.
Because this file is *also* config's own `.gitignore` (dual-purpose header, lines 27–32),
it ignores the file locally too, enabling change #4.

### 4. Un-track `config`'s own `settings.local.json` (dogfood the contract)
`git rm --cached .claude/settings.local.json` — stages a deletion from the index, leaves the
file on disk (now gitignored via #3). Its org-wide permissions survive because #2 moved them
into `settings.json`.

### 5. Docs — `README.md` (lines ~65–67)
Update the AI-agent-config bullet: `settings.local.json` is no longer distributed; describe
it as Claude Code's gitignored, per-developer personal layer that `pull` never touches.

### 6. `plansDirectory` (confirmed: include now)
Add top-level `"plansDirectory": ".agents/tasks"` to both `.claude/settings.json` and
`.claude/settings-hugo.json`.

## Out of scope / caveat
A consumer that already committed the old config-distributed `settings.local.json` keeps
that now-redundant tracked file until it runs a one-time `git rm --cached
.claude/settings.local.json` itself. `migrate` deliberately does **not** clean it up — the
rule is that a pull must not touch a developer's personal file, and migrate cannot reliably
distinguish an old artifact from a real personal file. Flag this to the team; a content-gated
one-time cleanup could be a later follow-up if wanted.

## Verification
- `bash -n migrate`; reason through all three branches (JVM/mixed else, Hugo-only, plain
  else) — no path references `settings.local.json`, each still writes `settings.json`.
- JSON-validate both edited templates (`python3 -c "import json; json.load(open(...))"`).
- Dry-run into a scratch parent dir: run the settings `cp` block and
  `bash scripts/update-gitignore.sh .gitignore <scratch>/.gitignore`; confirm
  (a) no `settings.local.json` is produced, (b) the merged `.gitignore` contains
  `/.claude/settings.local.json`, (c) `settings.json` carries the moved permissions +
  `plansDirectory`.
- `git status` + `git diff --staged` to show the un-tracking and every edit. **Stage only —
  do not commit or push** (the planning file under `.agents/tasks/` is a work artifact and is
  not part of the PR).
