#!/usr/bin/env bash
#
# Layer-1 version-bump check (deterministic, no agent reasoning).
#
# Verifies that a feature branch which contains publishable changes also
# bumps `version.gradle.kts` strictly above the value on the base ref.
# Prevents accidental Maven Local overwrites that would break integration
# tests in consuming repos.
#
# Exit codes:
#   0 — OK to publish:
#         * repo has no root `version.gradle.kts` (not a versioned project), OR
#         * branch has no publishable changes vs the base ref, OR
#         * working-tree version is strictly greater than base version.
#   1 — Block: branch has publishable changes but the version is unchanged
#         or decreased. A pointer to `/bump-version` is printed to stderr.
#   2 — Configuration error (bad base ref, parse failure). Stderr explains.
#
# Inputs (env, all optional):
#   VERSION_BUMPED_BASE   Base ref to compare against. Default: master, then
#                         main if master is absent. Caller may pass a remote
#                         ref like `origin/master` to force a fetch-aware
#                         comparison.
#   VERSION_BUMPED_QUIET  When `1`, suppress the "OK" line on stdout. The
#                         publish-version-gate hook sets this.
#
# Notes:
#   * Scope: this check guards against overwriting Maven Local artifacts
#     during day-to-day work. "Publishable" here means "can change a
#     published artifact's bytes" — source, buildSrc, gradle wrapper.
#     Pure docs, agent configs, and hook scripts are excluded because
#     rebuilding them produces identical bytes for the same version.
#     `docs/dependencies/**` is treated as publishable because those
#     reports are regenerated as part of a version-bump cycle.
#   * Stricter rule at PR time: CI's `checkVersionIncrement` fails any
#     PR in a versioned repo whose version did not advance, regardless
#     of what changed. `/pre-pr` step 2 enforces that stricter rule
#     locally before PR creation.
#   * The working tree is included in the changed-files set so the gate
#     reflects what `./gradlew build` would actually publish.
#
set -eu

repo_root=$(git rev-parse --show-toplevel 2>/dev/null) || {
  echo "version-bumped: not inside a git repository" >&2
  exit 2
}
cd "$repo_root"

version_file="version.gradle.kts"

# --- N/A: not a versioned project ----------------------------------------
if [ ! -f "$version_file" ]; then
  [ "${VERSION_BUMPED_QUIET:-0}" = "1" ] || echo "version-bumped: N/A (no root version.gradle.kts)"
  exit 0
fi

# --- Resolve base ref ----------------------------------------------------
base="${VERSION_BUMPED_BASE:-}"
if [ -z "$base" ]; then
  if git show-ref --verify --quiet refs/heads/master; then
    base=master
  elif git show-ref --verify --quiet refs/heads/main; then
    base=main
  else
    echo "version-bumped: no master or main branch found; set VERSION_BUMPED_BASE" >&2
    exit 2
  fi
fi

if ! git rev-parse --verify --quiet "$base" >/dev/null; then
  echo "version-bumped: base ref '$base' does not resolve" >&2
  exit 2
fi

# When we are on the base branch itself, there is nothing to gate.
current_branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
if [ "$current_branch" = "$base" ] || [ "$current_branch" = "${base##*/}" ]; then
  [ "${VERSION_BUMPED_QUIET:-0}" = "1" ] || echo "version-bumped: on base branch ($current_branch); nothing to gate"
  exit 0
fi

merge_base=$(git merge-base HEAD "$base" 2>/dev/null) || {
  echo "version-bumped: cannot find merge-base of HEAD and '$base'" >&2
  exit 2
}

# --- Collect changed files (committed + working tree + untracked) --------
committed=$(git diff --name-only "$merge_base"..HEAD 2>/dev/null || true)
worktree=$(git diff --name-only HEAD 2>/dev/null || true)
untracked=$(git ls-files --others --exclude-standard 2>/dev/null || true)
changed=$(printf '%s\n%s\n%s\n' "$committed" "$worktree" "$untracked" | sed '/^$/d' | sort -u)

