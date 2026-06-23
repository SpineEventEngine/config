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

set -euo pipefail

src="${1:-.gitignore}"
dest="${2:-../.gitignore}"

# Merges config's shared `.gitignore` into a consumer's, PRESERVING the consumer's
# own entries instead of overwriting them. Called by `migrate`. Replaces the
# previous `cp -a .gitignore ..`, which clobbered a consumer's secret-ignore
# patterns on every pull — the root cause of a decrypted service-account key being
# re-exposed and committed.
#
# Approach: DELIMIT, don't filter. `.gitignore` is order-sensitive and
# last-match-wins — a later `!pattern` re-includes what an earlier pattern
# excluded. So filtering, de-duplicating, or reordering a consumer's lines can
# silently flip what a neighbouring line matches. This script never rewrites
# repo-local lines in the steady state: it delimits the regions IT owns and copies
# everything else through verbatim. The merged file looks like:
#
#   # >>> shared config >>>        <- managed baseline, refreshed every pull
#   <baseline>
#   # <<< shared config <<<
#   # >>> repo-local entries >>>   <- the consumer's own lines, VERBATIM
#   <repo-local, original order>
#   # <<< repo-local entries <<<
#   # >>> secret ignores ... >>>   <- positive secret patterns, re-asserted LAST so
#   <secret patterns>                 a repo-local `!*.json` cannot un-ignore a key
#   # <<< secret ignores <<<
#
# Idempotency comes from stripping the managed blocks by their MARKERS, not by
# de-duplicating content, so re-running yields byte-identical output. Repo-local
# sits AFTER the baseline so a consumer can still override a baseline default (a
# scoped `*.gpg`, a theme's output, ...); the positive-only secret re-assertion is
# what keeps that freedom from re-exposing a key (it omits the baseline's `!*.gpg`,
# so it never overrides a consumer's own `*.gpg` ignore).
#
# Two code paths below:
#   * STEADY STATE (our markers present) — preserve repo-local verbatim, no de-dup.
#   * FIRST MIGRATION (no markers — a legacy raw `cp` of the baseline that the old
#     `migrate` left in every repo) — a one-time best-effort bootstrap that drops
#     baseline-positive duplicates, keeps negations + custom lines, then writes the
#     markers so every later pull takes the exact path above.
#
# Usage: update-gitignore.sh [SRC] [DEST]
#   SRC   shared baseline `.gitignore` (default: `.gitignore` — config's own)
#   DEST  consumer `.gitignore` to write (default: `../.gitignore`)
# Run from the `config` directory (as `migrate` does) so the defaults resolve.

# Managed-block markers. The baseline markers are UNCHANGED from earlier versions
# so a consumer file written by an older `update-gitignore.sh` is still recognized
# and refreshed in place.
base_begin='# >>> shared config (managed by ./config/pull -- do not edit inside this block) >>>'
base_end='# <<< shared config <<<'
local_begin='# >>> repo-local entries (preserved across ./config/pull) >>>'
local_end='# <<< repo-local entries <<<'
secret_begin='# >>> secret ignores re-asserted last (managed by ./config/pull -- do not edit) >>>'
secret_end='# <<< secret ignores <<<'

# Legacy pre-marker scaffolding emitted by intermediate versions of this script
# (they live only on this branch and were never released). Matched EXACTLY below —
# never by prefix — so a consumer's own comment that merely begins the same way
# cannot trigger the to-end-of-file drop and silently lose their entries.
legacy_local_label='# --- repo-local entries (preserved across ./config/pull) ---'
legacy_secret_label='# --- secret ignores re-asserted last so a repo-local negation cannot un-ignore a credential ---'

# Positive secret patterns from the baseline's Secrets section: the span from the
# `# Secrets` header to the closing `!*.gpg`, excluding comments and negations.
# (The `# Secrets` header and the trailing `!*.gpg` are asserted as invariants by
# scripts/test-update-gitignore.sh, guarding against silent baseline drift.)
secret_patterns() {
  awk '
    /^# Secrets/ { f = 1 }
    /^!\*\.gpg$/ { f = 0 }
    f && $0 !~ /^[[:space:]]*#/ && $0 !~ /^!/ && $0 ~ /[^[:space:]]/ { print }
  ' "$src"
}

# Strip leading and trailing blank lines (internal blanks kept) so repeated runs
# can't accumulate separators.
trim_blank_edges() {
  awk '
    { line[NR] = $0 }
    END {
      s = 1;  while (s <= NR && line[s] ~ /^[[:space:]]*$/) s++
      e = NR; while (e >= s  && line[e] ~ /^[[:space:]]*$/) e--
      for (i = s; i <= e; i++) print line[i]
    }
  '
}

