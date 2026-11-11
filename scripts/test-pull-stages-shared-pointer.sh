#!/usr/bin/env bash

# Copyright 2026 CodeMatters, Lda.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
# except in compliance with the License. You may obtain a copy of the License at
#
# https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under
# the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
# either express or implied. See the License for the specific language governing permissions
# and limitations under the License.

# Checks that `./config/pull` stages the pointer of a config-managed submodule it
# floats, which `ignore = all` otherwise hides from `git status` and `git add`.
#
# Builds throwaway local repositories: an `agents` remote, a `config` remote holding
# this `pull` and a stub `migrate`, and a consumer with both as submodules, declaring
# `.agents/shared` the way `adopt-shared-agents` does. The user's Git configuration
# is not read, so global hooks or settings cannot affect the result.

set -uo pipefail

# Resolve the repo's `config` directory from this script's location
# (scripts/ lives directly under it), so the test works regardless of CWD.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
config_dir="$(cd "$script_dir/.." && pwd)"
pull="$config_dir/pull"
[ -f "$pull" ] || { echo "FAIL: cannot find pull at $pull" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1
# Local `file://` submodules need this since Git 2.38.
export GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=protocol.file.allow GIT_CONFIG_VALUE_0=always
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.com
export GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.com

fail=0
check() { # check <description> <expected> <actual>
    if [ "$2" = "$3" ]; then
        echo "PASS: $1"
    else
        echo "FAIL: $1 (expected '$2', got '$3')" >&2
        fail=1
    fi
}
staged() { git ls-files -s -- .agents/shared | awk '{print $2}'; }
committed() { git ls-tree HEAD -- .agents/shared | awk '{print $3}'; }
run_pull() { # run_pull <log>; prints the exit code
    ./config/pull > "$1" 2>&1
    echo $?
}

# --- The remotes. ---------------------------------------------------------------
git init -q -b master "$work/agents-src"
( cd "$work/agents-src" && echo a > f && git add f && git commit -qm A )
git clone -q --bare "$work/agents-src" "$work/agents.git"

git init -q -b master "$work/config-src"
cp "$pull" "$work/config-src/pull" && chmod +x "$work/config-src/pull"
echo 'echo "(stub migrate)"' > "$work/config-src/migrate"
( cd "$work/config-src" && git add . && git commit -qm C )
git clone -q --bare "$work/config-src" "$work/config.git"

# --- The consumer, pinning `.agents/shared` at `A`. -----------------------------
git init -q -b master "$work/consumer"
cd "$work/consumer" || exit 1
git commit -q --allow-empty -m init
git submodule add -q "file://$work/config.git" config
git submodule add -q -b master "file://$work/agents.git" .agents/shared
git config -f .gitmodules submodule..agents/shared.update merge
git config -f .gitmodules submodule..agents/shared.ignore all
git add .gitmodules && git commit -qm "Add submodules"
pin=$(committed)

# The `agents` remote moves on to `B`.
( cd "$work/agents-src" && echo b > f && git commit -qam B \
    && git push -q "$work/agents.git" master )
tip=$(git -C "$work/agents.git" rev-parse master)

# --- 1. A float to the remote tip is staged, so the pull's commit records it. ---
check "pull exits with 0" 0 "$(run_pull "$work/pull1.log")"
check "the floated pointer is staged" "$tip" "$(staged)"
check "pull reports the staging" 1 \
    "$(grep -c "Staged '.agents/shared' at ${tip:0:12}" "$work/pull1.log")"
check "plain 'git status' shows the staged pointer" "M  .agents/shared" \
    "$(git status --short)"
git commit -qm "Pull config"
check "the commit of the pull pins the tip" "$tip" "$(committed)"
[ "$pin" != "$tip" ] || { echo "FAIL: the fixture did not move the remote" >&2; fail=1; }

# --- 2. A submodule ahead of its remote is not staged. -------------------------
( cd .agents/shared && echo c > g && git add g && git commit -qm local-C )
local_commit=$(git -C .agents/shared rev-parse HEAD)
check "pull exits with 0" 0 "$(run_pull "$work/pull2.log")"
check "the index keeps the remote tip, not the local commit" "$tip" "$(staged)"
check "a NOTE explains it" 1 \
    "$(grep -c "'.agents/shared' is not at origin/master; its pointer is not staged" \
        "$work/pull2.log")"
check "the local commit stays checked out" "$local_commit" \
    "$(git -C .agents/shared rev-parse HEAD)"
git -C .agents/shared reset -q --hard refs/remotes/origin/master

# --- 3. A failed float stages nothing. -----------------------------------------
( cd "$work/agents-src" && echo d > f && git commit -qam D \
    && git push -q "$work/agents.git" master )
mv "$work/agents.git" "$work/agents.git.away"
check "pull exits with 0" 0 "$(run_pull "$work/pull3.log")"
check "the index still holds the last committed pin" "$tip" "$(staged)"
check "a WARNING reports the failed float" 1 \
    "$(grep -c "WARNING: could not float '.agents/shared'" "$work/pull3.log")"
mv "$work/agents.git.away" "$work/agents.git"

# --- 4. A pull that changes nothing leaves the index clean. ---------------------
run_pull "$work/pull4.log" > /dev/null
git commit -qm "Pull config again"
run_pull "$work/pull5.log" > /dev/null
check "nothing is staged after a pull that moves nothing" "" \
    "$(git diff --cached --name-only --ignore-submodules=none)"

exit "$fail"
