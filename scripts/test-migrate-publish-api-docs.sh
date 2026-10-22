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

# Regression checks for `scripts/migrate-publish-api-docs.sh`, which rewrites a
# consumer's own Gradle scripts from `updateGitHubPages { }` to `publishApiDocs { }`:
#
#   * each form the consumers are known to use is rewritten to the expected text.
#     The fixtures reproduce them: `setupDocPublishing()` with
#     `tasks.named("publish")` (`compiler`, `validation`, `core-jvm`, ...), the root
#     `build.gradle.kts` of `gcloud-jvm`, `configureGitHubPages()` with
#     `project.afterEvaluate` (`time`, `base-libraries`, `testlib`), and a top-level
#     call in a script (`change`). The expected `module.gradle.kts` is `time`'s fix by hand;
#   * a form the script does not know is left as it is, and reported with its file
#     and line: a `dependsOn` with more than the docs task, a declaration of
#     `updateGitHubPages()`, the extension type; so is a workflow that names the
#     old task;
#   * a function's own KDoc is kept, and the KDoc the script adds is the one of
#     `jvm-module.gradle.kts`;
#   * files that do not name the old API, and untracked scripts outside `buildSrc`,
#     are not touched; a path with non-ASCII characters is not missed; CRLF line
#     endings and file modes are kept;
#   * a second run changes nothing; outside a Git work tree, only `buildSrc` is
#     rewritten;
#   * the REAL `migrate` restores the consumer's `module.gradle.kts`, rewrites it,
#     and stages it. The fixture follows `scripts/test-migrate-ide-files.sh`; read
#     the header there for why `adopt-shared-agents` is stubbed and why
#     `cp: No such file or directory` noise in the captured log is expected.

set -eo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
config_dir="$(cd "$script_dir/.." && pwd)"
script="$config_dir/scripts/migrate-publish-api-docs.sh"

fail=0
pass() { echo "PASS: $1"; }
f-ail() { echo "FAIL: $1" >&2; fail=1; }

for required in migrate scripts/migrate-publish-api-docs.sh scripts/update-gitignore.sh \
                .gitignore buildSrc/src/main/kotlin/jvm-module.gradle.kts; do
  [ -f "$config_dir/$required" ] \
    || { echo "FAIL: cannot find '$required' under $config_dir" >&2; exit 1; }
done

# File mode, portable across GNU and BSD `stat`; see `test-migrate-publishing-pin.sh`.
mode_of() {
  local mode
  mode="$(stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1")"
  [[ "$mode" =~ ^[0-7]{3,4}$ ]] \
    || { echo "FAIL: cannot read the mode of '$1' — stat answered '$mode'" >&2; exit 1; }
  echo "$mode"
}

