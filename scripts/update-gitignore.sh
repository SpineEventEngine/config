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
# Merges config's shared `.gitignore` into a consumer's, PRESERVING the
# consumer's own entries instead of overwriting them. Called by `migrate`.
#
# The shared baseline is wrapped in a managed block; repo-local patterns (a
# consumer's own decrypted-key paths, a Hugo theme's build output, ...) are kept
# in a trailing section and carried across every pull. The secret ignores are then
# re-asserted as the final word, so a preserved repo-local negation (e.g. `!*.json`
# or `!spine-dev.json`) cannot un-ignore a decrypted credential (gitignore is
# last-match-wins).
#
# This replaces the previous unconditional `cp -a .gitignore ..` in `migrate`,
# which silently dropped a consumer's secret-ignore patterns on every pull — the
# root cause of a decrypted service-account key being re-exposed and committed.
# The merge is idempotent: re-running only refreshes the managed block.
#
# Usage: update-gitignore.sh [SRC] [DEST]
#   SRC   shared baseline `.gitignore` (default: `.gitignore` — config's own)
#   DEST  consumer `.gitignore` to write (default: `../.gitignore`)
# Run from the `config` directory (as `migrate` does) so the defaults resolve.
# ---------------------------------------------------------------------------

set -u

src="${1:-.gitignore}"
dest="${2:-../.gitignore}"
begin='# >>> shared config (managed by ./config/pull -- do not edit inside this block) >>>'
end='# <<< shared config <<<'

# Build into a sibling temp file and `mv` into place: a same-directory rename is
# atomic, so an interrupted or failed run can never leave the consumer with a
# truncated or empty `.gitignore`.
out=$(mktemp "${dest}.XXXXXX") || { echo "update-gitignore: cannot create temp file." >&2; exit 1; }

if [ ! -f "$dest" ]; then
  { printf '%s\n' "$begin"; cat "$src"; printf '%s\n' "$end"; } > "$out"
  mv "$out" "$dest"
  exit 0
fi

locals=$(mktemp)
# Repo-local extras = pattern lines OUTSIDE any managed block (non-blank,
# non-comment), de-duplicated. A line duplicating a POSITIVE baseline pattern is
# dropped (it is already in the managed block), but a repo-local NEGATION (`!...`)
# is kept even when the baseline lists the same exception: a consumer may re-state
# it after their own broad ignore (e.g. `*.jar` then `!gradle-wrapper.jar`), and
# dropping it would silently re-ignore the exception. De-dup only against the
# baseline's non-negation lines (process substitution feeds those to `grep -f`).
awk -v b="$begin" -v e="$end" '
  $0==b {inb=1; next} $0==e {inb=0; next}
  !inb && $0 !~ /^[[:space:]]*#/ && $0 ~ /[^[:space:]]/ {print}
' "$dest" | grep -vxF -f <(grep -v '^!' "$src") | awk '!seen[$0]++' > "$locals"
{
  printf '%s\n' "$begin"; cat "$src"; printf '%s\n' "$end"
  if [ -s "$locals" ]; then
    printf '\n%s\n' '# --- repo-local entries (preserved across ./config/pull) ---'
    cat "$locals"
  fi
  # Re-assert the secret ignore PATTERNS as the final word: gitignore is
  # last-match-wins, so a preserved repo-local negation (e.g. `!*.json`) above must
  # not un-ignore a decrypted credential. Only the POSITIVE patterns are re-listed —
  # comments and the baseline's own `!*.gpg` negation are skipped, so this trailer
  # cannot override a consumer's repo-local `*.gpg` ignore (the managed block keeps
  # encrypted `*.gpg` committable by default). The Secrets section spans the
  # `# Secrets` header (ASCII-anchored, not the locale-fragile em-dash) up to the
  # `!*.gpg` line that closes it.
  printf '\n%s\n' '# --- secret ignores re-asserted last so a repo-local negation cannot un-ignore a credential ---'
  awk '/^# Secrets/{f=1} /^!\*\.gpg$/{f=0} f && $0 !~ /^[[:space:]]*#/ && $0 !~ /^!/ && $0 ~ /[^[:space:]]/' "$src"
} > "$out"
mv "$out" "$dest"
rm -f "$locals"
