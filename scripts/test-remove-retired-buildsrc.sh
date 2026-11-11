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

set -eo pipefail

# Regression checks for `scripts/remove-retired-buildsrc.sh`, which removes from a
# consumer's `buildSrc` the files `config` deleted in the last two years, unless the
# consumer's own code still uses them.
#
# The fixture is a fake `config` Git repository with backdated commits. It ships files,
# deletes most of them six months ago, deletes one more than two years ago, and ships
# one deleted file again. A consumer holds every file `config` once shipped, plus its
# own code, which uses some of the retired ones in each way the script knows:
#
#   * an import of a retired class keeps its package, helpers included (`logging`'s
#     `JacocoConfig`); an import of a class `config` still ships from the same package
#     does not (`core-jvm`'s `KoverConfig`);
#   * `<package>.*`, a file of the consumer's own in the package, `module.gradle.kts`,
#     and a script not committed yet keep a package; a file ignored by Git, a comment
#     naming the class, and an import from a subpackage do not;
#   * a retired package a kept one imports stays too;
#   * a script plugin stays while the consumer applies it;
#   * a Groovy script is reported and kept; files deleted more than two years ago, and
#     files `config` ships again, are not touched.
#
# Then: `--dry-run` removes nothing; a second run changes nothing; a shallow clone of
# `config` removes nothing; and the REAL `migrate` runs the script and stages the
# deletions. That run follows `scripts/test-migrate-ide-files.sh`: read its header for
# why `adopt-shared-agents` is stubbed and why `cp: No such file or directory` noise in
# the captured log is expected.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
config_dir="$(cd "$script_dir/.." && pwd)"

fail=0
pass() { echo "PASS: $1"; }
f-ail() { echo "FAIL: $1" >&2; fail=1; }

for required in migrate scripts/remove-retired-buildsrc.sh scripts/update-gitignore.sh \
                scripts/migrate-publish-api-docs.sh .gitignore; do
  [ -f "$config_dir/$required" ] \
    || { echo "FAIL: cannot find '$required' under $config_dir" >&2; exit 1; }
done

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

kotlin=buildSrc/src/main/kotlin

# Writes the file `$2` under the directory `$1` with the rest of the arguments as lines.
put() {
  local dir="$1" file="$2"
  shift 2
  mkdir -p "$(dirname "$dir/$file")"
  printf '%s\n' "$@" > "$dir/$file"
}

# Commits everything in the repository `$1` at `$2` days ago.
commit_days_ago() {
  local repo="$1" days="$2" message="$3" date
  date="$(( $(date +%s) - days * 86400 )) +0000"
  git -C "$repo" add -A
  GIT_AUTHOR_DATE="$date" GIT_COMMITTER_DATE="$date" \
    git -C "$repo" commit -qm "$message"
}

new_repo() {
  git init -q "$1"
  git -C "$1" config user.email test@example.com
  git -C "$1" config user.name test
}

# --- The fake `config`. --------------------------------------------------------------
origin="$work/config-origin"
new_repo "$origin"

put "$origin" "$kotlin/io/spine/ancient/Old.kt" "package io.spine.ancient" "object Old"
commit_days_ago "$origin" 1100 "Ship a file"
git -C "$origin" rm -q "$kotlin/io/spine/ancient/Old.kt"
commit_days_ago "$origin" 900 "Delete it more than two years ago"

# `cov` keeps `Kover`; `pom` keeps `PomGenerator`; `sub/inner` and `back` stay shipped.
put "$origin" "$kotlin/io/spine/dependency/Lib.kt" "package io.spine.dependency" "object Lib"
put "$origin" "$kotlin/io/spine/cov/Kover.kt" "package io.spine.cov" "object Kover"
put "$origin" "$kotlin/io/spine/cov/Jacoco.kt" "package io.spine.cov" "" \
    "import io.spine.dep.Dep" "" "object Jacoco { val helper = Helper; val dep = Dep }"
