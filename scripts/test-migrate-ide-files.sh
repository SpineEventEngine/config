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

# Regression checks for `migrate`'s handling of the IDE-managed `.idea` files
# (`misc.xml`, `kotlinc.xml`). IDEA rewrites both on its own, so `migrate` must
# leave a consumer with copies that are UNTRACKED, IGNORED, and carrying the
# consumer's own settings rather than config's.
#
# `migrate` runs WITHOUT `set -e` and is sourced by `pull`, so a broken step here
# fails silently mid-pull: the file just stays tracked, which is indistinguishable
# from "this consumer has not pulled yet". Hence these checks assert the observable
# end state (`git ls-files`, `git check-ignore`, file contents) rather than trusting
# the script's output.
#
# The REAL `migrate` is run against a minimal fake `config/` fixture. Two
# consequences worth knowing before reading a failure:
#
#   * `adopt-shared-agents` is STUBBED. The real one reaches out to
#     `github.com/SpineEventEngine/agents`, and `migrate` aborts (exit 1) when it
#     fails — that would make this suite network-dependent.
#   * The fixture omits most files `migrate` copies (`AGENTS.md`, `buildSrc`, the
#     workflows, ...), so the run prints `cp: No such file or directory` to stderr.
#     That noise is EXPECTED — `migrate` is fail-open by design and continues. The
#     run log is captured, and shown only when a check fails.
#
# SCOPE. Two mechanisms conspire to keep these files out of git, and this suite
# pins only one of them end-to-end:
#
#   * `migrate` — untracks the files and scrubs the retired `!` negation from the
#     merged `.gitignore`. Covered here.
#   * `scripts/update-gitignore.sh` — drops the same negation during the merge,
#     via its `retired_negations` list. Covered by `scripts/test-update-gitignore.sh`.
#
# The two overlap on purpose: `migrate` scrubs the negation AFTER the merge runs,
# so dropping an entry from `retired_negations` does not break the pull — and this
# suite keeps passing. That is redundancy working, not a blind spot; the entry
# matters when `update-gitignore.sh` is run on its own, which is exactly what the
# sibling suite asserts. Run both.

set -eo pipefail

# Resolve the repo's `config` directory from this script's location
# (scripts/ lives directly under it), so the test works regardless of CWD.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
config_dir="$(cd "$script_dir/.." && pwd)"

fail=0
pass() { echo "PASS: $1"; }
f-ail() { echo "FAIL: $1" >&2; fail=1; }

# --- Pre-flight: everything the fixture copies in must exist. -----------------
for required in migrate scripts/update-gitignore.sh .gitignore; do
  [ -f "$config_dir/$required" ] \
    || { echo "FAIL: cannot find '$required' under $config_dir" >&2; exit 1; }
done

# The files under test, and the marker each one carries in the fixture.
ide_files=(.idea/misc.xml .idea/kotlinc.xml)

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
consumer="$work/consumer"
fake_config="$consumer/config"
mkdir -p "$fake_config/scripts" "$fake_config/.idea" "$consumer/.idea"

# --- The fake `config` the consumer pulls from. -------------------------------
cp "$config_dir/migrate"                     "$fake_config/migrate"
cp "$config_dir/scripts/update-gitignore.sh" "$fake_config/scripts/"
cp "$config_dir/.gitignore"                  "$fake_config/.gitignore"

# Stub out the network gate (see the header note).
printf '#!/usr/bin/env bash\nexit 0\n' > "$fake_config/adopt-shared-agents"
chmod +x "$fake_config/adopt-shared-agents"

# Config's own `.idea`. It carries copies of BOTH files under test: config no
# longer tracks them, so a puller's checkout may still hold stray local ones —
# exactly the case the preserve/restore around `cp -R .idea ..` must survive.
# `other.xml` stands for the genuinely shared settings the overlay must deliver.
for f in "${ide_files[@]}"; do
  printf 'CONFIG-%s\n' "$(basename "$f")" > "$fake_config/$f"
done
printf 'CONFIG-shared\n' > "$fake_config/.idea/other.xml"

# --- A legacy consumer: both files COMMITTED, retired negations in place. -----
git -C "$consumer" init -q
git -C "$consumer" config user.email test@example.com
git -C "$consumer" config user.name  test

for f in "${ide_files[@]}"; do
  printf 'CONSUMER-%s\n' "$(basename "$f")" > "$consumer/$f"
done

# A raw copy of an OLDER baseline: the current baseline plus the `!` re-inclusions
# config shipped until 2025 (`2fbc0303`), plus a genuine consumer line. Without
# the retired-negation filter these `!`s land in the repo-local block, and
# `.gitignore` being last-match-wins they would un-ignore both files.
{ cat "$config_dir/.gitignore"
  printf '%s\n' '!.idea/misc.xml' '!.idea/kotlinc.xml' 'my-own-cache/'
} > "$consumer/.gitignore"

git -C "$consumer" add -A
git -C "$consumer" commit -qm "Legacy consumer with tracked IDE files"

