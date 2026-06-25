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

# Re-judges open pull requests when the base branch advances.
#
# Runs from `revalidate-versions.yml` on a push to a release base branch (i.e. when a
# pull request merges). For every OTHER open PR targeting that base whose
# `version.gradle.kts` version is now less than or equal to the new base version, it
# posts a failing `Version Guard` commit status on the PR's head commit, so the PR
# cannot merge until the author re-bumps. The status self-clears: the re-bump push runs
# `increment-guard.yml`, which posts a fresh `success` on the new head.
#
# This narrows, but does not close, the race against auto-merge: a PR that is already
# mergeable can still merge in the seconds before this fan-out posts its failure. Such a
# late merge produces a publish collision, which the immutable Maven registry rejects
# (a loud, recoverable red Publish) — it never overwrites an artifact.
#
# Inputs (env): REPO (owner/name), BASE_REF (the advanced branch), GH_TOKEN.
#
# The version-parsing helpers mirror `version-bumped.sh` in the shared `agents` module;
# both intentionally rely on `sort -V`, which orders the project's own successive
# snapshot numbers (`...SNAPSHOT.99` < `...SNAPSHOT.100`) correctly.

: "${REPO:?REPO is required}"
: "${BASE_REF:?BASE_REF is required}"
: "${GH_TOKEN:?GH_TOKEN is required}"

version_file="version.gradle.kts"

if [ ! -f "$version_file" ]; then
  echo "revalidate-versions: no $version_file at the base tip; nothing to do."
  exit 0
fi

# Discovers the publishing-version key from `build.gradle.kts` (`version = extra["KEY"]`),
# falling back to `versionToPublish`. A simplified form of `version-bumped.sh`'s discovery:
# on an ambiguous match it falls back rather than refusing, since a wrong key here only yields
# a missed stale flag (a false negative), never a wrong block — the registry stays the guard.
discover_key() {
  local files="" keys
  [ -f build.gradle.kts ] && files="build.gradle.kts"
  [ -f build.gradle ] && files="$files build.gradle"
  if [ -z "$files" ]; then
    echo "versionToPublish"
    return
  fi
  # shellcheck disable=SC2086
  keys=$(grep -hE '^[[:space:]]*version[[:space:]]*=[[:space:]]*extra[[:space:]]*\[[[:space:]]*["'"'"'][^"'"'"']+["'"'"']' $files 2>/dev/null \
      | sed -nE 's/.*extra[[:space:]]*\[[[:space:]]*["'"'"']([^"'"'"']+)["'"'"'].*/\1/p' \
      | sort -u)
  if [ "$(printf '%s' "$keys" | grep -c .)" = "1" ]; then
    printf '%s' "$keys"
  else
    echo "versionToPublish"
  fi
}

# Parses the version literal for the given key out of `version.gradle.kts` content.
# Handles a direct literal and one alias hop (via another `extra` or a plain `val`).
# Mirrors `version-bumped.sh`.
parse_version() {
  local content="$1" name="$2" v src
  v=$(printf '%s\n' "$content" \
      | grep -E "val[[:space:]]+${name}([[:space:]]*:[[:space:]]*String)?[[:space:]]+by[[:space:]]+extra\(\"" \
      | head -n1 | sed -nE 's/.*extra\("([^"]+)".*/\1/p')
  if [ -n "$v" ]; then printf '%s' "$v"; return 0; fi
  src=$(printf '%s\n' "$content" \
      | grep -E "val[[:space:]]+${name}([[:space:]]*:[[:space:]]*String)?[[:space:]]+by[[:space:]]+extra\(" \
      | head -n1 | sed -nE 's/.*extra\(([A-Za-z_][A-Za-z0-9_]*)\).*/\1/p')
  if [ -n "$src" ]; then
    v=$(printf '%s\n' "$content" \
        | grep -E "val[[:space:]]+${src}([[:space:]]*:[[:space:]]*String)?[[:space:]]+by[[:space:]]+extra\(\"" \
        | head -n1 | sed -nE 's/.*extra\("([^"]+)".*/\1/p')
    if [ -z "$v" ]; then
      v=$(printf '%s\n' "$content" \
          | grep -E "val[[:space:]]+${src}([[:space:]]*:[[:space:]]*String)?[[:space:]]*=[[:space:]]*\"" \
          | head -n1 | sed -nE 's/.*=[[:space:]]*"([^"]+)".*/\1/p')
    fi
    if [ -n "$v" ]; then printf '%s' "$v"; return 0; fi
  fi
  return 1
}

# `head <= base` ? Returns 0 (stale) when head is not strictly greater than base.
is_stale() {
  local head="$1" base="$2"
  [ "$head" = "$base" ] && return 0
  if [ "$(printf '%s\n%s\n' "$base" "$head" | sort -V | tail -n1)" = "$head" ]; then
    return 1
  fi
  return 0
}

key=$(discover_key)
base_version=$(parse_version "$(cat "$version_file")" "$key" || true)
if [ -z "$base_version" ]; then
  echo "revalidate-versions: could not parse '$key' from base $version_file; nothing to do."
  exit 0
fi
echo "revalidate-versions: base '$BASE_REF' publishes '$key' = $base_version."

gh pr list --repo "$REPO" --base "$BASE_REF" --state open --limit 200 \
  --json number,headRefOid,isDraft \
  --jq '.[] | select(.isDraft | not) | [.number, .headRefOid] | @tsv' \
| while IFS=$'\t' read -r number sha; do
    [ -z "$number" ] && continue
    content=$(gh api "repos/$REPO/contents/$version_file?ref=$sha" --jq '.content' 2>/dev/null \
        | base64 --decode 2>/dev/null || true)
    if [ -z "$content" ]; then
      echo "  PR #$number: no $version_file at $sha; skipping."
      continue
    fi
    head_version=$(parse_version "$content" "$key" || true)
    if [ -z "$head_version" ]; then
      echo "  PR #$number: could not parse '$key'; skipping."
      continue
    fi
    if is_stale "$head_version" "$base_version"; then
      echo "  PR #$number: version $head_version <= base $base_version -> marking stale."
      gh api -X POST "repos/$REPO/statuses/$sha" \
        -f state=failure \
        -f context="Version Guard" \
        -f description="Base advanced to $base_version; re-bump required (head $head_version)." \
        >/dev/null 2>&1 \
        || echo "  PR #$number: could not post status (continuing)."
    else
      echo "  PR #$number: version $head_version > base $base_version -> ok."
    fi
  done