put "$origin" "$kotlin/io/spine/cov/Helper.kt" "package io.spine.cov" "object Helper"
put "$origin" "$kotlin/io/spine/dep/Dep.kt" "package io.spine.dep" "object Dep"
put "$origin" "$kotlin/io/spine/pom/Pom.kt" "package io.spine.pom" "object PomGenerator"
put "$origin" "$kotlin/io/spine/pom/Comparator.kt" "package io.spine.pom" \
    "object VersionComparator"
put "$origin" "buildSrc/src/test/kotlin/io/spine/pom/ComparatorSpec.kt" \
    "package io.spine.pom" "class ComparatorSpec { val c = VersionComparator }"
put "$origin" "$kotlin/io/spine/star/Starred.kt" "package io.spine.star" "object Starred"
put "$origin" "$kotlin/io/spine/own/Shared.kt" "package io.spine.own" "object Shared"
put "$origin" "$kotlin/io/spine/mod/ModUsed.kt" "package io.spine.mod" "object ModUsed"
put "$origin" "$kotlin/io/spine/untracked/Untr.kt" "package io.spine.untracked" "object Untr"
put "$origin" "$kotlin/io/spine/ignored/Ign.kt" "package io.spine.ignored" "object Ign"
put "$origin" "$kotlin/io/spine/comment/Comm.kt" "package io.spine.comment" "object Comm"
put "$origin" "$kotlin/io/spine/sub/X.kt" "package io.spine.sub" \
    "// Unrelated to the inner package." "object X"
put "$origin" "$kotlin/io/spine/sub/inner/Shipped.kt" "package io.spine.sub.inner" \
    "object Shipped"
put "$origin" "$kotlin/io/spine/back/Back.kt" "package io.spine.back" "object Back"
put "$origin" "$kotlin/kept-plugin.gradle.kts" "plugins { java }"
put "$origin" "$kotlin/dropped-plugin.gradle.kts" "plugins { java }"
put "$origin" "buildSrc/src/main/groovy/legacy.gradle" "apply plugin: 'java'"
commit_days_ago "$origin" 400 "Ship the files the consumer receives"

# The template consumer: a copy of everything shipped so far, before the deletions.
template="$work/template"
cp -R "$origin" "$template"
rm -rf "$template/.git"
put "$template" "$kotlin/io/spine/ancient/Old.kt" "package io.spine.ancient" "object Old"

retired=(cov/Jacoco.kt cov/Helper.kt dep/Dep.kt pom/Comparator.kt star/Starred.kt
         own/Shared.kt mod/ModUsed.kt untracked/Untr.kt ignored/Ign.kt comment/Comm.kt
         sub/X.kt back/Back.kt)
for f in "${retired[@]}"; do
  git -C "$origin" rm -q "$kotlin/io/spine/$f"
done
git -C "$origin" rm -q "buildSrc/src/test/kotlin/io/spine/pom/ComparatorSpec.kt" \
    "$kotlin/kept-plugin.gradle.kts" "$kotlin/dropped-plugin.gradle.kts" \
    "buildSrc/src/main/groovy/legacy.gradle"
commit_days_ago "$origin" 180 "Retire most of them"

put "$origin" "$kotlin/io/spine/back/Back.kt" "package io.spine.back" "object Back"
commit_days_ago "$origin" 30 "Ship one again"

# The consumer's own code.
put "$template" "build.gradle.kts" \
    "import io.spine.cov.Jacoco" \
    "import io.spine.pom.PomGenerator" \
    "import io.spine.star.*" \
    "import io.spine.sub.inner.Shipped" \
    "" \
    "// Comm used to configure this build." \
    "plugins {" "    id(\"kept-plugin\")" "}"
put "$template" "$kotlin/module.gradle.kts" "import io.spine.mod.ModUsed"
put "$template" "$kotlin/io/spine/own/Mine.kt" "package io.spine.own" \
    "object Mine { val shared = Shared }"
put "$template" ".gitignore" "build/"
put "$template" "build/generated.kts" "val ignored = io.spine.ignored.Ign"

