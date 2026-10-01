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

# Regression checks for which repositories `migrate` hands the Gradle Wrapper
# validation workflow (`gradle-wrapper-validation.yml`) to:
#
#   * a Hugo-only repository with a Gradle Wrapper RECEIVES it — none of its
#     workflows uses `setup-gradle`, so nothing else validates the wrapper JAR —
#     and a stale copy is refreshed, never deleted;
#   * a JVM repository, with or without a Hugo site, does NOT: `setup-gradle`
#     validates the wrapper there, so `migrate` stages the removal of any copy an
#     earlier pull left behind, and `copy_workflows` must not ship config's copy;
#   * a Hugo-only repository WITHOUT a wrapper does not receive it either — the
#     validation action fails when it finds no wrapper at all;
#   * a repository that replaced it with a repo-specific variant
#     (`config:replaces`) is left alone;
#   * a second pull is a quiet no-op.
#
# The REAL `migrate` is run against a minimal fake `config/` fixture, exactly as
# `scripts/test-migrate-ide-files.sh` does; read the header there for why
# `adopt-shared-agents` is stubbed and why `cp: No such file or directory` noise
# in the captured logs is expected.

set -eo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
config_dir="$(cd "$script_dir/.." && pwd)"

fail=0
pass() { echo "PASS: $1"; }
f-ail() { echo "FAIL: $1" >&2; fail=1; }

wf=gradle-wrapper-validation.yml
wf_path=".github/workflows/$wf"

for required in migrate scripts/update-gitignore.sh .gitignore ".github-workflows/$wf"; do
  [ -f "$config_dir/$required" ] \
    || { echo "FAIL: cannot find '$required' under $config_dir" >&2; exit 1; }
done

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Lays out a consumer at $1 with a fake `config/` fixture inside it, and commits
# it. Each further argument adds a trait:
#
#   hugo     — a Hugo site under `docs/`;
#   jvm      — the `buildSrc` marker `migrate` detects a JVM repository by;
#   wrapper  — a Gradle Wrapper JAR;
#   stale    — the workflow as `spine.io` restored it by hand: an older copy,
#              triggered on a `main` branch no Spine repository has;
#   override — a repo-specific variant that `config:replaces` the workflow.
make_consumer() {
  local consumer="$1" fake_config="$1/config" trait
  shift
  mkdir -p "$consumer/.github/workflows" \
           "$fake_config/scripts" "$fake_config/.github-workflows" \
           "$fake_config/.github/workflows"
  printf 'consumer\n' > "$consumer/README.md"
  for trait in "$@"; do
    case "$trait" in
      hugo)
        mkdir -p "$consumer/docs"
        printf 'title = "Docs"\n' > "$consumer/docs/hugo.toml" ;;
      jvm)
        mkdir -p "$consumer/buildSrc/src/main/kotlin/io/spine/dependency" ;;
      wrapper)
        mkdir -p "$consumer/gradle/wrapper"
        printf 'not really a JAR\n' > "$consumer/gradle/wrapper/gradle-wrapper.jar" ;;
      stale)
        printf 'name: Gradle Wrapper validation\non:\n  push:\n    branches:\n      - main\n' \
          > "$consumer/$wf_path" ;;
      override)
        printf '# config:replaces %s\nname: Custom wrapper validation\non: push\n' "$wf" \
          > "$consumer/.github/workflows/wrapper-validation-custom.yml" ;;
      *)
        echo "FAIL: unknown consumer trait '$trait'" >&2; exit 1 ;;
    esac
  done

  git -C "$consumer" init -q
  git -C "$consumer" config user.email test@example.com
  git -C "$consumer" config user.name  test
  git -C "$consumer" add -A
  git -C "$consumer" commit -qm "A consumer: $*"

  cp "$config_dir/migrate"                      "$fake_config/migrate"
  cp "$config_dir/scripts/update-gitignore.sh"  "$fake_config/scripts/"
  cp "$config_dir/.gitignore"                   "$fake_config/.gitignore"
  cp "$config_dir/.github-workflows/$wf"        "$fake_config/.github-workflows/"
  # Another workflow distributed to JVM repositories, to show the skip is narrow.
  printf 'name: Build under Ubuntu\non: push\n' > "$fake_config/.github-workflows/build-on-ubuntu.yml"
  printf 'name: Secret scan\non: push\n'        > "$fake_config/.github/workflows/secret-scan.yml"
  printf '#!/usr/bin/env bash\nexit 0\n'        > "$fake_config/adopt-shared-agents"
  chmod +x "$fake_config/adopt-shared-agents"
}

# Runs the real `migrate` exactly as `pull` does (CWD = `config`), capturing
# the log at $2. Reports a non-zero exit as a failure.
run_migrate() {
  if ( cd "$1/config" && bash migrate ) > "$2" 2>&1; then
    pass "$(basename "$1"): migrate ran to completion (exit 0)"
  else
    f-ail "$(basename "$1"): migrate exited non-zero — see the log below"; cat "$2" >&2
  fi
}

# Whether the consumer at $1 carries config's exact copy of the workflow.
has_configs_copy() { cmp -s "$config_dir/.github-workflows/$wf" "$1/$wf_path"; }

# Whether the consumer at $1 tracks the workflow in its index.
is_tracked() { git -C "$1" ls-files --error-unmatch "$wf_path" >/dev/null 2>&1; }

# Whether the consumer at $1 has the deletion of the workflow staged.
is_deletion_staged() {
  [ "$(git -C "$1" diff --cached --name-status -- "$wf_path")" = "D	$wf_path" ]
}

