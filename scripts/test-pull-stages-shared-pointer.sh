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
# floats, which `ignore = all` otherwise hides from `git status` and `git add`, and
# that it stages only a commit the declared remote has.
#
# Builds throwaway local repositories: `agents` remotes, a `config` remote holding
# this `pull` and a `migrate` stand-in, and a consumer with both as submodules,
# declaring `.agents/shared` the way `adopt-shared-agents` does. The stand-in either
# does nothing, or runs the real `adopt-shared-agents` the way `migrate` does, with
# a local canonical remote. The user's Git configuration is not read.

set -uo pipefail

# Resolve the repo's `config` directory from this script's location
# (scripts/ lives directly under it), so the test works regardless of CWD.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
config_dir="$(cd "$script_dir/.." && pwd)"
pull="$config_dir/pull"
adopt="$config_dir/adopt-shared-agents"
[ -f "$pull" ]  || { echo "FAIL: cannot find pull at $pull" >&2; exit 1; }
[ -f "$adopt" ] || { echo "FAIL: cannot find adopt-shared-agents at $adopt" >&2; exit 1; }

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
commit_to() { # commit_to <bare-remote> <file> <content> <message>; prints the new tip
    local src="$work/scratch-$RANDOM"
    git clone -q "$1" "$src"
    ( cd "$src" && echo "$3" > "$2" && git add "$2" && git commit -qm "$4" \
        && git push -q origin HEAD:master )
    git -C "$1" rev-parse master
}

# make_consumer <dir> <migrate: stub|adopt> <canonical remote> <submodule remote>
#
# Creates `<dir>/config.git`, and the consumer at `<dir>/consumer`, whose
# `.agents/shared` comes from <submodule remote>. With `adopt`, the `migrate`
# stand-in adopts `.agents/shared` from <canonical remote>, as `migrate` does.
make_consumer() {
    local dir=$1 kind=$2 canonical=$3 remote=$4
    git init -q -b master "$dir/config-src"
    cp "$pull" "$dir/config-src/pull" && chmod +x "$dir/config-src/pull"
    cp "$adopt" "$dir/config-src/adopt-shared-agents"
    if [ "$kind" = adopt ]; then
        cat > "$dir/config-src/migrate" <<EOF
# The adoption step of the real \`migrate\`, with a local canonical remote.
if ! ( cd .. && bash ./config/adopt-shared-agents "file://$canonical" ); then
  echo "ERROR: shared-agents setup failed" >&2
  exit 1
fi
cd ..
EOF
    else
        echo 'echo "(stub migrate)"; cd ..' > "$dir/config-src/migrate"
    fi
    ( cd "$dir/config-src" && git add . && git commit -qm C )
    git clone -q --bare "$dir/config-src" "$dir/config.git"

    git init -q -b master "$dir/consumer"
    (
        cd "$dir/consumer" || exit 1
        git commit -q --allow-empty -m init
        git submodule add -q "file://$dir/config.git" config
        git submodule add -q -b master "file://$remote" .agents/shared
        git config -f .gitmodules submodule..agents/shared.update merge
        git config -f .gitmodules submodule..agents/shared.ignore all
        git add .gitmodules && git commit -qm "Add submodules"
    )
}

# The canonical `agents` remote, at `A`.
git init -q -b master "$work/agents-src"
( cd "$work/agents-src" && echo a > f && git add f && git commit -qm A )
git clone -q --bare "$work/agents-src" "$work/agents.git"

# =================================================================================
echo "== Without adoption"
make_consumer "$work/plain" stub "$work/agents.git" "$work/agents.git"
cd "$work/plain/consumer" || exit 1
pin=$(committed)
tip=$(commit_to "$work/agents.git" f b B)

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
    "$(grep -c "'.agents/shared' is not at the tip of 'master'" "$work/pull2.log")"
check "the local commit stays checked out" "$local_commit" \
    "$(git -C .agents/shared rev-parse HEAD)"
git -C .agents/shared reset -q --hard refs/remotes/origin/master

# --- 3. A failed float stages nothing. -----------------------------------------
tip_d=$(commit_to "$work/agents.git" f d D)
mv "$work/agents.git" "$work/agents.git.away"
check "pull exits with 0" 0 "$(run_pull "$work/pull3.log")"
check "the index still holds the last committed pin" "$tip" "$(staged)"
check "a WARNING reports the failed float" 1 \
    "$(grep -c "WARNING: could not float '.agents/shared'" "$work/pull3.log")"
mv "$work/agents.git.away" "$work/agents.git"

# --- 4. A pull that changes nothing leaves the index clean. ---------------------
run_pull "$work/pull4.log" > /dev/null
check "the next pull stages the new tip" "$tip_d" "$(staged)"
git commit -qm "Pull config again"
run_pull "$work/pull5.log" > /dev/null
check "nothing is staged after a pull that moves nothing" "" \
    "$(git diff --cached --name-only --ignore-submodules=none)"

# =================================================================================
echo "== With adoption"
canonical_tip=$(git -C "$work/agents.git" rev-parse master)

# --- 5. A consumer on the canonical remote gets its tip staged. ------------------
make_consumer "$work/canon" adopt "$work/agents.git" "$work/agents.git"
cd "$work/canon/consumer" || exit 1
git -C .agents/shared reset -q --hard HEAD~1   # Start behind the tip.
git add --force .agents/shared && git commit -qm "Pin behind the tip"
check "pull exits with 0" 0 "$(run_pull "$work/pull6.log")"
check "the canonical tip is staged" "$canonical_tip" "$(staged)"

# --- 6. A consumer on a fork never gets a fork-only commit staged. ---------------
git clone -q --bare "$work/agents.git" "$work/fork.git"
fork_tip=$(commit_to "$work/fork.git" fork-only x F)
make_consumer "$work/fork" adopt "$work/agents.git" "$work/fork.git"
cd "$work/fork/consumer" || exit 1
check "pull exits with 0" 0 "$(run_pull "$work/pull7.log")"
check "the canonical tip is staged, not the fork's" "$canonical_tip" "$(staged)"
check "the fork tip is not staged" "" "$([ "$(staged)" = "$fork_tip" ] && echo staged)"
check ".gitmodules now declares the canonical remote" "file://$work/agents.git" \
    "$(git config --blob=:.gitmodules --get submodule..agents/shared.url)"
git commit -qm "Pull config"
check "a fresh clone can check out the pinned commit" 0 \
    "$(git clone -q "$work/fork/consumer" "$work/fresh" \
        && git -C "$work/fresh" submodule update --init -q -- .agents/shared \
        > /dev/null 2>&1; echo $?)"

exit "$fail"