# Makes a consumer from the template at `$1`: all committed but `extra.gradle.kts`.
new_consumer() {
  cp -R "$template" "$1"
  new_repo "$1"
  git -C "$1" add -A
  git -C "$1" commit -qm "A consumer of config"
  put "$1" "extra.gradle.kts" "val untracked = io.spine.untracked.Untr"
}

# A `config` checkout carrying the script under test.
cfg="$work/config"
git clone -q "$origin" "$cfg"
mkdir -p "$cfg/scripts"
cp "$config_dir/scripts/remove-retired-buildsrc.sh" "$cfg/scripts/"

removed=(io/spine/pom/Comparator.kt io/spine/ignored/Ign.kt io/spine/comment/Comm.kt
         io/spine/sub/X.kt dropped-plugin.gradle.kts)
kept=(io/spine/cov/Jacoco.kt io/spine/cov/Helper.kt io/spine/cov/Kover.kt io/spine/dep/Dep.kt
      io/spine/pom/Pom.kt io/spine/star/Starred.kt io/spine/own/Shared.kt io/spine/own/Mine.kt
      io/spine/mod/ModUsed.kt io/spine/untracked/Untr.kt io/spine/sub/inner/Shipped.kt
      io/spine/ancient/Old.kt io/spine/back/Back.kt kept-plugin.gradle.kts module.gradle.kts)

check_removed() {
  local consumer="$1" label="$2" f
  for f in "${removed[@]}"; do
    if [ -e "$consumer/$kotlin/$f" ]; then f-ail "$label: '$f' was not removed"
    else pass "$label: '$f' removed"; fi
  done
  if [ -e "$consumer/buildSrc/src/test/kotlin/io/spine/pom/ComparatorSpec.kt" ]; then
    f-ail "$label: the test of a removed package was not removed"
  else
    pass "$label: the test of a removed package removed with it"
  fi
}

check_kept() {
  local consumer="$1" label="$2" f
  for f in "${kept[@]}"; do
    if [ -e "$consumer/$kotlin/$f" ]; then pass "$label: '$f' kept"
    else f-ail "$label: '$f' was removed"; fi
  done
  if [ -e "$consumer/buildSrc/src/main/groovy/legacy.gradle" ]; then
    pass "$label: the Groovy script kept"
  else
    f-ail "$label: the Groovy script was removed"
  fi
}

expect_output() {
  local log="$1" text="$2"
  if grep -qF -e "$text" "$log"; then pass "reports: $text"
  else f-ail "the output lacks: $text"; cat "$log" >&2; fi
}

# --- (1) `--dry-run` reports and removes nothing. ------------------------------------
consumer="$work/consumer-dry"
new_consumer "$consumer"
before="$(cd "$consumer" && find buildSrc -type f | sort)"
bash "$cfg/scripts/remove-retired-buildsrc.sh" --dry-run "$consumer" > "$work/dry.log" 2>&1
if [ "$before" = "$(cd "$consumer" && find buildSrc -type f | sort)" ]; then
  pass "--dry-run removes nothing"
else
  f-ail "--dry-run changed the files"
fi
expect_output "$work/dry.log" "Would remove \`$kotlin/io/spine/pom/Comparator.kt\`."

# --- (2) A run removes the unused units and keeps the used ones. --------------------
consumer="$work/consumer"
new_consumer "$consumer"
if bash "$cfg/scripts/remove-retired-buildsrc.sh" "$consumer" > "$work/run.log" 2>&1; then
  pass "the script exits 0"
else
  f-ail "the script failed"; cat "$work/run.log" >&2
fi
check_removed "$consumer" "run"
check_kept "$consumer" "run"
if [ -d "$consumer/$kotlin/io/spine/comment" ]; then
  f-ail "the directory left empty was not removed"
else
  pass "the directory left empty removed"
