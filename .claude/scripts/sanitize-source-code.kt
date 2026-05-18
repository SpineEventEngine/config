#!/usr/bin/env bash
#
# PostToolUse hook: enforce the source-code formatting rules from
# .agents/coding-guidelines.md after Edit/Write/MultiEdit:
#   - strip trailing whitespace
#   - replace 2+ consecutive blank lines with a single blank line
#
# Input: hook JSON on stdin (Claude Code passes tool_input.file_path).
# Exit:  0 always (post-tool-use; never block).
#
set -eu

input=$(cat)
file=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')

[ -z "$file" ] && exit 0
[ ! -f "$file" ] && exit 0

case "$file" in
  *.java|*.kt|*.kts) ;;
  *) exit 0 ;;
esac

tmp=$(mktemp)
awk '
  { sub(/[ \t]+$/, "") }
  /^$/ { blank++; if (blank > 1) next; print; next }
  { blank = 0; print }
' "$file" > "$tmp" && mv "$tmp" "$file"
