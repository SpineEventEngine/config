---
slug: reusable-publishing-workflow
branch: reusable-publishing-workflow
owner: claude
status: in-review
started: 2026-09-29
---

## Goal

Move the job body of the distributed `publish.yml` into a reusable workflow hosted by
`config` — `.github/workflows/publishing.yml` — in the two-job shape the SLSA GitHub
builder uses, so that the attestation of every artifact a consumer publishes names
`config` as the signer and meets SLSA v1.0 Build Level 3 both as GitHub documents it
and as the SLSA requirements text reads. The distributed `publish.yml` shrinks to a
thin caller: trigger, permission grant, secrets, and one `uses:` line.

Follows up `attest-published-artifacts` (PR #763), which introduced the attest step
in its Build Level 2 shape.

## Context

- GitHub: attestations alone give Build L2; a reusable workflow that *builds and
  attests* gives L3. Docs: [increase-security-rating][gh-l3], [reuse-workflows][gh-reuse].
- Mechanism: the Sigstore certificate's *Build Signer URI* is the OIDC claim
  `job_workflow_ref`, which for a called workflow names the **called** file, while
  *Build Config URI* (`workflow_ref`) and *Source Repository URI* (`repository`) stay on
  the caller. `gh attestation verify --signer-workflow <owner>/<repo>/<path>` pins the
  signer by prefix, so the `@<ref>` suffix does not matter to verification;
  `--signer-digest` pins the exact config commit.
- Read against the [SLSA v1.0 requirements][slsa-req], the single-job shape GitHub
  documents leaves two gaps, both closed here:
  - *Isolated*: "It MUST NOT be possible for one build to inject false entries into a
    build cache used by another build." GitHub caches are shared across workflows on
    the same branch; `setup-gradle` writes from default-branch jobs and its restore
    fallback reaches "same OS"; `gradle.properties` sets `org.gradle.caching=true`, so
    the cached Gradle home carries task outputs. A consumer-owned `build-on-ubuntu.yml`
    run on `master` can thus feed the trusted publish job.
  - *Unforgeable*: signing material "MUST NOT be accessible to the environment running
    the user-defined build steps." `id-token: write` is job-scoped and the runner
    exposes the token endpoint to every step, so in one job the `./gradlew` step —
    which executes the consumer's build scripts and the `buildSrc` its submodule pins —
    can mint a token naming config as signer.
- Reference implementation: [`builder_go_slsa3.yml`][slsa-go] — the `build` job
  declares no `id-token`, the `provenance` job holds it, the digest crosses as a job
  output named `UNTRUSTED_BINARY_HASH`. [SPECIFICATIONS.md][slsa-spec] calls job
  outputs "a trusted channel … using namespaces that identify the exact job".
- `migrate` copies **both** `.github-workflows/` and config's own `.github/workflows/`
  into JVM consumers (`migrate:368-370`, with `detekt-code-analysis.yml` removed
  afterwards), so the new file needs an explicit exclusion or it is distributed.
- `config` is public, so its reusable workflows are callable from any repository
  without an access setting.
- `on: workflow_call:` is the sole trigger of the new file; GitHub creates no run for
  it on config's own events, only parses it.
- Org scan (2026-09-29) of all 28 `publish.yml` files in SpineEventEngine: the template
  has never carried per-consumer parameters. 15 active repos hold the pre-#763 template
  verbatim; model-tools, template, doc-tools and gradle-execfork-plugin hold 2021–2022
  templates (Java 8/11) because they have not pulled config for years; the two Chords
  repos carry one hand edit (`-x updateGitHubPages`) that the next pull erases under
  today's design too; spine-ts, validation-ts and embed-code-gradle-plugin run their own
  non-template workflows. No consumer uses `config:replaces publish.yml`.

## Design decisions

1. **Two jobs in `publishing.yml`.** `publish` (`contents: read`, `packages: write`)
   does checkout, setup, decryption, `./gradlew publish publicationChecksums -x test`,
   the failure-report step, and exports the manifest. `attest` (`needs: publish`;
   `contents: read`, `id-token: write`, `attestations: write`,
   `artifact-metadata: write`) writes the manifest to disk and runs `actions/attest@v4`
   with `subject-checksums`. No step that runs consumer code holds the signing
   credential; the signer runs no consumer code. `needs:` preserves "publish before
   attest, never attest a failed publication".
2. **Manifest crosses jobs as a job output**, not through the run's artifact store.
   Outputs are bound to the job that set them; the artifact store is run-scoped and
   writable by any job in the run, including jobs a consumer adds to its caller. The
   manifest is kilobytes; the output limit is 1 MB and GitHub fails loudly above it.
   Multiline value via `$GITHUB_OUTPUT` with a random delimiter.
3. **`setup-gradle` with `cache-disabled: true`** in `publish`. The job starts from an
   empty Gradle home; `org.gradle.caching=true` stays and only repopulates a local
   cache nobody else reads. Cost: cold dependency download once per `master` merge.
4. **Caller pins the reusable workflow by SHA, stamped by `migrate`.** The template
   carries the placeholder `publishing.yml@CONFIG_COMMIT`; `migrate` replaces it with
   `git rev-parse HEAD` of the config checkout, which is the commit the consumer pins
   as its submodule. Workflow steps and `buildSrc` then move together, the reference is
   immutable (GitHub's recommendation), and the diff lands in the same commit as the
   submodule bump consumers already make. If no `config` commit can be resolved — the
   directory is not a work tree of its own, or has no commit — the pull fails: a mutable
   ref would hand every consumer secret to whatever `master` holds at run time (the
   draft's warn-and-pin-`master` fallback was dropped after Copilot's review on #776).
   Every pull rewrites the SHA — accepted at approval.
5. **`secrets: inherit`** in the caller. The called job needs five decryption keys
   plus `NPM_SECRET`, all defined per consumer; listing them in every thin caller
   would recreate per-repo churn. `GITHUB_TOKEN` is passed automatically.
6. **No `workflow_call` inputs.** Java version and the Gradle invocation stay fixed as
   today; parametrisation is a separate task if a consumer ever needs it.
7. **`publishing.yml` is never distributed.** `copy_workflows` gains a skip list of
   config-only workflows (`detekt-code-analysis.yml`, `publishing.yml`), replacing the
   copy-then-`rm -f` for detekt.
8. **No `workflow_call` inputs** (decided at approval, 2026-09-29). Per-consumer values —
   the repository slug and the secrets — flow through the `github` context and
   `secrets: inherit`. The Java version is a property of the config commit, which the
   stamped SHA already pins, so exposing it would only create mismatches. Inputs with
   defaults can be added later without touching stamped callers.
9. **Comments are ported, not rewritten.** The `permissions` rationale, the
   single-invocation rationale for `publicationChecksums`, the failure-report text, and
   the rejected attest-only path move into `publishing.yml`; the two-job and cache
   rationales are added in the same voice.

### Shape of `publishing.yml`

```yaml
on:
  workflow_call:

jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    outputs:
      manifest: ${{ steps.manifest.outputs.manifest }}
    steps:
      # checkout (submodules), setup-java, setup-gradle (cache-disabled: true),
      # five decrypt steps, chmod, append Gradle properties,
      # Publish artifacts to Maven (id: publish), Report a failed publication,
      - name: Export the attestation manifest
        id: manifest
        run: |   # heredoc into $GITHUB_OUTPUT with a random delimiter

  attest:
    needs: publish
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write
      attestations: write
      artifact-metadata: write
    steps:
      - name: Write the attestation manifest
        env:
          MANIFEST: ${{ needs.publish.outputs.manifest }}
        run: |
          mkdir -p build/attestation
          printf '%s\n' "$MANIFEST" > build/attestation/subject-checksums.txt
      - uses: actions/attest@v4
        with:
          subject-checksums: build/attestation/subject-checksums.txt
```

### Shape of the distributed `publish.yml`

```yaml
name: Publish

on:
  push:
    branches: [master]

jobs:
  publish:
    name: Publish to Maven repositories
    uses: SpineEventEngine/config/.github/workflows/publishing.yml@CONFIG_COMMIT
    permissions:
      contents: read
      packages: write
      id-token: write
      attestations: write
      artifact-metadata: write
    secrets: inherit
```

## Plan

- [x] 1. Create `.github/workflows/publishing.yml` per the shape above, porting the
      comments from the current template.
- [x] 2. Reduce `.github-workflows/publish.yml` to the caller, with a comment
      explaining the placeholder and where the job body now lives.
- [x] 3. `migrate`:
      - skip list for config-only workflows in `copy_workflows`;
      - stamp `@CONFIG_COMMIT` in the copied `publish.yml` (portable: temp file + `mv`,
        no `sed -i`); fail loudly if the placeholder survives or no commit resolves.
      - checked-in regression test `scripts/test-migrate-publishing-pin.sh`, modeled on
        `scripts/test-migrate-ide-files.sh`: skip list, pin, re-pin, `config:replaces`,
        fail-closed path.
- [x] 4. README:
      - refine "`.github-workflows` directory": a third category — workflows config
        hosts for consumers, triggered only by `workflow_call`, living in
        `.github/workflows/` and excluded from distribution;
      - add "Verifying published artifacts" with the `--signer-workflow` command and
        a note that versions published before this change verify with `-R` alone.
- [x] 5. Static checks: parse both YAML files; `actionlint` (install via Homebrew if
      absent); `shellcheck migrate` if available.
      - done: YAML parse (system Ruby), `bash -n migrate`, six sandbox cases for the
        new `migrate` functions, `actionlint` 1.7.12 with the `shellcheck` integration on
        both workflow files, `shellcheck` 0.11.0 on `migrate` — no findings (see Log).
- [x] 6. Read-only prerequisite check: branch protection on config `master`
      (`gh api repos/SpineEventEngine/config/branches/master/protection`); record the
      result in the Log. With `@<sha>` stamping this is defence in depth; with
      `@master` it would be the whole guarantee.
- [x] 7. `/pre-pr` — YAML, shell and Markdown only, so no Gradle build; reviewers
      `review-docs` and `spine-code-review`.
      - run in-session against the working tree (see Log); the `pre-pr.ok` sentinel is
        written after the commit, since it binds to the HEAD SHA.
- [ ] 8. Open the PR; description from this file.
- [ ] 9. Rollout verification, after merge, in one low-traffic JVM consumer:
      `./config/pull`, confirm the stamped SHA, merge; confirm the publish run shows
      `publish` and `attest` as separate jobs with `attest` green; download one
      published artifact and verify it with the signer pinned:

      ```bash
      gh attestation verify -R <owner>/<repo> \
        --signer-workflow SpineEventEngine/config/.github/workflows/publishing.yml <file>
      ```

      Then assert the signer positively from the certificate, not from a negative:
      add `--format json` and check, under
      `.[].verificationResult.signature.certificate`, that `buildSignerURI` is
      `https://github.com/SpineEventEngine/config/.github/workflows/publishing.yml@<ref>`,
      `sourceRepositoryURI` is the consumer, and `buildSignerDigest` is the pinned
      `config` commit. (`-R` alone is expected to fail too — the CLI derives the identity
      regex `^https://github.com/<owner>/<repo>/` from `--repo`, and config's signer does
      not match it — but the rollout does not rest on that.) Record the output in the Log.

## Risks

- **A missed exclusion distributes `publishing.yml`** to every consumer on the next
  pull. Noise, not a security issue: a `workflow_call`-only file does nothing there.
- **Existing verification instructions break** for versions built after the switch;
  the README section in step 4 is the mitigation.
- **Consumers with a `config:replaces publish.yml` override** keep their inline job and
  stay at Level 2. Enumerate them during rollout; migrating them is out of scope.
- **`secrets: inherit` hands every consumer secret to config-authored steps.** This is
  the trust consumers already extend to config through `buildSrc`, which runs in their
  CI with the same secrets; the reusable workflow does not widen it.
- **Caller grant must cover the called jobs.** A called workflow can only keep or
  reduce permissions, so `artifact-metadata: write` and the rest must be in the thin
  caller or `attest` fails.
- **Cold builds on every publish.** Dependency download replaces the cache restore;
  minutes, once per merge to `master`.
- **A stamped SHA that becomes unreachable** (a config PR-branch commit whose branch
  was deleted without a merge commit) fails the consumer's publish with "workflow not
  found" — the same failure class as an unfetchable submodule commit, not a new one.
- **Unchanged from #763:** publication precedes attestation. A failure in `publish`
  after the uploads — `publicationChecksums` — leaves the version published and
  unattested, and the fix is to publish the next version. A failure in `attest` is
  recoverable in place: "Re-run failed jobs" re-attests from the retained manifest.

## Non-goals

- `workflow_call` inputs. Follow-up candidate: an optional `extra-gradle-args` input,
  so a consumer with a Chords-style exclusion can stay on the trusted workflow instead
  of falling back to an inline Level 2 workflow.
- Gradle dependency verification (`gradle/verification-metadata.xml`) — worthwhile,
  separate task.
- Migrating consumers that override `publish.yml`.
- Attesting anything other than what `publicationChecksums` already lists.

## Log

- 2026-09-29 — drafted after a five-step walkthrough of the Level 3 mechanism with
  Alexander; two-job shape chosen over GitHub's single-job example; awaiting approval.
- 2026-09-29 — org scan of consumer `publish.yml` files; no inputs in this version.
  Plan approved (plan mode), SHA stamping confirmed; executing.
- 2026-09-29 — branch `reusable-publishing-workflow` created. Files written: the two-job
  `publishing.yml`, the thin `publish.yml`, `migrate` (skip list + `pin_publishing_workflow`),
  README (template section refined, "Verifying published artifacts" added).
- 2026-09-29 — `migrate` functions exercised against a sandbox consumer in the scratchpad:
  config-only files skipped while others copy; pin writes the 40-char SHA, keeps mode 0644,
  leaves no temp file; second run is a no-op; outside a git tree warns and pins `master`;
  missing target is a silent no-op; a surviving placeholder fails loudly. First run caught
  a real defect: the post-check grepped the bare word `CONFIG_COMMIT`, which the template's
  own comment contains — narrowed to the `publishing.yml@CONFIG_COMMIT` pattern.
- 2026-09-29 — step 6: no classic branch protection on config `master`, but a repository
  ruleset (id 16355142) enforces pull requests with 1 approving review, blocks deletion
  and non-fast-forward pushes, and requires the "Run detekt" status check (strict). With
  SHA-stamped callers this is defence in depth, as planned.
- 2026-09-29 — re-run rule behind the `attest` comment confirmed: on "Re-run failed jobs"
  GitHub re-runs the failed jobs and splices in the outputs of the jobs that succeeded
  (GitHub changelog 2022-03-17, community discussion 52505).
- 2026-09-29 — pre-PR pass. Scope: config itself, version gate not applicable, no Hugo
  site, no Gradle command for YAML/shell/Markdown (`build_status=skipped`). Reviewers run
  in-session rather than as subagents (team memory: reviewer subagents with a shell have
  committed and pushed before, and worktree isolation cannot see uncommitted changes).
  `review-docs`: no findings on README or this file. `spine-code-review` on `migrate` and
  the workflows: four findings, all fixed before presenting — two new `echo` lines over
  100 chars wrapped as two-argument `echo`; `cp` moved into the success condition so a
  failed copy cannot print "Pinned"; `A && B || return` rewritten as an `if`; a
  permissions comment softened from "requires" to "completes the grant GitHub documents".
  Ported lines (decrypt commands, failure-report echoes) left verbatim over the limit.
  Verdict: PASS pending the sentinel. `actionlint`/`shellcheck` still unavailable locally.

[gh-l3]: https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/increase-security-rating
[gh-reuse]: https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows
[slsa-req]: https://slsa.dev/spec/v1.0/requirements
[slsa-go]: https://github.com/slsa-framework/slsa-github-generator/blob/main/.github/workflows/builder_go_slsa3.yml
[slsa-spec]: https://github.com/slsa-framework/slsa-github-generator/blob/main/SPECIFICATIONS.md
- 2026-09-29 — committed on `reusable-publishing-workflow`; PR opened against `master`.
- 2026-09-29 — Alexander accepted the Xcode license; `brew install actionlint shellcheck`
  succeeded. `actionlint -no-color` on `publishing.yml` and the `publish.yml` template, and
  on all live workflows: exit 0, no findings. `shellcheck -s bash migrate`: exit 0.
- 2026-09-29 — Copilot review on #776, five inline findings, all applied: the
  warn-and-pin-`master` fallback replaced by a fail-closed check that `config` is a work
  tree of its own with a resolvable HEAD; `scripts/test-migrate-publishing-pin.sh` added
  (runs the real `migrate` against a fixture, like the IDE-files harness); the README no
  longer promises an attestation for a run whose `attest` job failed, and distinguishes
  pre-attestation versions from inline-attested ones; this file's risk entry now matches
  the re-run recovery. Codex reviewed the same commit with no findings.
- 2026-09-29 — Codex on `f02838e9`: replace the "`-R` alone must fail" rollout expectation
  with a positive check of the certificate identity. Adopted the positive check. The
  premise that `-R` alone accepts the new attestation is not borne out by the CLI's
  `policy.go`, which builds the SAN regex from `--repo`; Fulcio's SAN template for GitHub
  Actions is `{{ .url }}/{{ .job_workflow_ref }}`, so the signer is config's file.
