#!/usr/bin/env bash
#
# PreToolUse hook: block direct Edit/Write/MultiEdit on `version.gradle.kts`.
# The bump-version skill owns the version-bump policy (snapshot numbering,
# rebuilds, dependency-report updates, conflict resolution) -- see
# .agents/version-policy.md and .agents/skills/bump-version/SKILL.md.
#
# Input: hook JSON on stdin.
# Exit:  0 to allow, 2 to block with stderr message surfaced to Claude.
#
set -eu

input=$(cat)
file=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')

case "$file" in
  */version.gradle.kts|version.gradle.kts)
    cat >&2 <<'EOF'
Direct edits to version.gradle.kts are blocked by a project hook.

Use the bump-version skill instead:
  /bump-version [snapshot|minor|major]

The skill owns the policy (snapshot numbering, rebuild of dependency
reports, conflict resolution). See:
  - .agents/version-policy.md
  - .agents/skills/bump-version/SKILL.md
EOF
    exit 2
    ;;
esac

exit 0