fi
# Expects the report of keeping the unit `$1` of `$2` files, for the reason `$3`.
expect_kept() {
  expect_output "$work/run.log" "Keeping \`$1\` ($2 retired file(s)): $3."
}
expect_kept io.spine.cov 2 "\`build.gradle.kts\` uses \`Jacoco\`"
expect_kept io.spine.dep 1 "\`$kotlin/io/spine/cov/Jacoco.kt\` uses \`Dep\`"
expect_kept io.spine.star 1 "\`build.gradle.kts\` imports \`io.spine.star.*\`"
expect_kept io.spine.own 1 "\`$kotlin/io/spine/own/Mine.kt\` is in the package"
expect_kept io.spine.mod 1 "\`$kotlin/module.gradle.kts\` uses \`ModUsed\`"
expect_kept io.spine.untracked 1 "\`extra.gradle.kts\` uses \`Untr\`"
expect_kept kept-plugin 1 "\`build.gradle.kts\` applies \`kept-plugin\`"
expect_output "$work/run.log" \
    "Keeping \`buildSrc/src/main/groovy/legacy.gradle\`: neither a Kotlin file"

# --- (3) A second run changes nothing. -----------------------------------------------
before="$(cd "$consumer" && find buildSrc -type f | sort)"
bash "$cfg/scripts/remove-retired-buildsrc.sh" "$consumer" > "$work/rerun.log" 2>&1
if [ "$before" = "$(cd "$consumer" && find buildSrc -type f | sort)" ] \
   && ! grep -q '^Removed' "$work/rerun.log"; then
  pass "a second run changes nothing"
else
  f-ail "a second run changed the files"; cat "$work/rerun.log" >&2
fi

# --- (4) A shallow clone of `config` holds no deletions: nothing is removed. ---------
shallow="$work/config-shallow"
git clone -q --depth 1 "file://$origin" "$shallow"
mkdir -p "$shallow/scripts"
cp "$config_dir/scripts/remove-retired-buildsrc.sh" "$shallow/scripts/"
consumer="$work/consumer-shallow"
new_consumer "$consumer"
before="$(cd "$consumer" && find buildSrc -type f | sort)"
bash "$shallow/scripts/remove-retired-buildsrc.sh" "$consumer" > "$work/shallow.log" 2>&1
if [ "$before" = "$(cd "$consumer" && find buildSrc -type f | sort)" ]; then
  pass "a shallow \`config\` removes nothing"
else
  f-ail "a shallow \`config\` removed files"; cat "$work/shallow.log" >&2
fi
expect_output "$work/shallow.log" "is a shallow clone"

# --- (5) The real `migrate` runs the script and stages the deletions. ---------------
consumer="$work/consumer-migrate"
new_consumer "$consumer"
git -C "$consumer" -c protocol.file.allow=always submodule add -q "$origin" config
git -C "$consumer" commit -qm "Add config"
for f in migrate .gitignore scripts/remove-retired-buildsrc.sh scripts/update-gitignore.sh \
         scripts/migrate-publish-api-docs.sh; do
  mkdir -p "$(dirname "$consumer/config/$f")"
  cp "$config_dir/$f" "$consumer/config/$f"
done
printf '#!/usr/bin/env bash\nexit 0\n' > "$consumer/config/adopt-shared-agents"
chmod +x "$consumer/config/adopt-shared-agents"
( cd "$consumer/config" && bash migrate ) > "$work/migrate.log" 2>&1 || true
check_removed "$consumer" "migrate"
check_kept "$consumer" "migrate"
staged="$(git -C "$consumer" diff --cached --name-status -- buildSrc)"
if printf '%s\n' "$staged" | grep -qx "D	$kotlin/io/spine/pom/Comparator.kt"; then
  pass "migrate stages the deletion"
else
  f-ail "migrate did not stage the deletion"; printf '%s\n' "$staged" >&2
  cat "$work/migrate.log" >&2
fi

if [ "$fail" -ne 0 ]; then
  echo "Some checks failed." >&2
  exit 1
fi
echo "All checks passed."
