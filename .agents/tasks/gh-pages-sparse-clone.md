---
slug: gh-pages-sparse-clone
branch: claude/publish-api-docs-a78bfa
owner: claude
status: in-review
started: 2026-10-22
---

## Goal

Publishing the API docs of a module clones only what it touches. Before this
change, each module's `publishApiDocs` cloned the whole repository and checked
out the whole `gh-pages` branch, which holds the docs of every version of every
module. That was slow and, with modules running in parallel, could exhaust a CI
runner's disk.

## Context

Measured on core-jvm (392 MB packed on GitHub, 7 documented modules), 2026-10-22,
for one module, `core`, in both formats:

| Step | Before | Shallow + partial + sparse |
|---|---|---|
| Clone | 25 s (`master`, full history) | 6 s (`gh-pages` tip, `.git` 157 MB) |
| Check out `gh-pages` | 70 s, 12 GB / 694k files | 4 s, 2,495 files |
| `git add --all` + `commit`, each of 2 | 5.6 s + 3.1 s | 0.35 s + 0.35 s |
| Disk | ~12.5 GB | 197 MB |

- The "after" column runs the exact git sequence of the code; the `add` and
  `commit` timings come from an earlier probe of one format.
- History is not the cost: a shallow `gh-pages` clone still took 92 s and 12 GB,
  as the tip itself holds every `v/<version>` folder (`javadoc/server/v`: 1.8 GB).
- A GitHub-hosted runner has about 14 GB free; with `org.gradle.parallel`, several
  modules clone at once.
- A commit in the sparse clone (one changed file, one new version folder) held
  exactly those two paths and deleted none of the files left out of the checkout.
- `git ls-tree` lists the 57 published versions of a format from the trees in
  0.01 s; a blob-less clone still has all trees.
- Before this change, only `forPublishingDocumentation` called `Repository.clone`.

## Plan

- [x] `Repository` (`io.spine.gradle.git`):
  - new factory `cloneSparsely(project, sshUrl, user, branch, initialFiles)`:
    `git ls-remote` against the URL first; an existing branch is cloned with
    `--depth 1 --filter=blob:none --sparse --single-branch --branch <branch>`;
    a missing one starts from such a clone of the default branch, then the
    existing orphan-branch creation seeds it (with the `CNAME`) and pushes it;
    a failed clone deletes its temporary folder;
  - `checkOutOnly(patterns)`: `git sparse-checkout set --no-cone <patterns>`,
    which replaces the paths checked out before;
  - `listFolders(folder)`: the names of the folders in a folder of the `HEAD` tree,
    via `git ls-tree -z`, skipping files;
  - adoption of a branch another module created first adds it to the branches
    fetched from `origin` (`git remote set-branches --add`) and fetches it
    explicitly: a single-branch clone's `git fetch origin` does not bring it, and
    Git refuses an upstream it does not fetch;
  - `repoExecute` takes a command as a list too, so that the commands assembled
    from options and patterns need no spread operator (detekt's `SpreadOperator`).
- [x] `DocsPublication`: before publishing, check out only the module's most recent
      docs and its `v/<version>/` folder for both formats, with
      `docsCheckoutPatterns`; list the published versions for the newest-version
      guard from the tree, as the folders of the other versions are no longer on
      disk. `forPublishingDocumentation` uses `cloneSparsely`.
- [x] `Cli`: keep the line breaks of a command's output. It appended the lines
      without them, so a listing came back as one string. It also takes a command
      as a list, which `ProcessBuilder` accepts as it is.
- [x] Tests (`RepositorySpec`), against a local bare remote reached by a `file:`
      URL, with partial clones allowed:
  - the sparse clone checks out only the top-level files;
  - the patterns check out only one module's docs, and the folder of a version
    published before when it is published again;
  - `listFolders` reads the versions from the tree and skips a file next to them;
  - a commit touches only its paths;
  - `push()` rebases onto a commit another clone pushed meanwhile;
  - a missing branch is created with its `CNAME` and pushed to;
  - a branch that another publication creates first, forced by a `pre-receive`
    hook, is adopted and pushed to, in a sparse clone and in a full one;
  - a version published before is checked out and updated in place.
  `CliSpec` pins the output with its line breaks and the failure message.
  The real `docsCheckoutPatterns` drives the checkout cases.
- [x] Verify: `:buildSrc:build detekt`, reviewers; no push to any real `gh-pages`.

## Not in this change

One clone and one push per build. With sparse clones a module costs about 12 s;
sharing one clone saves about 6 s per further module, but needs a shared build
service, serialized commits and a separate push task, and changes what running
one module's `publishApiDocs` means.

## Log

- 2026-10-22 — measured; drafted. Leaves the changes uncommitted on top of the
  `DocsPublication` rework, for the user's review.
- 2026-10-22 — plan approved; the `DocsPublication` rework committed first.
- 2026-10-22 — implemented. `RepositorySpec` caught two bugs before any real run:
  `Cli` dropped line breaks (fixed there), and a single-branch clone could not
  set an adopted branch as the upstream (`remote set-branches --add`).
- 2026-10-22 — re-checked read-only against core-jvm with the exact sequence of
  the code: `ls-remote`, sparse clone 6 s, checkout of `core` in both formats 4 s,
  11 s in all; 2,495 files, 197 MB; 57 versions per format listed from the tree,
  none of their folders checked out.
- 2026-10-22 — `kotlin-engineer` and `review-docs` findings applied: named
  arguments, `checkOutOnly` and `listFolders` (folders only, so a stray file
  under `v/` cannot pass for the newest version), cleanup of a failed clone,
  `mkdirs()`, one constant for `v`, `--` before the URL, KDoc corrections. The
  claim that a pattern cannot re-include a path under an excluded folder holds
  for `.gitignore`, not for sparse checkout; tested and dropped. The layout of the
  patterns stays as cone mode writes it.
- 2026-10-22 — `spine-code-review` findings applied: the existing-version case,
  adoption through the full `Repository.clone` too, and on to a push; the
  non-cone mode and its minimum Git (2.35) noted; `CliSpec`. Final check:
  `:buildSrc:build detekt` green, 315 tests; nothing committed, for review.