# Converts the line endings of standard input to CRLF.
crlf() { awk '{ printf "%s\r\n", $0 }'; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Passes when the file $1 has the content of the file $2.
expect_file() {
  if diff -u "$2" "$1" > "$work/diff"; then
    pass "$3"
  else
    f-ail "$3 — unexpected content:"; cat "$work/diff" >&2
  fi
}

# =============================================================================
# Fixtures: each surveyed form, before and after.
# =============================================================================
fixtures="$work/fixtures"
mkdir -p "$fixtures"

# The form of `time`, `base-libraries`, and `testlib`; the expected text is `time`'s
# hand-made fix.
cat > "$fixtures/after-evaluate.before" <<'KTS'
import io.spine.dependency.local.Base
import io.spine.gradle.github.pages.updateGitHubPages
import io.spine.gradle.javac.configureJavac

project.run {
    setTaskDependencies(generatedDir)

    configureGitHubPages()
}

typealias Module = Project

fun Module.setTaskDependencies(generatedDir: String) {
    tasks {
        clean.configure {
            dependsOn(cleanGenerated)
        }

        project.afterEvaluate {
            val publish = tasks.findByName("publish")
            publish?.dependsOn("${project.path}:updateGitHubPages")
        }
    }
    afterEvaluate {
        configureTaskDependencies()
    }
}

fun Module.configureGitHubPages() {
    updateGitHubPages() {
        rootFolder.set(rootDir)
    }
}
KTS
cat > "$fixtures/after-evaluate.after" <<'KTS'
import io.spine.dependency.local.Base
import io.spine.gradle.github.pages.publishApiDocs
import io.spine.gradle.javac.configureJavac

project.run {
    setTaskDependencies(generatedDir)

    configureApiDocs()
}

typealias Module = Project

fun Module.setTaskDependencies(generatedDir: String) {
    tasks {
        clean.configure {
            dependsOn(cleanGenerated)
        }
    }
    afterEvaluate {
        configureTaskDependencies()
    }
}

/**
 * Applies the plugin that registers the `publishApiDocs` task of this module.
 *
 * The `publish-api-docs.yml` workflow runs the task through `publishAllApiDocs` of
 * the root project, which depends on it for the published modules only. It is not
 * a part of `publish`.
 */
fun Module.configureApiDocs() {
    publishApiDocs {
        rootFolder.set(rootDir)
    }
}
KTS

# The form of `compiler`, `validation`, `core-jvm`, and the other repositories
# whose function also opens with a KDoc of its own.
cat > "$fixtures/named-publish.before" <<'KTS'
import io.spine.gradle.github.pages.updateGitHubPages

project.run {
    setupDocPublishing()
}

/**
 * Configures documentation publishing for this subproject.
 */
fun Module.setupDocPublishing() {
    updateGitHubPages {
        rootFolder.set(rootDir)
    }

    tasks.named("publish") {
        dependsOn("${project.path}:updateGitHubPages")
    }
}
KTS
cat > "$fixtures/named-publish.after" <<'KTS'
import io.spine.gradle.github.pages.publishApiDocs

project.run {
    setupDocPublishing()
}

/**
 * Configures documentation publishing for this subproject.
 */
fun Module.setupDocPublishing() {
    publishApiDocs {
        rootFolder.set(rootDir)
    }
}
KTS

# The root `build.gradle.kts` of `gcloud-jvm`: a tracked script outside `buildSrc`.
to_root='s/Module\.setupDocPublishing/Project.setupPublishing/;'
to_root+=' s/setupDocPublishing()/setupPublishing()/'
sed "$to_root" "$fixtures/named-publish.before" > "$fixtures/root.before"
sed "$to_root" "$fixtures/named-publish.after"  > "$fixtures/root.after"

# A `configureGitHubPages()` with a KDoc of its own, which stays, and a `dependsOn`
# with more than the docs task, which stays as it is and is reported.
cat > "$fixtures/kept.before" <<'KTS'
import io.spine.gradle.github.pages.updateGitHubPages

/**
 * Publishes the docs of this module.
 */
fun Module.configureGitHubPages() {
    updateGitHubPages {
        rootFolder.set(rootDir)
    }

    tasks.named("publish") {
        dependsOn("generateStuff", "${project.path}:updateGitHubPages")
    }
}

configureGitHubPages()
KTS
cat > "$fixtures/kept.after" <<'KTS'
import io.spine.gradle.github.pages.publishApiDocs

/**
 * Publishes the docs of this module.
 */
fun Module.configureApiDocs() {
    publishApiDocs {
        rootFolder.set(rootDir)
    }

    tasks.named("publish") {
        dependsOn("generateStuff", "${project.path}:updateGitHubPages")
    }
}

configureApiDocs()
KTS

# The form of `change`: a top-level call in a script. The script imports the new
# name already, so the old import goes.
cat > "$fixtures/top-level.before" <<'KTS'
import io.spine.gradle.github.pages.publishApiDocs
import io.spine.gradle.github.pages.updateGitHubPages

updateGitHubPages() {
    rootFolder.set(rootDir)
}

project.forceDependencies()
KTS
cat > "$fixtures/top-level.after" <<'KTS'
import io.spine.gradle.github.pages.publishApiDocs

publishApiDocs {
    rootFolder.set(rootDir)
}

project.forceDependencies()
KTS

# Forms the script does not know: they must stay as they are, and be reported. A
# declaration of `updateGitHubPages()` is not a call, with an annotation too.
cat > "$fixtures/Unknown.kt" <<'KT'
package io.acme.gradle

import io.spine.gradle.github.pages.UpdateGitHubPagesExtension
import org.gradle.api.Project

fun Project.docs(): UpdateGitHubPagesExtension =
    extensions.getByType(UpdateGitHubPagesExtension::class.java)

fun Project.updateGitHubPages() {
    docs()
}

@Suppress("unused") internal fun Settings.updateGitHubPages() {
}
KT

# A script that holds nothing but the dependency on the docs task: it is left empty.
cat > "$fixtures/dependency-only.before" <<'KTS'
tasks.named("publish") {
    dependsOn("${project.path}:updateGitHubPages")
}
KTS
: > "$fixtures/empty"
cat > "$fixtures/custom.yml" <<'YML'
name: Custom publish
on: push
jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew publish -x updateGitHubPages
YML

# The KDoc the script adds must stay the one of `configureApiDocs()` in
# `jvm-module.gradle.kts`, which the expected text above holds.
kdoc_of_configure_api_docs() {
  awk '/^\/\*\*/ { kdoc = "" } { kdoc = kdoc $0 "\n" }
       /^fun Module[.]configureApiDocs[(]/ { printf "%s", kdoc; exit }' "$1" \
  | sed '$d'
}
jvm_module="$config_dir/buildSrc/src/main/kotlin/jvm-module.gradle.kts"
kdoc_of_configure_api_docs "$jvm_module" > "$work/kdoc.jvm-module"
kdoc_of_configure_api_docs "$fixtures/after-evaluate.after" > "$work/kdoc.expected"
if [ -s "$work/kdoc.jvm-module" ] && diff -u "$work/kdoc.jvm-module" "$work/kdoc.expected"; then
  pass "the expected KDoc is the one of 'configureApiDocs()' in 'jvm-module.gradle.kts'"
else
  f-ail "the expected KDoc differs from the one in 'jvm-module.gradle.kts'"
fi

# =============================================================================
# (1) The script on a consumer that holds every form.
# =============================================================================
consumer="$work/consumer"
kotlin="$consumer/buildSrc/src/main/kotlin"
# A path with non-ASCII characters, which Git quotes unless told otherwise.
accented="módulo"
mkdir -p "$kotlin/io/acme/gradle" "$consumer/.github/workflows" "$consumer/server" \
         "$consumer/$accented" "$consumer/scratch"
cp "$fixtures/after-evaluate.before" "$kotlin/module.gradle.kts"
cp "$fixtures/top-level.before"      "$kotlin/docs.gradle.kts"
cp "$fixtures/kept.before"           "$kotlin/kept.gradle.kts"
# Rewritten after other files: it must not get the text of the one before it.
cp "$fixtures/dependency-only.before" "$kotlin/zz-dependency-only.gradle.kts"
cp "$fixtures/Unknown.kt"            "$kotlin/io/acme/gradle/Unknown.kt"
cp "$fixtures/root.before"           "$consumer/build.gradle.kts"
cp "$fixtures/root.before"           "$consumer/$accented/build.gradle.kts"
crlf < "$fixtures/named-publish.before" > "$consumer/server/build.gradle.kts"
cp "$fixtures/custom.yml"            "$consumer/.github/workflows/custom.yml"
printf 'plugins { java }\n'        > "$kotlin/other.gradle.kts"
# Untracked, so not the consumer's: a build output, for example.
cp "$fixtures/root.before"           "$consumer/scratch/untracked.gradle.kts"
chmod 644 "$kotlin/module.gradle.kts"
tracked=(buildSrc .github build.gradle.kts server "$accented")

git -C "$consumer" init -q
git -C "$consumer" config user.email test@example.com
git -C "$consumer" config user.name  test
# Keep the CRLF fixture as it is, whatever the host's `core.autocrlf`.
git -C "$consumer" config core.autocrlf false
git -C "$consumer" add "${tracked[@]}"
git -C "$consumer" commit -qm "A consumer on the old API"

out1="$work/run-1.out"; err1="$work/run-1.err"
if bash "$script" "$consumer" > "$out1" 2> "$err1"; then
  pass "the script ran to completion (exit 0)"
else
  f-ail "the script exited non-zero:"; cat "$out1" "$err1" >&2
fi

expect_file "$kotlin/module.gradle.kts" "$fixtures/after-evaluate.after" \
  "'configureGitHubPages()' with 'project.afterEvaluate' rewritten as it was by hand in 'time'"
expect_file "$kotlin/docs.gradle.kts" "$fixtures/top-level.after" \
  "a top-level call in another 'buildSrc' script rewritten, the old import dropped"
expect_file "$kotlin/kept.gradle.kts" "$fixtures/kept.after" \
  "a function's own KDoc kept, a 'dependsOn' with more arguments left as it is"
expect_file "$kotlin/zz-dependency-only.gradle.kts" "$fixtures/empty" \
  "a script of nothing but the docs dependency, rewritten after another, left empty"
expect_file "$consumer/build.gradle.kts" "$fixtures/root.after" \
  "a tracked script outside 'buildSrc' rewritten"
expect_file "$consumer/$accented/build.gradle.kts" "$fixtures/root.after" \
  "a tracked script under a non-ASCII path rewritten"
crlf < "$fixtures/named-publish.after" > "$fixtures/named-publish.after.crlf"
expect_file "$consumer/server/build.gradle.kts" "$fixtures/named-publish.after.crlf" \
  "'tasks.named(\"publish\")' form rewritten, CRLF line endings kept"
expect_file "$kotlin/io/acme/gradle/Unknown.kt" "$fixtures/Unknown.kt" \
  "an unknown form left as it is"
expect_file "$consumer/.github/workflows/custom.yml" "$fixtures/custom.yml" \
  "a workflow left as it is"
expect_file "$consumer/scratch/untracked.gradle.kts" "$fixtures/root.before" \
  "an untracked script outside 'buildSrc' left as it is"

for moved in buildSrc/src/main/kotlin/module.gradle.kts \
             buildSrc/src/main/kotlin/docs.gradle.kts \
             buildSrc/src/main/kotlin/kept.gradle.kts \
             buildSrc/src/main/kotlin/zz-dependency-only.gradle.kts \
             build.gradle.kts server/build.gradle.kts "$accented/build.gradle.kts"; do
  if grep -qxF "Rewrote \`$moved\` for \`publishApiDocs\`" "$out1"; then
    pass "reported the rewrite of '$moved'"
  else
    f-ail "did not report the rewrite of '$moved'; output: $(cat "$out1")"
  fi
done
if grep -qE 'other\.gradle\.kts|Unknown\.kt|untracked' "$out1"; then
  f-ail "reported a rewrite of a file it must not change: $(cat "$out1")"
else
  pass "reported no rewrite of the files it must not change"
fi

for left in 'buildSrc/src/main/kotlin/io/acme/gradle/Unknown.kt:3:' \
            'buildSrc/src/main/kotlin/io/acme/gradle/Unknown.kt:6:' \
            'buildSrc/src/main/kotlin/io/acme/gradle/Unknown.kt:7:' \
            'buildSrc/src/main/kotlin/io/acme/gradle/Unknown.kt:9:' \
            'buildSrc/src/main/kotlin/io/acme/gradle/Unknown.kt:13:' \
            'buildSrc/src/main/kotlin/kept.gradle.kts:12:' \
            '.github/workflows/custom.yml:7:'; do
  if grep -qF "  $left" "$err1"; then
    pass "warned about '$left'"
  else
    f-ail "no warning about '$left'; stderr: $(cat "$err1")"
  fi
done
if grep -qF 'publishAllApiDocs' "$err1" && grep -qF 'PublishApiDocsExtension' "$err1"; then
  pass "the warning names the replacements"
else
  f-ail "the warning does not name the replacements; stderr: $(cat "$err1")"
fi
if grep -qE 'module\.gradle\.kts|docs\.gradle\.kts|untracked' "$err1"; then
  f-ail "warned about a file it rewrote or must ignore: $(cat "$err1")"
else
  pass "no warning about the rewritten or ignored files"
fi

if [ "$(mode_of "$kotlin/module.gradle.kts")" = 644 ]; then
  pass "'module.gradle.kts' kept mode 644"
else
  f-ail "'module.gradle.kts' has mode $(mode_of "$kotlin/module.gradle.kts"), expected 644"
fi

# The script writes its temporary files outside the consumer's tree.
leaked="$(find "$consumer" -type f -name '*.??????' -not -path '*/.git/*' -print)"
if [ -z "$leaked" ]; then
  pass "no temp files left behind"
else
  f-ail "temp files leaked into the consumer: $leaked"
fi

# =============================================================================
# (2) A second run changes nothing.
# =============================================================================
git -C "$consumer" add -A "${tracked[@]}"
git -C "$consumer" commit -qm "Rewritten for the new API"

out2="$work/run-2.out"; err2="$work/run-2.err"
if bash "$script" "$consumer" > "$out2" 2> "$err2"; then
  pass "the second run ran to completion (exit 0)"
else
  f-ail "second run exited non-zero:"; cat "$out2" "$err2" >&2
fi
if [ -s "$out2" ]; then
  f-ail "second run rewrote files again: $(cat "$out2")"
else
  pass "second run rewrote nothing"
fi
if [ -z "$(git -C "$consumer" status --porcelain -- "${tracked[@]}")" ]; then
  pass "second run left the tracked files as they were"
else
  f-ail "second run changed tracked files:"; git -C "$consumer" status --porcelain >&2
fi

# =============================================================================
# (2b) A consumer that is not a Git work tree: `buildSrc` only.
# =============================================================================
plain="$work/plain"
mkdir -p "$plain/buildSrc/src/main/kotlin"
cp "$fixtures/after-evaluate.before" "$plain/buildSrc/src/main/kotlin/module.gradle.kts"
# With no Git, the script cannot tell a consumer's script from a build output.
cp "$fixtures/root.before" "$plain/build.gradle.kts"
if bash "$script" "$plain" > "$work/plain.out" 2> "$work/plain.err"; then
  pass "the script ran to completion outside a Git work tree (exit 0)"
else
  f-ail "the script exited non-zero outside a Git work tree:"
  cat "$work/plain.out" "$work/plain.err" >&2
fi
expect_file "$plain/buildSrc/src/main/kotlin/module.gradle.kts" \
  "$fixtures/after-evaluate.after" "outside a Git work tree, 'buildSrc' rewritten"
expect_file "$plain/build.gradle.kts" "$fixtures/root.before" \
  "outside a Git work tree, scripts outside 'buildSrc' left as they are"

# =============================================================================
# (2c) The first file rewritten loses every line: there is no earlier output.
# =============================================================================
only="$work/only"
mkdir -p "$only/buildSrc/src/main/kotlin"
cp "$fixtures/dependency-only.before" "$only/buildSrc/src/main/kotlin/docs.gradle.kts"
if bash "$script" "$only" > "$work/only.out" 2> "$work/only.err"; then
  pass "the script ran to completion when the first rewrite drops every line (exit 0)"
else
  f-ail "the script exited non-zero when the first rewrite drops every line:"
  cat "$work/only.out" "$work/only.err" >&2
fi
expect_file "$only/buildSrc/src/main/kotlin/docs.gradle.kts" "$fixtures/empty" \
  "a script of nothing but the docs dependency, rewritten first, left empty"

# =============================================================================
# (3) The real `migrate` restores, rewrites, and stages the consumer's script.
# =============================================================================
consumer="$work/pulling"
kotlin="$consumer/buildSrc/src/main/kotlin"
fake_config="$consumer/config"
pages="buildSrc/src/main/kotlin/io/spine/gradle/github/pages"
mkdir -p "$kotlin/io/spine/dependency" "$consumer/$pages" \
         "$fake_config/scripts" "$fake_config/buildSrc/src/main/kotlin"
cp "$fixtures/after-evaluate.before" "$kotlin/module.gradle.kts"
printf 'object Base\n' > "$kotlin/io/spine/dependency/Base.kt"
printf 'class UpdateGitHubPages\n' > "$consumer/$pages/UpdateGitHubPages.kt"

git -C "$consumer" init -q
git -C "$consumer" config user.email test@example.com
git -C "$consumer" config user.name  test
git -C "$consumer" add buildSrc
git -C "$consumer" commit -qm "A JVM consumer on the old API"

cp "$config_dir/migrate"                                "$fake_config/migrate"
cp "$config_dir/scripts/update-gitignore.sh"            "$fake_config/scripts/"
cp "$config_dir/scripts/migrate-publish-api-docs.sh"    "$fake_config/scripts/"
cp "$config_dir/.gitignore"                             "$fake_config/.gitignore"
printf '// The seed of a new consumer.\n' \
    > "$fake_config/buildSrc/src/main/kotlin/module.gradle.kts"
printf '#!/usr/bin/env bash\nexit 0\n' > "$fake_config/adopt-shared-agents"
chmod +x "$fake_config/adopt-shared-agents"

log="$work/migrate.log"
if ( cd "$fake_config" && bash migrate ) > "$log" 2>&1; then
  pass "migrate ran to completion (exit 0)"
else
  f-ail "migrate exited non-zero — see the log below"; cat "$log" >&2
fi
expect_file "$kotlin/module.gradle.kts" "$fixtures/after-evaluate.after" \
  "migrate restored the consumer's 'module.gradle.kts' and rewrote it"

staged="$(git -C "$consumer" diff --cached --name-status)"
if grep -qxF "$(printf 'M\t%s' buildSrc/src/main/kotlin/module.gradle.kts)" <<< "$staged"; then
  pass "migrate staged the rewritten 'module.gradle.kts'"
else
  f-ail "the rewritten 'module.gradle.kts' is not staged; staged: $staged"
fi
if grep -qxF "$(printf 'D\t%s' "$pages/UpdateGitHubPages.kt")" <<< "$staged"; then
  pass "migrate staged the deletion of the stale plugin source"
else
  f-ail "the stale plugin source is not deleted and staged; staged: $staged"
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "OK: all migrate-publish-api-docs regression checks passed."
else
  echo "FAILED: one or more migrate-publish-api-docs regression checks failed." >&2
  echo "--- migrate log ---" >&2; cat "$log" >&2 2>/dev/null || true
  exit 1
fi
