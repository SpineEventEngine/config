#!/usr/bin/env bash
#
# PreToolUse hook: block direct Edit/Write/MultiEdit on `version.gradle.kts`.
# In repositories that have this file, the bump-version skill owns the
# version-bump policy (snapshot numbering, rebuilds, dependency-report updates,
# conflict resolution). Repositories without it must not add it just to satisfy
# hooks or reviewers.
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

If this repository already has a root version.gradle.kts, use the bump-version
skill instead:
  /bump-version [snapshot|minor|major]

If this repository does not have a root version.gradle.kts, do not add one just
to satisfy /pre-pr; the version check is not applicable.

See:
  - .agents/version-policy.md
  - .agents/skills/bump-version/SKILL.md
EOF
    exit 2
    ;;
esac

exit 0
