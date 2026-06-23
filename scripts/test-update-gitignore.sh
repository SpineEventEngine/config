#!/usr/bin/env bash

#
# Copyright 2026, TeamDev. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Redistribution and use in source and/or binary forms, with or without
# modification, must retain the above copyright notice and the following
# disclaimer.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
# LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#

# ---------------------------------------------------------------------------
# Regression test for `config/scripts/update-gitignore.sh` (called by `migrate`).
#
# `update_gitignore` writes the shared `.gitignore` baseline inside a managed
# block, then preserves a consumer's own repo-local pattern lines in a trailing
# section. Because `.gitignore` is LAST-MATCH-WINS, a preserved repo-local
# negation such as `!*.json` or `!spine-dev.json` — placed after the managed
# secret patterns — would otherwise un-ignore a decrypted credential and re-expose
# it for commit. That re-exposure is precisely what the `prevent-secret-commits`
# work exists to stop. To defend against it, `update_gitignore` re-asserts the
# baseline's Secrets section as the FINAL section of the merged file.
#
# This test exercises the REAL function (extracted from the live `migrate` file,
# not a copy) against the REAL baseline (`config/.gitignore`), and asserts:
#   1. decrypted credentials stay ignored even with `!*.json` + `!spine-dev.json`
#      present as preserved repo-local negations;
#   2. the committed ENCRYPTED form (`*.gpg`) stays committable;
#   3. a benign repo-local rule (`my-build-output/`) is still honored;
#   4. the merge is idempotent (re-running yields byte-identical output);
#   5. the baseline still carries the `# Secrets` header and a trailing `!*.gpg`
#      that the re-assertion depends on (guards against silent baseline drift).
#
# Real ignore status is asserted with `git check-ignore -q` (and cross-checked
# with `git status --porcelain --ignored`), NOT `git check-ignore -v`: `-v` exits
# 0 even when the matching line is a NEGATION, so it would falsely report a
# re-included secret as "matched".
#
# Self-contained: no bats / external test framework required. Exits 0 on success,
# 1 on any failure. Run from anywhere:  bash config/scripts/test-update-gitignore.sh
# ---------------------------------------------------------------------------

set -eo pipefail

# Resolve the repo's `config` directory from this script's location
# (scripts/ lives directly under it), so the test works regardless of CWD.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
config_dir="$(cd "$script_dir/.." && pwd)"
script="$config_dir/scripts/update-gitignore.sh"
baseline="$config_dir/.gitignore"

BEGIN='# >>> shared config (managed by ./config/pull -- do not edit inside this block) >>>'
END='# <<< shared config <<<'

fail=0
pass() { echo "PASS: $1"; }
f-ail() { echo "FAIL: $1" >&2; fail=1; }

# --- Pre-flight: the script and baseline must be present. ---------------------
[ -f "$script" ]   || { echo "FAIL: cannot find update-gitignore.sh at $script" >&2; exit 1; }
[ -f "$baseline" ] || { echo "FAIL: cannot find baseline at $baseline" >&2; exit 1; }

# --- Guard the baseline invariants the re-assertion relies on. -----------------
# The trailer re-lists the Secrets section's positive patterns, anchored on a
# `# Secrets` header line to start and the closing `!*.gpg` line to mark the
# section end. If either disappears (or `!*.gpg` stops closing the section), the
# protection silently weakens — so assert their presence here.
grep -qE '^# Secrets'  "$baseline" || f-ail "baseline lost its '# Secrets' header line"
grep -qxF '!*.gpg'     "$baseline" || f-ail "baseline lost its trailing '!*.gpg' negation"

# --- Build a sandbox mirroring migrate's assumed layout. ----------------------
# update_gitignore hardcodes src=".gitignore", dest="../.gitignore" relative to
# its CWD, so we run it from a `config/` subdir of a throwaway consumer repo.
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
consumer="$work/consumer"
mkdir -p "$consumer/config"
cp "$baseline" "$consumer/config/.gitignore"

git -C "$consumer" init -q
git -C "$consumer" config user.email test@example.com
git -C "$consumer" config user.name  test

