#!/usr/bin/env bash

# Copyright 2026, TeamDev. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# https://www.apache.org/licenses/LICENSE-2.0
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
  printf '%s\n' '!debug.log'
  printf '%s\n' '*.log'
  # A consumer comment that merely BEGINS like the legacy secret trailer, plus a
  # real entry after it. The legacy match is exact, so this comment is preserved
  # and the entry survives (regression guard for the start-anchored to-EOF drop).
  printf '%s\n' '# --- secret ignores re-asserted last (consumer note, not the tool label) ---'
  printf '%s\n' 'keep-me-ignored/'
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
: > debug.log
: > info.log
: > creds.secret.properties.gpg
mkdir -p keep-me-ignored && : > keep-me-ignored/x

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

# (3c) A repo-local broad ignore plus an exception must survive IN ORDER:
# `*.jar` + `!gradle-wrapper.jar`. Repo-local lines are preserved verbatim, so the
# exception keeps the wrapper jar committable while other jars are ignored.
if git check-ignore -q other.jar && ! git check-ignore -q gradle-wrapper.jar; then
  pass "repo-local '*.jar' + '!gradle-wrapper.jar' exception preserved"
else
  f-ail "repo-local '!gradle-wrapper.jar' exception dropped (wrapper jar wrongly ignored)"
fi

# (3d) Repo-local lines are preserved VERBATIM, not de-duplicated: the ordered
# pair `!debug.log` then `*.log` must keep debug.log ignored. The earlier de-dup
# dropped the `*.log` duplicate (it is a baseline pattern), stranding `!debug.log`
# after the managed block and re-exposing debug.log. Verbatim preservation fixes
# it — `*.log` follows `!debug.log` here exactly as in the consumer's own file.
if git check-ignore -q debug.log && git check-ignore -q info.log; then
  pass "ordered repo-local '!debug.log' + '*.log' preserved (debug.log stays ignored)"
else
  f-ail "ordered repo-local pair reordered/de-duped (debug.log wrongly un-ignored)"
fi

# (3e) Migration cleanliness: the EXACT legacy scaffolding labels emitted by
# intermediate script versions must NOT survive. Matched exactly (not by prefix),
# so a consumer's own look-alike comment is preserved instead — see (3g).
legacy_local='# --- repo-local entries (preserved across ./config/pull) ---'
legacy_secret='# --- secret ignores re-asserted last so a repo-local negation cannot un-ignore a credential ---'
if grep -qxF "$legacy_local" "$consumer/.gitignore" ||
  grep -qxF "$legacy_secret" "$consumer/.gitignore"; then
  f-ail "an exact legacy scaffolding label survived migration"
else
  pass "exact legacy scaffolding labels removed on migration"
fi

# (3f) An encrypted twin whose plaintext name matches a secret glob must stay
# committable: `creds.secret.properties.gpg` (the `*.gpg` `!`-negation wins; the
# positive `*.secret.properties` re-assertion does not match the `.gpg` suffix).
if git check-ignore -q creds.secret.properties.gpg; then
  f-ail "secret-named encrypted twin 'creds.secret.properties.gpg' wrongly ignored"
else
  pass "secret-named encrypted twin 'creds.secret.properties.gpg' stays committable"
fi

# (3g) A consumer comment that merely BEGINS like the legacy secret trailer (but is
# not the exact label) is preserved, and a real entry after it survives. Under the
# old start-anchored match this comment dropped everything to EOF, silently losing
# the entry; exact matching fixes it.
if git check-ignore -q keep-me-ignored/x; then
  pass "look-alike legacy comment preserved; following entry 'keep-me-ignored/' survived"
else
  f-ail "look-alike legacy comment triggered to-EOF drop; 'keep-me-ignored/' lost"
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

# --- (6) First-migration bootstrap: a legacy UNMARKED consumer file. ----------
# Before this change, `migrate` did `cp -a .gitignore ..`, so every consumer's
# file is a raw copy of the baseline with NO managed markers, often with the
# repo's own lines appended. The first pull must bootstrap that into the marked
# format: preserve the custom lines, do NOT duplicate the baseline, keep secrets
# safe, and become idempotent thereafter.
consumer2="$work/consumer2"
mkdir -p "$consumer2/config"
cp "$baseline" "$consumer2/config/.gitignore"
git -C "$consumer2" init -q
git -C "$consumer2" config user.email test@example.com
git -C "$consumer2" config user.name  test

