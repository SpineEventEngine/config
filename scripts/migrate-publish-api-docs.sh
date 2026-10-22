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

# Rewrites a consumer's own Gradle scripts from the `updateGitHubPages { }` API to
# `publishApiDocs { }`. Called by `migrate`.
#
# The `UpdateGitHubPages` plugin was renamed `PublishApiDocs`, with no deprecated
# aliases, and its API docs moved out of `publish` into the `publish-api-docs.yml`
# workflow, which runs the root `publishAllApiDocs` task. `migrate` deletes the stale
# plugin sources, but it preserves `buildSrc/src/main/kotlin/module.gradle.kts`, and
# most consumers call the old API from there; their `buildSrc` then stops compiling
# (`Unresolved reference 'updateGitHubPages'`).
#
# The script looks at the Kotlin sources and scripts under `buildSrc/src` and the
# tracked `*.gradle.kts` files elsewhere that name the old API. The files that
# `config` distributes no longer name it, so what is found is the consumer's own.
# In each, the script rewrites the forms the consumers are known to use:
#
#   * `import io.spine.gradle.github.pages.updateGitHubPages`
#       -> `import io.spine.gradle.github.pages.publishApiDocs`;
#   * `updateGitHubPages {` and `updateGitHubPages() {` -> `publishApiDocs {`;
#   * the dependency of `publish` on the docs task, deleted with the blank line
#     before it (or else the one after it), in either known form:
#
#         tasks.named("publish") {
#             dependsOn("${project.path}:updateGitHubPages")
#         }
#
#         project.afterEvaluate {
#             val publish = tasks.findByName("publish")
#             publish?.dependsOn("${project.path}:updateGitHubPages")
#         }
#
#     Kept, it would fail `publish` with "Task with path ':x:updateGitHubPages'
#     not found". It is not renamed: the docs are no longer a part of `publish`;
#   * a top-level `fun <Receiver>.configureGitHubPages()` of a `*.gradle.kts` script,
#     and its calls -> `configureApiDocs()`, with the KDoc of `configureApiDocs()`
#     from `jvm-module.gradle.kts` when the function has none. A script's top-level
#     functions are visible only in the script, so all the calls are in the same file.
#
# Every rewrite matches only the old form, so a second run changes nothing. A
# file is written only when its content changes, and keeps its CRLF line endings.
#
# Any use of the old API left afterwards is in a form this script does not know;
# it is reported, with the file, the line, and the replacements, for the developer
# to fix by hand. So is any tracked workflow that names the old task. The script
# fails only when it cannot read or write a file.
#
# Usage: migrate-publish-api-docs.sh [ROOT]
#   ROOT  the root of the consumer repository (default: `..`)
# Run from the `config` directory (as `migrate` does) so the default resolves.

root="${1:-..}"
cd "$root"

old_api='updateGitHubPages|UpdateGitHubPages'

# Whether the consumer is a Git work tree; the tracked files are found with Git.
in_git=false
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  in_git=true
fi

# Prints the paths of the tracked files that match the given pathspecs and name the
# old API, one per line. `-z` keeps Git from quoting a path with non-ASCII characters.
function tracked_naming_old_api() {
  [ "$in_git" = true ] || return 0
  git ls-files -z -- "$@" \
  | while IFS= read -r -d '' f; do
      if [ -f "$f" ] && grep -qE "$old_api" "$f"; then
        printf '%s\n' "$f"
      fi
    done
}

# Prints the paths of the files to rewrite, one per line.
function scripts_to_rewrite() {
  if [ -d buildSrc/src ]; then
    grep -rlE --include='*.kt' --include='*.kts' "$old_api" buildSrc/src || true
  fi
  # Git lists a submodule only as a gitlink, so excluding `config` matters only for
  # a consumer that holds it as a plain directory.
  tracked_naming_old_api '*.gradle.kts' ':(exclude)buildSrc/' ':(exclude)config/'
}

