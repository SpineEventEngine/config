---
slug: share-skills-via-submodule
branch: sharable-skills
owner: claude
status: in-progress
started: 2026-06-02
related-memories: []
---

## Goal

Replace the copy-based distribution of shared agent content (currently the
`migrate` script `cp`-ing `.agents/skills`, `.agents/scripts`, guidelines, and
`.claude` command/agent files into every consumer repo) with a **single shared
Git submodule that floats to latest**. Outcome: shared skills live in one repo,
update everywhere automatically, and **never appear as file churn in any
consumer-repo pull request** — eliminating the noise that today competes with
production/test diffs.

## Decisions (locked with maintainer)

1. **Problem**: any consumer-repo PR touching config-distributed files is noisy.
   Kill the churn.
2. **Tool-agnostic**: real, on-disk skill files must be present for Claude Code,
   Junie, Copilot, and Codex. (A Claude-only plugin marketplace is insufficient;
   GitHub's backend index does not see submodule contents.)
3. **Mechanism**: **Git submodule + symlinks** (not copy-propagation, not subtree).
4. **Source repo**: a **new dedicated repo `SpineEventEngine/agents`**. `config`
   keeps distributing non-agent files (buildSrc, workflows, gradle, `.idea`, root
   docs) via `migrate`.
5. **Read environment**: local/runner checkouts only — so `git submodule update
   --init --remote` can be guaranteed in clones, IDE, CI, and agent runners.
6. **Update tracking**: **float to `agents@master`** via `--remote`. No pinned
   commit bumps land in consumer repos → zero ongoing churn.
7. **Scope**: **all shared `.agents` content** — skills, scripts, guideline
   `*.md` (+ `_TOC.md`, image), templates, plus `.claude/{commands,agents}`. Two
   refinements: **guidelines grouped under `guidelines/`** (one symlink, not 16)
   and **`.agents/project.md` → `docs/project.md`**. Per-repo writable content
   stays local and OUT of the submodule: `docs/project.md`, `.agents/tasks/`,
   `.agents/memory/`.
8. **No pruning**: every repo gets the full shared tree (one `.agents/skills`
   symlink). Irrelevant skills are simply present but never fire.
9. **Authoring / dogfood**: edit skills **in the `agents` repo**; `config` also
   consumes the submodule. One source of truth, zero drift.

## Target consumer-repo layout

    repo/
    ├── docs/project.md                # repo-local; the project description
    └── .agents/
        ├── shared/                    # ← submodule → SpineEventEngine/agents @ master (floats)
        │   ├── skills/  scripts/  guidelines/  claude/{commands,agents}/
        ├── skills      -> shared/skills          guidelines -> shared/guidelines
        ├── scripts     -> shared/scripts         project.md  -> ../docs/project.md
        ├── memory/   tasks/                       # repo-local, writable (NOT in submodule)
    .claude/skills   -> ../.agents/skills          # already a symlink; keeps working (2-hop)
    .claude/commands -> ../.agents/shared/claude/commands
    .claude/agents   -> ../.agents/shared/claude/agents
    .junie/skills    -> ../.agents/skills          # already a symlink; keeps working

Side benefit: `memory/`+`tasks/` are left untouched, so the new `migrate` no
longer `rm -rf`s `.agents` — repo-local memory and live task files stop being wiped.

## Float mechanics & guards

- `.gitmodules`: `branch = master`, `update = checkout`, **`ignore = all`** (the
  floating gitlink never shows dirty / is never accidentally committed as a pin bump).
- Clone: `git clone --recurse-submodules`; existing clones: `git submodule update
  --init --remote`.
- **Agent runners / dev clones / IDE** float via `--remote`. **Classic CI**
  (build/test/links/publish) does not run skills, so it needs no float — pinned
  init (or dangling skill symlinks) is harmless.
- **Token caveat**: if `agents` is **private**, `actions/checkout` with
  `submodules: true` needs a cross-repo PAT/App token. **Public** (recommended,
  Spine is open source) → default `GITHUB_TOKEN` works.

## Sequencing (de-risks the big reference rewrite)

- **Phase 1 — now, independently mergeable, working tree always functional.**
  Regroup guidelines into `.agents/guidelines/`, rewrite references, fix
  `migrate`'s prune paths. `migrate` keeps copying — nothing breaks, no submodule.
- **Phase 2 — prepared now, executed when `SpineEventEngine/agents` exists.**
  New `migrate` (submodule model), adopt script, dogfood `config`, doc rewrites,
  agent-runner init. The `migrate`→submodule rewrite is deferred (it would break
  `migrate` until the remote exists) and fully specified below.

Commit-time PR decomposition (one branch vs two) deferred; no commits without
explicit authorization.

## Plan

### Phase 0 — Create the shared repo (gated: org/GitHub writes by maintainer)
- [ ] Create `SpineEventEngine/agents` (public recommended).
- [ ] Seed it from config's regrouped content **with history** via `git filter-repo`
      (recipe below). Push.
- [ ] Protect `master` (require PR + review — everyone floats to it). Add skill lint/CI.

### Phase 1 — Regroup + reference rewrite (config; do now) ✅ DONE
- [x] `git mv` 16 paths `.agents/<x>` → `.agents/guidelines/<x>`:
      advanced-safety-rules.md, coding-guidelines.md, common-tasks.md,
      documentation-guidelines.md, documentation-tasks.md, jvm-project.md,
      project-structure-expectations.md, quick-reference-card.md,
      refactoring-guidelines.md, running-builds.md, safety-rules.md, testing.md,
      version-policy.md, _TOC.md, project.template.md, widow-runt-orphan.jpg.
- [x] Rewrite references — **3 rules** (full inventory: 26 files / 89 refs):
      - Rewrite every repo-rooted `.agents/<guideline>` → `.agents/guidelines/<guideline>`
        (skills' SKILL.md, AGENTS.md, CLAUDE.md, README.md, .junie/guidelines.md,
        `.agents/scripts/*.sh`, `.claude/commands/*.md`, live `.agents/tasks/*.md`,
        **and the `migrate` script's own `cp`/prune paths** — they contain the token).
      - One manual edit: `.agents/guidelines/project.template.md`'s bare
        `jvm-project.md` link → repo-rooted `.agents/guidelines/jvm-project.md`
        (it is instantiated into `docs/project.md`).
      - Leave unchanged: intra-`guidelines/` bare sibling links (`_TOC.md`'s 13
        links, quick-reference→safety-rules, documentation-guidelines→jpg) and all
        `.agents/tasks/archive/*` (historical records).
- [x] Verify: no stale `.agents/<file>` refs remain; all `.agents/guidelines/<x>`
      targets exist; `migrate` parses (`bash -n`). **Extra fix found in review:**
      `_TOC.md` + `version-policy.md` had relative cross-dir links to `memory/`,
      `tasks/`, `skills/` that broke when those files moved a level deeper —
      repo-rooted them to `.agents/...` (Phase-2-safe: works whether `guidelines/`
      is a real dir or a symlink into the submodule).

### Phase 2 — Submodule wiring (config)
**Prepared now (no working-tree breakage):** ✅ DONE
- [x] `adopt-shared-agents` script (config root) — one-time per consumer; works for
      `config` itself too. Verified: `bash -n` passes. Reachability-guarded; stages,
      never commits.
- [x] New `migrate` spec (above) — to be applied at Phase 2-execute, not now.
- [x] Doc rewrites drafted (paste-ready section above): README.md, AGENTS.md,
      CONTRIBUTING.md, copilot-instructions.md.
- [x] Agent-runner init drafted: Codex (`.codex/hooks.json` paths are symlink-transparent;
      cloud setup floats submodule), Copilot `copilot-setup-steps.yml`.

**Gated (executed when `agents` exists):**
- [ ] Apply new `migrate`. Run `adopt-shared-agents` in `config` (dogfood commit:
      submodule add + flip dirs to symlinks). Commit `.gitmodules` with
      `branch=master, update=checkout, ignore=all`.

### Phase 3 — Roll out (gated)
- [ ] Run `adopt-shared-agents` across SpineEventEngine repos (one PR each).

### Phase 4 — Verify (after `agents` exists)
- [ ] Clone a test consumer `--recurse-submodules`; run new `migrate`; confirm
      `.agents/skills|guidelines`, `.claude/commands` resolve and skills load in
      Claude, Junie, Copilot, Codex.
- [ ] Edit a skill on `agents@master`; `git submodule update --remote` in consumer;
      confirm the change appears with **zero** consumer commits (the noise-kill proof).
- [ ] Confirm a Hugo-only and a JVM repo both work (no pruning). Windows symlink check.

## `git filter-repo` extraction recipe (Phase 0)

    git clone https://github.com/SpineEventEngine/config.git agents-seed
    cd agents-seed && git checkout sharable-skills      # branch with regrouped guidelines
    git filter-repo \
      --path .agents/skills/ --path .agents/scripts/ --path .agents/guidelines/ \
      --path .claude/commands/ --path .claude/agents/ \
      --path-rename .agents/skills/:skills/ \
      --path-rename .agents/scripts/:scripts/ \
      --path-rename .agents/guidelines/:guidelines/ \
      --path-rename .claude/commands/:claude/commands/ \
      --path-rename .claude/agents/:claude/agents/
    git remote add origin git@github.com:SpineEventEngine/agents.git && git push -u origin master

Result layout in `agents`: `skills/ scripts/ guidelines/ claude/{commands,agents}/`,
with history preserved.

## New `migrate` spec (Phase 2-execute)

Keep: project-type detection; common copies (`.idea`, CONTRIBUTING, CODE_OF_CONDUCT,
AGENTS.md, CLAUDE.md, `.junie/guidelines.md`, `copilot-instructions.md`,
`.gitattributes`/`.gitignore`); JVM/Hugo **non-agent** file copies (buildSrc, gradle,
workflows, codecov, lychee).

Replace the `.agents` + `.claude` copy/prune blocks with:
1. Ensure submodule: if `.gitmodules` lacks `.agents/shared`, `git submodule add -b
   master <agents-url> .agents/shared` (clear error if remote unreachable); then
   `git submodule update --init --remote .agents/shared`.
2. (Re)create symlinks: `.agents/skills→shared/skills`, `.agents/scripts→shared/scripts`,
   `.agents/guidelines→shared/guidelines`, `.claude/commands→../.agents/shared/claude/commands`,
   `.claude/agents→../.agents/shared/claude/agents`, ensure `.claude/skills→../.agents/skills`
   and `.junie/skills→../.agents/skills`.
3. project.md: if `docs/project.md` absent, seed from
   `.agents/shared/guidelines/project.template.md`; ensure `.agents/project.md→../docs/project.md`.
4. Do NOT `rm -rf .agents` (preserves repo-local `memory/`+`tasks/`). Drop all pruning.

## Phase 2 — paste-ready drafts (apply at Phase 2-execute, once `agents` exists)

Not applied now: they'd be inaccurate until `migrate` switches to the submodule
and the `agents` repo exists. `adopt-shared-agents` (config root) is already written.

**README.md — replace the "AI agent configuration" section** with the submodule model:
> The `pull` script also wires up AI-agent configuration. `AGENTS.md` and `CLAUDE.md`
> are copied as before. The shared agent content — `.agents/skills`, `.agents/scripts`,
> `.agents/guidelines`, and `.claude/commands`/`.claude/agents` — is **not copied**; it
> comes from the `SpineEventEngine/agents` repository mounted as a floating Git submodule
> at `.agents/shared`, exposed through symlinks. `pull` runs `git submodule update
> --remote .agents/shared`, so every pull gets the latest shared skills with no file
> churn in your repo. Per-repo content stays local: `docs/project.md` (linked from
> `.agents/project.md`), `.agents/memory/`, and `.agents/tasks/`. First-time adoption is
> a one-time `./config/adopt-shared-agents` run.

**AGENTS.md — add an "Initializing shared content" note** near Orientation:
> Shared skills/scripts/guidelines live in the `.agents/shared` submodule. `./config/pull`
> initializes and floats it. On a fresh clone that skips `pull`, run
> `git submodule update --init --remote --recursive`.

**CONTRIBUTING.md — IDE one-liner:** enable JetBrains *Settings → Version Control → Git →
Update submodules on pull/checkout* so the IDE floats `.agents/shared` automatically.

**copilot-instructions.md — wording:** "managed by the `config` submodule" →
"managed by the `config` and `agents` submodules" (the skip list is otherwise unchanged —
consumers still skip `.agents/**`, `.claude/**`).

**Agent-runner init** (only fresh clones that skip `pull` need this — CI doesn't use skills):
- Codex: hook paths in `.codex/hooks.json` resolve through the `.agents/scripts` symlink
  unchanged. For cloud runs, the setup/maintenance step should
  `git submodule update --init --remote --recursive`.
- Copilot coding agent: add `.github/workflows/copilot-setup-steps.yml` with a step
  `git submodule update --init --remote --recursive`.
- Same for `.claude/settings.json` hooks — `.agents/scripts/*` paths are symlink-transparent.

## Trade-offs / risks
- One-time large adoption commit per repo (deletes copies, adds submodule). One-time only.
- No per-repo pinning — `agents@master` reaches everyone on next checkout (mitigate:
  branch protection + review in `agents`).
- Guideline path change rewrites refs (Phase 1, mechanical).
- Windows symlink+submodule checkout to verify (`build-on-windows.yml`).

## Open knobs (defaults chosen; veto welcome)
- A. Move `.claude/{commands,agents}` into the submodule — default **yes**.
- B. Preserve git history via `git filter-repo` — default **yes**.
- C. Names: repo `agents`, mount `.agents/shared`, dir `guidelines`.

## Log
- 2026-06-02 — Drafted after requirements interview.
- 2026-06-02 — Plan approved (Phase 1 now / Phase 2 prepared+gated). Reconciled this
  doc with the approved plan; status → in-progress. Working on branch `sharable-skills`.
  Starting Phase 1.
- 2026-06-02 — **Phase 1 complete & verified.** `git mv` 16 files into
  `.agents/guidelines/` (history preserved); rewrote 68 repo-rooted `.agents/<g>`
  refs across 25 files via one scoped pass + the `project.template.md` link-target
  special case; fixed 11 cross-dir links in `_TOC.md`/`version-policy.md`. All link
  targets resolve, `migrate` parses, no stale refs. No `version.gradle.kts` in config
  → version gate N/A (docs-only, no build). Starting Phase 2 prep.
- 2026-06-02 — **Phase 2 prep complete.** Wrote `adopt-shared-agents` (config root,
  `bash -n` clean). Added paste-ready Phase 2 drafts (README/AGENTS/CONTRIBUTING/
  copilot-instructions + runner init) and the full new-`migrate` spec to this doc.
  Confirmed `config` is itself a submodule (per `pull`), so consumers already run
  `git submodule update` — the agents submodule rides the existing infra. Remaining
  work is all gated on creating `SpineEventEngine/agents` (Phase 0) + the dogfood
  apply (Phase 2-execute).
