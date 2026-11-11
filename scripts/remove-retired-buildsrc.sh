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

set -euo pipefail
set -f            # A `*` read from an import must stay literal.
export LC_ALL=C   # One collation for every `sort` and `comm` below.

# Removes from a consumer's `buildSrc` the files that `config` deleted from its own
# `buildSrc` in the last two years, unless the consumer's own code still uses them.
# Called by `migrate`.
#
# `migrate` copies `buildSrc` with `cp -R buildSrc ..`, which never deletes. A file
# `config` drops stays in every consumer that received it, still compiled, unless it is
# removed here or by an explicit `rm -f` in `migrate`.
#
# Candidates. The files deleted under `buildSrc/src` of `config` in the last two years
# (`git log --since`), less those `config` ships again, that the consumer has, committed
# or not. A shallow clone of `config` holds less history, so fewer files are found; none
# is removed wrongly.
#
# Units. Files are kept or removed together:
#
#   * the candidate `.kt` files of one package, main and test sources alike: files of
#     a package use each other without imports;
#   * a precompiled script plugin, `*.gradle.kts`, on its own.
#
# Any other candidate (a `.kt` file outside a package, a Groovy script, a resource) is
# reported and kept.
#
# Use. The files `config` ships compile without the candidates, so only the consumer's
# own text can hold one in place: its `*.kts`, `*.kt`, and `*.gradle` files outside
# `buildSrc` (committed or not, but not ignored), the files in its `buildSrc` that
# `config` does not ship, and `module.gradle.kts`. A unit is in use when that text:
#
#   * has a file in the unit's package;
#   * names `<package>.*`, or `<package>.<name>` where `<name>` is not a subpackage
#     and occurs as a word in a file of the unit: an import, or a fully qualified use;
#   * names a script plugin's ID as a word.
#
# The text of a unit kept in use joins the text above, and the search repeats, so the
# units a kept one needs stay as well. The rest are removed; `migrate` stages their
# deletion with `git add ./buildSrc/src`.
#
# The check reads text and compiles nothing, and every doubt keeps the file. Should a
# needed file go after all, the build fails, and `git checkout -- <file>` restores it:
# `migrate` does not commit.
#
# Usage: remove-retired-buildsrc.sh [--dry-run] [ROOT]
#   --dry-run  report what would be removed, and remove nothing
#   ROOT       the root of the consumer repository (default: `..`)
# Run from the `config` directory (as `migrate` does) so the default resolves.

dry_run=false
if [ "${1:-}" = "--dry-run" ]; then
  dry_run=true
  shift
fi
root="${1:-..}"
config_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
window="2 years ago"