# Build into a sibling temp file and `mv` into place: a same-directory rename is
# atomic, so an interrupted or failed run can never leave the consumer with a
# truncated or empty `.gitignore`. `set -e` aborts before the `mv` if any step
# fails, so the existing file is left untouched (fail closed).
out=$(mktemp "${dest}.XXXXXX") || { echo "update-gitignore: cannot create temp file." >&2; exit 1; }
locals=$(mktemp) || { rm -f "$out"; echo "update-gitignore: cannot create temp file." >&2; exit 1; }
trap 'rm -f "$out" "$locals"' EXIT

# Fresh consumer: just drop the baseline in a managed block.
if [ ! -f "$dest" ]; then
  { printf '%s\n' "$base_begin"; cat "$src"; printf '%s\n' "$base_end"; } > "$out"
  mv "$out" "$dest"
  exit 0
fi

if grep -qxF "$base_begin" "$dest"; then
  # STEADY STATE — the file already carries our markers. Repo-local entries are
  # everything OUTSIDE the managed blocks, preserved VERBATIM and IN ORDER (no
  # de-duplication, no filtering). Stripped here: the baseline block, the secrets
  # block, the repo-local markers themselves (their CONTENT is what we keep), and
  # any legacy pre-marker scaffolding written by intermediate script versions (the
  # `legacy_local_label` and a `legacy_secret_label` trailer that always ran to
  # end-of-file), matched EXACTLY — so an already-migrated consumer converts to the
  # marker format exactly once, and a look-alike consumer comment is never dropped.
  awk -v bb="$base_begin" -v eb="$base_end" \
      -v sb="$secret_begin" -v se="$secret_end" \
      -v lb="$local_begin"  -v le="$local_end" \
      -v lll="$legacy_local_label" -v lsl="$legacy_secret_label" '
    $0 == bb  { inb = 1; next }
    inb       { if ($0 == eb) inb = 0; next }
    $0 == sb  { ins = 1; next }
    ins       { if ($0 == se) ins = 0; next }
    $0 == lsl { legacy = 1 }
    legacy    { next }
    $0 == lb || $0 == le { next }
    $0 == lll { next }
    { print }
  ' "$dest" | trim_blank_edges > "$locals"
else
  # FIRST MIGRATION — no markers yet (a legacy raw `cp` of the baseline, possibly
  # with the consumer's own lines appended). One-time best-effort bootstrap: drop
  # lines duplicating a baseline POSITIVE pattern (already in the managed block),
  # keep every negation (a consumer may re-state an exception such as
  # `!gradle-wrapper.jar` after their own broad ignore) and every custom line.
  # Keeping negations means a raw baseline copy carries the baseline's OWN
  # negations (e.g. `!*.gpg`) into the repo-local block on this one pull — cosmetic
  # and harmless (they re-include already-included paths), and dropping them would
  # reintroduce the order-sensitive de-dup this rewrite removed, so we don't.
  # After this run writes the markers, the exact steady-state path above takes
  # over. `|| true`: a pure raw copy leaves no custom lines, and `grep -v` exits 1
  # when it selects nothing — not an error here.
  { grep -vxF -f <(grep -v '^!' "$src") "$dest" || true; } \
    | awk '!seen[$0]++' | trim_blank_edges > "$locals"
fi

# Fail closed: re-asserting secrets only matters when there ARE repo-local lines
# (with none, nothing can un-ignore a credential). When it does matter, the
# baseline's Secrets section MUST yield patterns — an empty result would mean a
# malformed/truncated baseline reached a consumer and would silently leave a
# repo-local `!*.json` free to re-expose a key. Refuse rather than weaken.
secrets=""
if [ -s "$locals" ]; then
  secrets=$(secret_patterns)
  [ -n "$secrets" ] || {
    echo "update-gitignore: baseline Secrets section yielded no patterns — refusing to weaken secret protection." >&2
    exit 1
  }
fi

{
  printf '%s\n' "$base_begin"; cat "$src"; printf '%s\n' "$base_end"
  if [ -s "$locals" ]; then
    printf '\n%s\n' "$local_begin"
    cat "$locals"
    printf '%s\n' "$local_end"
    printf '\n%s\n' "$secret_begin"
    printf '%s\n' "$secrets"
    printf '%s\n' "$secret_end"
  fi
} > "$out"

mv "$out" "$dest"
exit 0
