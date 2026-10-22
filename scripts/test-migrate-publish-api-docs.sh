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

# Regression checks for `scripts/migrate-publish-api-docs.sh`, which moves a
# consumer's own Gradle scripts from `updateGitHubPages { }` to `publishApiDocs { }`:
#
#   * each form the consumers are known to use is rewritten to the expected text.
#     The fixtures reproduce them: `setupDocPublishing()` with
#     `tasks.named("publish")` (compiler, validation, core-jvm, ...), the root
#     `build.gradle.kts` of gcloud-jvm, `configureGitHubPages()` with
#     `project.afterEvaluate` (time, base-libraries, testlib), and a top-level call
#     in a script (change). The expected `module.gradle.kts` is what `time` got by
#     hand;
#   * a form the script does not know is left as it is, and reported with its file
#     and line; so is a workflow that names the old task;
#   * files that do not name the old API, and untracked scripts outside `buildSrc`,
#     are not touched; CRLF line endings and file modes are kept;
#   * a second run changes nothing;
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

for required in migrate scripts/migrate-publish-api-docs.sh scripts/update-gitignore.sh .gitignore; do
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
sed 's/Module\.setupDocPublishing/Project.setupPublishing/; s/setupDocPublishing()/setupPublishing()/' \
    "$fixtures/named-publish.before" > "$fixtures/root.before"
sed 's/Module\.setupDocPublishing/Project.setupPublishing/; s/setupDocPublishing()/setupPublishing()/' \
    "$fixtures/named-publish.after" > "$fixtures/root.after"

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

# Forms the script does not know: they must stay as they are, and be reported.
cat > "$fixtures/Unknown.kt" <<'KT'
package io.acme.gradle

import io.spine.gradle.github.pages.UpdateGitHubPagesExtension
import org.gradle.api.Project

fun Project.docs(): UpdateGitHubPagesExtension =
    extensions.getByType(UpdateGitHubPagesExtension::class.java)
KT
cat > "$fixtures/custom.yml" <<'YML'
name: Custom publish
on: push
jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew publish -x updateGitHubPages
YML

# =============================================================================
# (1) The script on a consumer that holds every form.
# =============================================================================
consumer="$work/consumer"
kotlin="$consumer/buildSrc/src/main/kotlin"
mkdir -p "$kotlin/io/acme/gradle" "$consumer/.github/workflows" "$consumer/server" \
         "$consumer/scratch"
cp "$fixtures/after-evaluate.before" "$kotlin/module.gradle.kts"
cp "$fixtures/top-level.before"      "$kotlin/docs.gradle.kts"
cp "$fixtures/Unknown.kt"            "$kotlin/io/acme/gradle/Unknown.kt"
cp "$fixtures/root.before"           "$consumer/build.gradle.kts"
crlf < "$fixtures/named-publish.before" > "$consumer/server/build.gradle.kts"
cp "$fixtures/custom.yml"            "$consumer/.github/workflows/custom.yml"
printf 'plugins { java }\n'        > "$kotlin/other.gradle.kts"
# Untracked, so not the consumer's: a build output, for example.
cp "$fixtures/root.before"           "$consumer/scratch/untracked.gradle.kts"
chmod 644 "$kotlin/module.gradle.kts"

git -C "$consumer" init -q
git -C "$consumer" config user.email test@example.com
git -C "$consumer" config user.name  test
# Keep the CRLF fixture as it is, whatever the host's `core.autocrlf`.
git -C "$consumer" config core.autocrlf false
git -C "$consumer" add buildSrc .github build.gradle.kts server
git -C "$consumer" commit -qm "A consumer on the old API"

out1="$work/run-1.out"; err1="$work/run-1.err"
if bash "$script" "$consumer" > "$out1" 2> "$err1"; then
  pass "the script ran to completion (exit 0)"
else
  f-ail "the script exited non-zero:"; cat "$out1" "$err1" >&2
fi

expect_file "$kotlin/module.gradle.kts" "$fixtures/after-evaluate.after" \
  "'configureGitHubPages()' with 'project.afterEvaluate' rewritten as 'time' was by hand"
expect_file "$kotlin/docs.gradle.kts" "$fixtures/top-level.after" \
  "a top-level call in another 'buildSrc' script rewritten, the old import dropped"
expect_file "$consumer/build.gradle.kts" "$fixtures/root.after" \
  "a tracked script outside 'buildSrc' rewritten"
crlf < "$fixtures/named-publish.after" > "$fixtures/named-publish.after.crlf"
expect_file "$consumer/server/build.gradle.kts" "$fixtures/named-publish.after.crlf" \
  "'tasks.named(\"publish\")' form rewritten, CRLF line endings kept"
expect_file "$kotlin/io/acme/gradle/Unknown.kt" "$fixtures/Unknown.kt" \
  "an unknown form left as it is"
expect_file "$consumer/.github/workflows/custom.yml" "$fixtures/custom.yml" \
  "a workflow left as it is"
expect_file "$consumer/scratch/untracked.gradle.kts" "$fixtures/root.before" \
  "an untracked script outside 'buildSrc' left as it is"

for moved in buildSrc/src/main/kotlin/module.gradle.kts buildSrc/src/main/kotlin/docs.gradle.kts \
             build.gradle.kts server/build.gradle.kts; do
  if grep -qxF "Moved \`$moved\` to \`publishApiDocs\`" "$out1"; then
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

# The script writes through `mktemp "<file>.XXXXXX"`.
leaked="$(find "$consumer" -name '*.kt*.??????' -not -path '*/.git/*' -print)"
if [ -z "$leaked" ]; then
  pass "no temp files left behind"
else
  f-ail "temp files leaked into the consumer: $leaked"
fi

# =============================================================================
# (2) A second run changes nothing.
# =============================================================================
git -C "$consumer" add -A buildSrc .github build.gradle.kts server
git -C "$consumer" commit -qm "Moved to the new API"

out2="$work/run-2.out"; err2="$work/run-2.err"
if bash "$script" "$consumer" > "$out2" 2> "$err2"; then
  pass "second run completed (exit 0)"
else
  f-ail "second run exited non-zero:"; cat "$out2" "$err2" >&2
fi
if [ -s "$out2" ]; then
  f-ail "second run rewrote files again: $(cat "$out2")"
else
  pass "second run rewrote nothing"
fi
if [ -z "$(git -C "$consumer" status --porcelain -- buildSrc .github build.gradle.kts server)" ]; then
  pass "second run left the tracked files as they were"
else
  f-ail "second run changed tracked files:"; git -C "$consumer" status --porcelain >&2
fi

# =============================================================================
# (3) The real `migrate` restores the consumer's script, rewrites, and stages it.
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
printf '// The seed of a new consumer.\n' > "$fake_config/buildSrc/src/main/kotlin/module.gradle.kts"
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