# =============================================================================
# (1) Hugo-only with a wrapper: the workflow is distributed.
# =============================================================================
hugo="$work/hugo"
make_consumer "$hugo" hugo wrapper
run_migrate "$hugo" "$work/hugo-1.log"

if has_configs_copy "$hugo"; then
  pass "hugo: config's '$wf' distributed"
else
  f-ail "hugo: config's '$wf' was not distributed"
fi

if [ -e "$hugo/.github/workflows/build-on-ubuntu.yml" ]; then
  f-ail "hugo: JVM workflow 'build-on-ubuntu.yml' reached a Hugo-only repository"
else
  pass "hugo: JVM workflows kept out"
fi

# =============================================================================
# (2) Hugo-only with a stale copy: refreshed, still tracked, never removed.
# =============================================================================
hugo_stale="$work/hugo-stale"
make_consumer "$hugo_stale" hugo wrapper stale
run_migrate "$hugo_stale" "$work/hugo-stale.log"

if has_configs_copy "$hugo_stale"; then
  pass "hugo-stale: stale '$wf' replaced by config's copy"
else
  f-ail "hugo-stale: '$wf' is not config's copy"
fi

if is_tracked "$hugo_stale"; then
  pass "hugo-stale: '$wf' still tracked"
else
  f-ail "hugo-stale: '$wf' was untracked — migrate removed it from a Hugo-only repository"
fi

if grep -q "Removing workflow" "$work/hugo-stale.log"; then
  f-ail "hugo-stale: migrate announced the removal of '$wf'"
else
  pass "hugo-stale: no removal announced"
fi

# =============================================================================
# (3) JVM: config's copy is not shipped, and an earlier pull's copy is removed.
# =============================================================================
jvm="$work/jvm"
make_consumer "$jvm" jvm
run_migrate "$jvm" "$work/jvm.log"

# Checking the log too: had `copy_workflows` shipped the file, the removal block
# would delete it again, and the absence alone would prove nothing.
if [ ! -e "$jvm/$wf_path" ] && ! grep -q "Removing workflow" "$work/jvm.log"; then
  pass "jvm: '$wf' not distributed"
else
  f-ail "jvm: '$wf' was distributed by \`copy_workflows\`"
fi

if [ -f "$jvm/.github/workflows/build-on-ubuntu.yml" ]; then
  pass "jvm: other workflows ('build-on-ubuntu.yml') still distributed"
else
  f-ail "jvm: 'build-on-ubuntu.yml' was not distributed — the skip is too wide"
fi

jvm_stale="$work/jvm-stale"
make_consumer "$jvm_stale" jvm wrapper stale
run_migrate "$jvm_stale" "$work/jvm-stale.log"

if is_deletion_staged "$jvm_stale" && [ ! -e "$jvm_stale/$wf_path" ]; then
  pass "jvm-stale: removal of '$wf' staged"
else
  f-ail "jvm-stale: removal of '$wf' not staged"
fi

# =============================================================================
# (4) JVM with a Hugo site: `setup-gradle` still covers the wrapper — removed.
# =============================================================================
jvm_hugo="$work/jvm-hugo"
make_consumer "$jvm_hugo" jvm hugo wrapper stale
run_migrate "$jvm_hugo" "$work/jvm-hugo.log"

if is_deletion_staged "$jvm_hugo" && [ ! -e "$jvm_hugo/$wf_path" ]; then
  pass "jvm-hugo: removal of '$wf' staged"
else
  f-ail "jvm-hugo: removal of '$wf' not staged"
fi

# =============================================================================
# (5) Hugo-only with a repo-specific variant: left alone.
# =============================================================================
hugo_override="$work/hugo-override"
make_consumer "$hugo_override" hugo wrapper override
custom="$hugo_override/.github/workflows/wrapper-validation-custom.yml"
custom_before="$(cat "$custom")"
run_migrate "$hugo_override" "$work/hugo-override.log"

if [ -e "$hugo_override/$wf_path" ]; then
  f-ail "hugo-override: generic '$wf' distributed despite the 'config:replaces' directive"
else
  pass "hugo-override: generic '$wf' withheld"
fi

if [ "$(cat "$custom")" = "$custom_before" ]; then
  pass "hugo-override: repo-specific variant untouched"
else
  f-ail "hugo-override: repo-specific variant was modified"
fi

# =============================================================================
# (6) Hugo-only without a wrapper: nothing to validate — removed.
# =============================================================================
hugo_bare="$work/hugo-bare"
make_consumer "$hugo_bare" hugo stale
run_migrate "$hugo_bare" "$work/hugo-bare.log"

if is_deletion_staged "$hugo_bare" && [ ! -e "$hugo_bare/$wf_path" ]; then
  pass "hugo-bare: '$wf' not distributed, and the stale copy's removal staged"
else
  f-ail "hugo-bare: '$wf' kept in a repository without a wrapper"
fi

# =============================================================================
# (7) A second pull into (1) is a quiet no-op.
# =============================================================================
git -C "$hugo" add -A
git -C "$hugo" commit -qm "Pull: distribute the wrapper validation"
run_migrate "$hugo" "$work/hugo-2.log"

if [ -z "$(git -C "$hugo" status --porcelain)" ]; then
  pass "hugo: second run left the working tree clean"
else
  f-ail "hugo: second run dirtied the working tree:"
  git -C "$hugo" status --porcelain >&2
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "OK: all migrate wrapper-validation regression checks passed."
else
  echo "FAILED: one or more migrate wrapper-validation regression checks failed." >&2
  exit 1
fi