# Raw baseline copy + the repo's own appended lines (no managed markers).
{ cat "$baseline"; printf '%s\n' 'my-tool-cache/' '**/local-notes.txt'; } > "$consumer2/.gitignore"

( cd "$consumer2/config" && bash "$script" )

# Appended custom lines are preserved.
if git -C "$consumer2" check-ignore -q my-tool-cache/x &&
  git -C "$consumer2" check-ignore -q sub/local-notes.txt; then
  pass "bootstrap preserved the consumer's appended custom lines"
else
  f-ail "bootstrap dropped the consumer's appended custom lines"
fi

# The baseline is not duplicated: a distinctive NON-secret positive baseline line
# (`*.iml`, line ~48 — outside the `# Secrets` span, so the secret block does not
# re-list it) must appear exactly once, in the managed block. The bootstrap drops
# the consumer's raw-copy duplicate. (Secret patterns such as `credentials.tar`
# legitimately appear twice: once in the baseline, once re-asserted last.)
n_iml="$(grep -cxF '*.iml' "$consumer2/.gitignore" || true)"
if [ "$n_iml" -eq 1 ]; then
  pass "bootstrap did not duplicate the baseline ('*.iml' appears once)"
else
  f-ail "bootstrap duplicated baseline lines ('*.iml' x$n_iml)"
fi

# Secrets stay ignored; the encrypted form stays committable.
if git -C "$consumer2" check-ignore -q spine-dev.json &&
  ! git -C "$consumer2" check-ignore -q keep.gpg; then
  pass "bootstrap keeps secrets ignored and *.gpg committable"
else
  f-ail "bootstrap broke secret / encrypted-form handling"
fi

# The result is now marked and idempotent on a second run.
cp "$consumer2/.gitignore" "$work/c2-first.gitignore"
( cd "$consumer2/config" && bash "$script" )
if grep -qxF "$BEGIN" "$consumer2/.gitignore" &&
  diff -q "$work/c2-first.gitignore" "$consumer2/.gitignore" >/dev/null; then
  pass "bootstrap result is marked and idempotent"
else
  f-ail "bootstrap result is not marked or not idempotent"
  diff "$work/c2-first.gitignore" "$consumer2/.gitignore" >&2 || true
fi

# --- (7) Fresh consumer: no `.gitignore` exists yet (dest absent). ------------
# The script writes just the managed baseline block — no repo-local or secret
# block — and is idempotent. (Exercises the `[ ! -f "$dest" ]` branch.)
consumer3="$work/consumer3"
mkdir -p "$consumer3/config"
cp "$baseline" "$consumer3/config/.gitignore"
git -C "$consumer3" init -q
git -C "$consumer3" config user.email test@example.com
git -C "$consumer3" config user.name  test
# Note: NO "$consumer3/.gitignore" is created before the run.

( cd "$consumer3/config" && bash "$script" )

if [ -f "$consumer3/.gitignore" ] && grep -qxF "$BEGIN" "$consumer3/.gitignore"; then
  pass "fresh consumer: managed baseline block written"
else
  f-ail "fresh consumer: managed baseline block missing"
fi
# A fresh file has no repo-local entries, so no repo-local/secret blocks are added.
if grep -qF 'repo-local entries (preserved' "$consumer3/.gitignore"; then
  f-ail "fresh consumer: unexpected repo-local block with no consumer entries"
else
  pass "fresh consumer: no spurious repo-local/secret blocks"
fi
# Secrets are ignored and the encrypted form is committable from the baseline alone.
if git -C "$consumer3" check-ignore -q spine-dev.json &&
  ! git -C "$consumer3" check-ignore -q keep.gpg; then
  pass "fresh consumer: baseline alone keeps secrets ignored, *.gpg committable"
else
  f-ail "fresh consumer: baseline secret/encrypted-form handling broken"
fi
# Idempotent on a second run.
cp "$consumer3/.gitignore" "$work/c3-first.gitignore"
( cd "$consumer3/config" && bash "$script" )
if diff -q "$work/c3-first.gitignore" "$consumer3/.gitignore" >/dev/null; then
  pass "fresh consumer: idempotent on second run"
else
  f-ail "fresh consumer: not idempotent"
  diff "$work/c3-first.gitignore" "$consumer3/.gitignore" >&2 || true
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "OK: all update_gitignore regression checks passed."
else
  echo "FAILED: one or more update_gitignore regression checks failed." >&2
fi
exit "$fail"