# Sanity-check the premise: the fixture must really start with both files tracked,
# otherwise every assertion below would pass vacuously.
for f in "${ide_files[@]}"; do
  git -C "$consumer" ls-files --error-unmatch "$f" >/dev/null 2>&1 \
    || { echo "FAIL: fixture is broken — '$f' was not tracked before migrate ran" >&2; exit 1; }
done

# --- Run the real `migrate`, exactly as `pull` does (CWD = `config`). ---------
run_migrate() {
  local log="$1"
  ( cd "$fake_config" && bash migrate ) > "$log" 2>&1
}

log1="$work/migrate-1.log"
if run_migrate "$log1"; then
  pass "migrate ran to completion (exit 0)"
else
  f-ail "migrate exited non-zero — see the log below"
  cat "$log1" >&2
fi

cd "$consumer"

# --- (1) Untracked, but still on disk, and ignored. ---------------------------
for f in "${ide_files[@]}"; do
  if git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
    f-ail "'$f' is still tracked after migrate"
  else
    pass "'$f' untracked"
  fi

  if [ -f "$f" ]; then
    pass "'$f' still present on disk"
  else
    f-ail "'$f' was deleted from the working tree (must survive \`git rm --cached\`)"
  fi

  if git check-ignore -q "$f"; then
    pass "'$f' ignored"
  else
    f-ail "'$f' is NOT ignored — it will come back as '??' and \`git add -A\` will re-add it"
  fi
done

# --- (2) The consumer's own settings survive config's `.idea` overlay. --------
# A git-silent clobber here would reset every consumer's JDK / Kotlin JVM target
# to whatever the puller happened to have locally.
for f in "${ide_files[@]}"; do
  expected="CONSUMER-$(basename "$f")"
  if [ "$(cat "$f")" = "$expected" ]; then
    pass "'$f' kept the consumer's own content"
  else
    f-ail "'$f' was clobbered by config's copy (expected '$expected', got '$(cat "$f")')"
  fi
done

# The overlay must still deliver genuinely shared `.idea` files — a preserve step
# that accidentally skipped the copy would also pass every check above.
if [ -f .idea/other.xml ] && [ "$(cat .idea/other.xml)" = "CONFIG-shared" ]; then
  pass "shared '.idea/other.xml' delivered by the overlay"
else
  f-ail "shared '.idea/other.xml' missing — the '.idea' overlay did not run"
fi

# --- (3) The retired negations are gone; genuine consumer lines are not. ------
for f in "${ide_files[@]}"; do
  if grep -qxF "!$f" .gitignore; then
    f-ail "retired negation '!$f' survived in the merged .gitignore"
  else
    pass "retired negation '!$f' stripped from the merged .gitignore"
  fi
done

if git check-ignore -q my-own-cache/x; then
  pass "genuine consumer entry 'my-own-cache/' preserved"
else
  f-ail "genuine consumer entry 'my-own-cache/' was lost by the merge"
fi

# --- (4) `git add -A` must not resurrect them. --------------------------------
# This is the failure the whole mechanism exists to prevent: an un-ignored file
# left in the working tree is silently re-committed by the consumer's next
# `git add -A`, and the churn returns.
git add -A
for f in "${ide_files[@]}"; do
  if git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
    f-ail "'git add -A' re-added '$f' — the ignore did not hold"
  else
    pass "'git add -A' left '$f' untracked"
  fi
done

# --- (5) No temp files leaked into the consumer. ------------------------------
# The negation scrub builds `./.gitignore.XXXXXX` next to the target so the
# replacement is an atomic same-directory rename; every one must be consumed.
leaked="$(find . -maxdepth 1 -name '.gitignore.??????' -print)"
if [ -z "$leaked" ]; then
  pass "no '.gitignore.XXXXXX' temp files left behind"
else
  f-ail "temp files leaked into the consumer: $leaked"
fi

# --- (6) A second pull is a quiet no-op. --------------------------------------
# The `git ls-files` / `grep -qxF` guards must make a re-run do nothing: `git rm`
# on an already-untracked path fails, and `migrate` runs without `set -e`.
git commit -qm "Pull: untrack IDE-managed files"

log2="$work/migrate-2.log"
if run_migrate "$log2"; then
  pass "second migrate run completed (exit 0)"
else
  f-ail "second migrate run exited non-zero — see the log below"
  cat "$log2" >&2
fi

for f in "${ide_files[@]}"; do
  if grep -q "Untracking '$f'" "$log2"; then
    f-ail "second run tried to untrack '$f' again (guard is not idempotent)"
  else
    pass "second run skipped '$f' (quiet no-op)"
  fi
done

if [ -z "$(git status --porcelain)" ]; then
  pass "second run left the working tree clean"
else
  f-ail "second run dirtied the working tree:"
  git status --porcelain >&2
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "OK: all migrate IDE-file regression checks passed."
else
  echo "FAILED: one or more migrate IDE-file regression checks failed." >&2
  exit 1
fi