# Prints the paths of the tracked workflows that name the old task, one per line.
function workflows_naming_old_task() {
  tracked_naming_old_api '.github/workflows/*'
}

# The KDoc of `configureApiDocs()` in `jvm-module.gradle.kts`, word for word.
IFS= read -r -d '' kdoc <<'KDOC' || true
/**
 * Applies the plugin that registers the `publishApiDocs` task of this module.
 *
 * The `publish-api-docs.yml` workflow runs the task through `publishAllApiDocs` of
 * the root project, which depends on it for the published modules only. It is not
 * a part of `publish`.
 */
KDOC

# Rewrites the file named by `FILENAME` into the file named by `MIGRATE_OUT`, and
# prints `1` when the content changed, `0` otherwise. The `script` variable is `1` for
# a `*.gradle.kts` script; `MIGRATE_KDOC` holds the KDoc to add. The paths and the
# KDoc come through the environment, which, unlike `-v`, leaves backslashes alone.
#
# Regular expressions use bracket expressions for literal punctuation and `[ \t]`
# for whitespace; every `awk` in use reads these the same way: BSD `awk` on macOS,
# `gawk` in Git Bash, `mawk` on Debian and Ubuntu.
read -r -d '' rewrite <<'AWK' || true
function blank(s) { return s ~ /^[ \t]*$/ }

function closing_brace(s) { return s ~ /^[ \t]*[}][ \t]*$/ }

# A statement of its own that makes a task depend on the docs task, and on nothing
# else: a `dependsOn` with more arguments is left as it is, and reported.
function docs_dependency(s) {
    return s ~ /^[ \t]*(publish[?]?[.])?dependsOn[(]"[^"]*updateGitHubPages"[)][ \t]*$/
}

# Marks the lines `a` to `b` dropped, with the blank line before them, or else
# the one after them.
function drop(a, b,    i) {
    for (i = a; i <= b; i++) {
        dropped[i] = 1
    }
    if (a > 1 && blank(line[a - 1]) && !dropped[a - 1]) {
        dropped[a - 1] = 1
    } else if (b < n && blank(line[b + 1])) {
        dropped[b + 1] = 1
    }
    changed = 1
}

# Replaces each match of `re` in `s` with `to`, unless the match continues a
# longer name.
function replace(s, re, to,    out, before) {
    out = ""
    while (match(s, re)) {
        before = substr(s, 1, RSTART - 1)
        out = out before (before ~ /[A-Za-z0-9_]$/ ? substr(s, RSTART, RLENGTH) : to)
        s = substr(s, RSTART + RLENGTH)
    }
    return out s
}

function emit(s, i) {
    printf "%s%s\n", s, (cr[i] ? "\r" : "") > out
}

BEGIN {
    out = ENVIRON["MIGRATE_OUT"]
    kdoc_text = ENVIRON["MIGRATE_KDOC"]
    sub(/\n$/, "", kdoc_text)
    kdoc_lines = split(kdoc_text, kdoc, "\n")
}

{
    cr[NR] = sub(/\r$/, "")
    line[NR] = $0
}