# --- Publishable classifier ----------------------------------------------
is_publishable() {
  case "$1" in
    # Dependency reports ride with publish cycles — publishable.
    docs/dependencies/*) return 0 ;;
    # Non-publishable: documentation, agent config, hooks, CI, top-level dotfiles.
    *.md|docs/*|.agents/*|.claude/*|.github/*|LICENSE|.gitignore|.gitattributes|.editorconfig)
      return 1 ;;
    # Source-shaped and build-shaped paths.
    *.kt|*.kts|*.java|*.proto) return 0 ;;
    buildSrc/*) return 0 ;;
    gradle/wrapper/*|gradlew|gradlew.bat) return 0 ;;
    # Default to publishable — safer than letting an unknown path through.
    *) return 0 ;;
  esac
}

publishable_count=0
if [ -n "$changed" ]; then
  while IFS= read -r f; do
    if is_publishable "$f"; then
      publishable_count=$((publishable_count + 1))
    fi
  done <<EOF
$changed
EOF
fi

if [ "$publishable_count" -eq 0 ]; then
  [ "${VERSION_BUMPED_QUIET:-0}" = "1" ] || echo "version-bumped: no publishable changes vs $base"
  exit 0
fi

# --- Parse versionToPublish from a Gradle file content -------------------
# Handles two shapes (per .agents/skills/bump-version/SKILL.md step 2):
#   1. val versionToPublish: String by extra("X")
#   2. val sourceVar: String by extra("X")
#      val versionToPublish by extra(sourceVar)
parse_version() {
  local content="$1"
  local v
  v=$(printf '%s' "$content" \
      | grep -E 'val[[:space:]]+versionToPublish[^=]*by[[:space:]]+extra\("' \
      | head -n1 \
      | sed -nE 's/.*extra\("([^"]+)".*/\1/p')
  if [ -n "$v" ]; then
    printf '%s' "$v"
    return 0
  fi
  local varName
  varName=$(printf '%s' "$content" \
      | grep -E 'val[[:space:]]+versionToPublish[^=]*by[[:space:]]+extra\(' \
      | head -n1 \
      | sed -nE 's/.*extra\(([A-Za-z_][A-Za-z0-9_]*)\).*/\1/p')
  if [ -n "$varName" ]; then
    v=$(printf '%s' "$content" \
        | grep -E "val[[:space:]]+${varName}[^=]*by[[:space:]]+extra\(\"" \
        | head -n1 \
        | sed -nE 's/.*extra\("([^"]+)".*/\1/p')
    if [ -n "$v" ]; then
      printf '%s' "$v"
      return 0
    fi
  fi
  return 1
}

head_content=$(cat "$version_file" 2>/dev/null || true)
head_version=$(parse_version "$head_content" || true)
if [ -z "$head_version" ]; then
  echo "version-bumped: cannot parse versionToPublish from working-tree $version_file" >&2
  exit 2
fi

# Base content may legitimately not exist (file newly introduced).
base_content=$(git show "$base:$version_file" 2>/dev/null || true)
if [ -z "$base_content" ]; then
  [ "${VERSION_BUMPED_QUIET:-0}" = "1" ] || echo "version-bumped: $version_file newly introduced at $head_version; treating as bumped"
  exit 0
fi

base_version=$(parse_version "$base_content" || true)
if [ -z "$base_version" ]; then
  echo "version-bumped: cannot parse versionToPublish from $base:$version_file" >&2
  exit 2
fi

# --- Strict-greater comparison via `sort -V` -----------------------------
if [ "$head_version" = "$base_version" ]; then
  cmp="equal"
elif [ "$(printf '%s\n%s\n' "$base_version" "$head_version" | sort -V | tail -n1)" = "$head_version" ]; then
  cmp="greater"
else
  cmp="lesser"
fi

if [ "$cmp" = "greater" ]; then
  [ "${VERSION_BUMPED_QUIET:-0}" = "1" ] || echo "version-bumped: OK ($base_version -> $head_version, $publishable_count publishable file(s))"
  exit 0
fi

cat >&2 <<EOF
version-bumped: BLOCK — $publishable_count publishable file(s) changed vs $base
  but $version_file is $cmp ($base_version vs $head_version).

  Publishing now would overwrite the Maven Local artifact at
  $base_version, which integration tests in consumer repos may rely on.

  Run /bump-version (or invoke /version-bumped to auto-recover).
  See .agents/version-policy.md and .agents/skills/bump-version/SKILL.md.
EOF
exit 1