# Pre-existing consumer .gitignore: a prior managed block (the full baseline) plus
# a repo-local section carrying dangerous negations AND a benign rule.
{
  printf '%s\n' "$BEGIN"
  cat "$baseline"
  printf '%s\n' "$END"
  printf '\n# --- repo-local entries (preserved across ./config/pull) ---\n'
  printf '%s\n' '!*.json'
  printf '%s\n' '!spine-dev.json'
  printf '%s\n' 'my-build-output/'
  printf '%s\n' 'generated/*.gpg'
  printf '%s\n' '*.jar'
  printf '%s\n' '!gradle-wrapper.jar'
} > "$consumer/.gitignore"

# --- Run the merge. -----------------------------------------------------------
( cd "$consumer/config" && bash "$script" )

# --- Assert real ignore status. -----------------------------------------------
cd "$consumer"
secrets=(spine-dev.json my-sa.json my-service-account-x.json my.secret.properties)
for f in "${secrets[@]}"; do : > "$f"; done
: > keep.gpg
mkdir -p my-build-output && : > my-build-output/artifact.txt
mkdir -p generated && : > generated/secret.gpg
: > gradle-wrapper.jar
: > other.jar

# (1) Every decrypted credential must be ignored despite the repo-local negations.
for f in "${secrets[@]}"; do
  if git check-ignore -q "$f"; then
    pass "decrypted credential ignored: $f"
  else
    f-ail "decrypted credential RE-EXPOSED (committable): $f"
  fi
done

# Cross-check one secret via porcelain: it must be ignored (!!), not untracked (??).
status_sd="$(git status --porcelain --ignored -- spine-dev.json)"
case "$status_sd" in
  '!!'*) pass "git status marks spine-dev.json ignored (!!)";;
  '??'*) f-ail "git status marks spine-dev.json UNTRACKED/committable (??)";;
  *)     f-ail "git status unexpected for spine-dev.json: [$status_sd]";;
esac

# (2) The committed ENCRYPTED form must remain committable.
if git check-ignore -q keep.gpg; then
  f-ail "encrypted form keep.gpg is ignored, but *.gpg must stay committable"
else
  pass "encrypted form keep.gpg is committable"
fi

# (3) A benign repo-local rule must still be honored.
if git check-ignore -q my-build-output/artifact.txt; then
  pass "benign repo-local rule 'my-build-output/' honored"
else
  f-ail "benign repo-local rule 'my-build-output/' was dropped"
fi

# (3b) A repo-local *scoped* `.gpg` ignore must survive: the secret trailer
# re-asserts only positive patterns (not the baseline `!*.gpg`), so it must NOT
# re-include a consumer's own `generated/*.gpg` artifacts. (`keep.gpg` at the root
# stays committable above — the managed block's `!*.gpg` still applies there.)
if git check-ignore -q generated/secret.gpg; then
  pass "repo-local 'generated/*.gpg' honored (trailer did not re-include it)"
else
  f-ail "repo-local 'generated/*.gpg' overridden by the secret trailer"
fi

# (3c) A repo-local broad ignore plus an exception that ALSO appears in the
# baseline must survive: `*.jar` + `!gradle-wrapper.jar`. The de-dup drops only
# positive duplicates, keeping the negation, so the wrapper jar stays committable
# while other jars are ignored.
if git check-ignore -q other.jar && ! git check-ignore -q gradle-wrapper.jar; then
  pass "repo-local '*.jar' + '!gradle-wrapper.jar' exception preserved"
else
  f-ail "repo-local '!gradle-wrapper.jar' exception dropped (wrapper jar wrongly ignored)"
fi

# --- (4) Idempotency: re-running yields byte-identical output. -----------------
cp "$consumer/.gitignore" "$work/first.gitignore"
( cd "$consumer/config" && bash "$script" )
if diff -q "$work/first.gitignore" "$consumer/.gitignore" >/dev/null; then
  pass "merge is idempotent (2nd run identical)"
else
  f-ail "merge is NOT idempotent (2nd run differs)"
  diff "$work/first.gitignore" "$consumer/.gitignore" >&2 || true
fi
# A third run guards against slow drift (e.g. the trailer re-entering locals).
( cd "$consumer/config" && bash "$script" )
if diff -q "$work/first.gitignore" "$consumer/.gitignore" >/dev/null; then
  pass "merge is idempotent (3rd run identical)"
else
  f-ail "merge drifts on the 3rd run"
  diff "$work/first.gitignore" "$consumer/.gitignore" >&2 || true
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "OK: all update_gitignore regression checks passed."
else
  echo "FAILED: one or more update_gitignore regression checks failed." >&2
fi
exit "$fail"