cd "$root"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Not a Git work tree; leaving the retired \`buildSrc\` files in place."
  exit 0
fi
if ! git -C "$config_dir" rev-parse --git-dir >/dev/null 2>&1; then
  echo "\`config\` has no Git history here; leaving the retired \`buildSrc\` files in place."
  exit 0
fi
if [ "$(git -C "$config_dir" rev-parse --is-shallow-repository)" = "true" ]; then
  echo "\`config\` is a shallow clone: only the deletions in its history are found."
fi

work="$(mktemp -d "${TMPDIR:-/tmp}/retired-buildsrc.XXXXXX")"
trap 'rm -rf "$work"' EXIT

git -C "$config_dir" ls-files buildSrc | sort -u > "$work/shipped"
git -C "$config_dir" log --since="$window" --diff-filter=D --no-renames \
    --name-only --format= -- buildSrc/src \
  | sed '/^$/d' | sort -u | comm -23 - "$work/shipped" > "$work/retired"
# Only the files on disk: the index still lists a file deleted but not yet staged.
git ls-files -co --exclude-standard buildSrc | while IFS= read -r file; do
  if [ -f "$file" ]; then
    echo "$file"
  fi
done | sort -u > "$work/present"
comm -12 "$work/present" "$work/retired" > "$work/candidates"

if [ ! -s "$work/candidates" ]; then
  echo "No retired \`buildSrc\` files to remove."
  exit 0
fi

# Prints `<path><TAB><unit>`: `pkg:<package>` for a `.kt` file in a package,
# `plugin:<ID>` for a script plugin, `other:<path>` for the rest.
unit_of() {
  awk '{
    path = $0; unit = "other:" path
    if (path ~ /^buildSrc\/src\/[^\/]+\/kotlin\/.+\/[^\/]+\.kt$/) {
      pkg = path
      sub(/^buildSrc\/src\/[^\/]+\/kotlin\//, "", pkg); sub(/\/[^\/]+$/, "", pkg)
      gsub(/\//, ".", pkg); unit = "pkg:" pkg
    } else if (path ~ /\.gradle\.kts$/) {
      id = path; sub(/^.*\//, "", id); sub(/\.gradle\.kts$/, "", id); unit = "plugin:" id
    }
    print path "\t" unit
  }' "$1"
}

unit_of "$work/candidates" > "$work/candidate-units"
cut -f2 "$work/candidate-units" | sort -u > "$work/units"

# The consumer's own files in `buildSrc`: not shipped by `config`, not candidates.
comm -23 "$work/present" "$work/shipped" | comm -23 - "$work/candidates" > "$work/own"
if [ -f buildSrc/src/main/kotlin/module.gradle.kts ]; then
  echo buildSrc/src/main/kotlin/module.gradle.kts >> "$work/own"
fi
unit_of "$work/own" > "$work/own-units"

# The text that may use a candidate, as `<path>:<line>`.
with_path() {
  # `grep -I` passes text files only.
  if [ -f "$1" ] && grep -Iq . "$1"; then
    awk -v path="$1" '{ print path ":" $0 }' "$1"
  fi
}
{
  git grep -I --untracked -e '' -- '*.kts' '*.kt' '*.gradle' ':!buildSrc' || true
  while IFS= read -r file; do
    with_path "$file"
  done < "$work/own"
} > "$work/text"

# Prints the files of a unit, one per line.
files_of() {
  awk -F'\t' -v unit="$1" '$2 == unit { print $1 }' "$work/candidate-units"
}

# Prints the `<path><TAB><name>` pairs for each `<package>.<name>` and `<package>.*`
# in the text, where `<package>` is the first argument.
names_used_from() {
  local pattern
  pattern="$(printf '%s' "$1" | sed 's/\./[.]/g')"
  awk -v pkg="$pattern" '
    BEGIN { re = "(^|[^[:alnum:]_.])" pkg "[.]([[:alnum:]_]+|[*])" }
    {
      colon = index($0, ":"); path = substr($0, 1, colon - 1); text = substr($0, colon + 1)
      while (match(text, re)) {
        found = substr(text, RSTART, RLENGTH); sub(/^.*[.]/, "", found)
        print path "\t" found
        text = substr(text, RSTART + RLENGTH)
      }
    }' "$work/text" | sort -u
}

# Whether a directory named `$2` under package `$1` exists in `config` or in the
# consumer: then `$1.$2` names a subpackage, not a declaration.
is_subpackage() {
  local dir set
  dir="$(printf '%s' "$1" | tr . /)/$2"
  for set in main test; do
    if [ -d "$config_dir/buildSrc/src/$set/kotlin/$dir" ] \
       || [ -d "buildSrc/src/$set/kotlin/$dir" ]; then
      return 0
    fi
  done
  return 1
}

# Prints why the unit is in use, or nothing.
reason_in_use() {
  local unit="$1" pkg path name hit
  case "$unit" in
    pkg:*)
      pkg="${unit#pkg:}"
      path="$(awk -F'\t' -v unit="$unit" '$2 == unit && !found { print $1; found = 1 }' \
                  "$work/own-units")"
      if [ -n "$path" ]; then
        echo "\`$path\` is in the package"
        return
      fi
      files_of "$unit" > "$work/unit-files"
      names_used_from "$pkg" > "$work/names"
      while IFS="$(printf '\t')" read -r path name; do
        if [ "$name" = "*" ]; then
          echo "\`$path\` imports \`$pkg.*\`"
          return
        fi
        is_subpackage "$pkg" "$name" && continue
        hit=false
        while IFS= read -r file; do
          if grep -q -w -F -e "$name" "$file"; then
            hit=true
            break
          fi
        done < "$work/unit-files"
        if [ "$hit" = "true" ]; then
          echo "\`$path\` uses \`$name\`"
          return
        fi
      done < "$work/names"
      ;;
    plugin:*)
      name="${unit#plugin:}"
      path="$(grep -w -F -e "$name" "$work/text" | head -n 1 | cut -d: -f1 || true)"
      if [ -n "$path" ]; then
        echo "\`$path\` applies \`$name\`"
      fi
      ;;
    other:*)
      echo "neither a Kotlin file in a package nor a script plugin; remove it by hand if unused"
      ;;
  esac
}

: > "$work/kept"   # `<unit><TAB><reason>`

# Whether the unit is kept. Reads the file instead of piping into `grep -q`, which
# could make `pipefail` report a closed pipe as a failure.
is_kept() {
  awk -F'\t' -v unit="$1" '$1 == unit { found = 1 } END { exit !found }' "$work/kept"
}

changed=true
while [ "$changed" = "true" ]; do
  changed=false
  while IFS= read -r unit; do
    if is_kept "$unit"; then
      continue
    fi
    reason="$(reason_in_use "$unit")"
    if [ -n "$reason" ]; then
      printf '%s\t%s\n' "$unit" "$reason" >> "$work/kept"
      changed=true
      # What a kept unit uses must stay too.
      files_of "$unit" > "$work/unit-files"
      while IFS= read -r file; do
        with_path "$file"
      done < "$work/unit-files" >> "$work/text"
    fi
  done < "$work/units"
done

while IFS="$(printf '\t')" read -r unit reason; do
  count="$(files_of "$unit" | wc -l | tr -d ' ')"
  case "$unit" in
    other:*) echo "Keeping \`${unit#other:}\`: $reason." ;;
    *) echo "Keeping \`${unit#*:}\` ($count retired file(s)): $reason." ;;
  esac
done < "$work/kept"

while IFS="$(printf '\t')" read -r file unit; do
  if is_kept "$unit"; then
    continue
  fi
  if [ "$dry_run" = "true" ]; then
    echo "Would remove \`$file\`."
    continue
  fi
  rm -f "$file"
  echo "Removed \`$file\`."
  # Drop the directories the removal left empty, up to `buildSrc`.
  dir="$(dirname "$file")"
  while [ "$dir" != "buildSrc" ] && [ "$dir" != "." ] && rmdir "$dir" 2>/dev/null; do
    dir="$(dirname "$dir")"
  done
done < "$work/candidate-units"