END {
    n = NR
    old_import = "^import[ \t]+io[.]spine[.]gradle[.]github[.]pages[.]updateGitHubPages[ \t]*$"
    new_import = "^import[ \t]+io[.]spine[.]gradle[.]github[.]pages[.]publishApiDocs[ \t]*$"
    old_function = "^fun[ \t]+[A-Za-z0-9_.<>]+[.]configureGitHubPages[(]"
    new_function = "^fun[ \t]+[A-Za-z0-9_.<>]+[.]configureApiDocs[(]"
    # A declaration, such as `fun Project.updateGitHubPages(...) {`, is not a call.
    declaration = "^[ \t]*([a-z]+[ \t]+)*fun[ \t]"
    named_publish = "^[ \t]*tasks[.]named[(]\"publish\"[)][ \t]*[{][ \t]*$"
    after_evaluate = "^[ \t]*(project[.])?afterEvaluate[ \t]*[{][ \t]*$"
    find_publish = "^[ \t]*val[ \t]+publish[ \t]*=[ \t]*" \
        "tasks[.]findByName[(]\"publish\"[)][ \t]*$"
    # A string, not a `/.../` literal: a `/` ends a literal even inside brackets.
    kdoc_end = "[*]/[ \t]*$"

    imported = 0
    defines_old = 0
    defines_new = 0
    for (i = 1; i <= n; i++) {
        if (line[i] ~ new_import) imported = 1
        if (line[i] ~ old_function) defines_old = 1
        if (line[i] ~ new_function) defines_new = 1
    }
    rename_function = script && defines_old && !defines_new

    for (i = 1; i <= n; i++) {
        if (line[i] ~ named_publish \
                && docs_dependency(line[i + 1]) && closing_brace(line[i + 2])) {
            drop(i, i + 2)
        } else if (line[i] ~ after_evaluate && line[i + 1] ~ find_publish \
                && docs_dependency(line[i + 2]) && closing_brace(line[i + 3])) {
            drop(i, i + 3)
        }
    }

    for (i = 1; i <= n; i++) {
        if (dropped[i]) {
            continue
        }
        s = line[i]
        if (s ~ old_import) {
            changed = 1
            if (imported) {
                continue
            }
            s = "import io.spine.gradle.github.pages.publishApiDocs"
            imported = 1
        }
        if (s !~ declaration) {
            s = replace(s, "updateGitHubPages([(][)])?[ \t]*[{]", "publishApiDocs {")
        }
        if (rename_function) {
            if (s ~ old_function && line[i - 1] !~ kdoc_end) {
                for (k = 1; k <= kdoc_lines; k++) {
                    emit(kdoc[k], i)
                }
            }
            s = replace(s, "configureGitHubPages[(]", "configureApiDocs(")
        }
        if (s != line[i]) {
            changed = 1
        }
        emit(s, i)
    }
    print (changed ? 1 : 0)
}
AWK

# The rewritten text goes outside the consumer's tree, so that a killed run leaves
# nothing for the closing `git add ./buildSrc/src` of `migrate` to stage.
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
tmp="$work/rewritten"

while IFS= read -r f; do
  [ -n "$f" ] || continue
  script=0
  case "$f" in
    *.gradle.kts) script=1 ;;
  esac
  changed=$(MIGRATE_OUT="$tmp" MIGRATE_KDOC="$kdoc" awk -v script="$script" "$rewrite" "$f")
  if [ "$changed" = 1 ]; then
    # `cp` onto the target keeps its mode.
    cp "$tmp" "$f"
    echo "Rewrote \`$f\` for \`publishApiDocs\`"
  fi
done < <(scripts_to_rewrite | sort -u)

# Reports what is left of the old API in the files above and in the workflows.
left=$( { scripts_to_rewrite; workflows_naming_old_task; } | sort -u \
        | while IFS= read -r f; do
            grep -nE "$old_api" "$f" | while IFS= read -r hit; do
              printf '  %s:%s\n' "$f" "$hit"
            done || true
          done )
if [ -n "$left" ]; then
  {
    echo "WARNING: these lines still use the API that \`PublishApiDocs\` replaced:"
    printf '%s\n' "$left"
    echo "  Change them by hand, in the same commit as the \`config\` pull:"
    echo "    \`updateGitHubPages { }\` -> \`publishApiDocs { }\`" \
         "(import it from \`io.spine.gradle.github.pages\`);"
    echo "    \`UpdateGitHubPagesExtension\` -> \`PublishApiDocsExtension\`;"
    echo "    \`TaskName.updateGitHubPages\` -> \`TaskName.publishApiDocs\`;"
    echo "    a dependency of \`publish\` on the \`updateGitHubPages\` task," \
         "or \`-x updateGitHubPages\`"
    echo "      -> delete it: the \`publish-api-docs.yml\` workflow publishes the docs"
    echo "      with the \`publishAllApiDocs\` task of the root project."
  } >&2
fi
