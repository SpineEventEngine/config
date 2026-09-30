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

# Regression checks for how `migrate` hands the reusable publishing workflow to
# a JVM consumer:
#
#   * the config-only workflows (`publishing.yml`, `detekt-code-analysis.yml`)
#     are NOT copied, while the other distributed workflows still are;
#   * the copied `publish.yml` is pinned to the exact commit of `config` the
#     consumer is about to record as its submodule, by replacing the
#     `@CONFIG_COMMIT` placeholder — an immutable reference, never a branch;
#   * a second pull re-pins to the commit `config` has moved to;
#   * a consumer that replaced `publish.yml` with a repo-specific workflow
#     (`config:replaces`) is left alone;
#   * when no `config` commit can be resolved the pull FAILS, rather than
#     falling back to a mutable ref: the caller passes every repository secret
#     to the workflow it names, so the reference must not be forgeable.
#
# The REAL `migrate` is run against a minimal fake `config/` fixture, exactly as
# `scripts/test-migrate-ide-files.sh` does; read the header there for why
# `adopt-shared-agents` is stubbed and why `cp: No such file or directory` noise
# in the captured logs is expected. One difference matters here: the fixture
# `config/` is a git repository OF ITS OWN inside the consumer's, the shape a
# submodule has, so `git rev-parse HEAD` inside it must yield config's commit
# and not the consumer's.

set -eo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
config_dir="$(cd "$script_dir/.." && pwd)"

fail=0
pass() { echo "PASS: $1"; }
f-ail() { echo "FAIL: $1" >&2; fail=1; }

for required in migrate scripts/update-gitignore.sh .gitignore \
                .github-workflows/publish.yml .github/workflows/publishing.yml; do
  [ -f "$config_dir/$required" ] \
    || { echo "FAIL: cannot find '$required' under $config_dir" >&2; exit 1; }
done

# File mode, portable across GNU and BSD `stat`. GNU first: BSD `stat -c` fails
# outright, whereas GNU `stat -f` SUCCEEDS with file-system information, so the
# other order would never fall through on Linux. The result is validated so a
# `stat` that answers with something other than a mode fails the run loudly
# instead of failing one assertion with a confusing diff.
mode_of() {
  local mode
  mode="$(stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1")"
  [[ "$mode" =~ ^[0-7]{3,4}$ ]] \
    || { echo "FAIL: cannot read the mode of '$1' — stat answered '$mode'" >&2; exit 1; }
  echo "$mode"
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Lays out a JVM consumer at $1 with a fake `config/` fixture inside it. The
# fixture becomes a git repository of its own when $2 is `own-repo`; with
# `plain-dir` it stays a bare directory inside the consumer's repository.
make_consumer() {
  local consumer="$1" shape="$2" fake_config="$1/config"
  mkdir -p "$consumer/.github/workflows" \
           "$consumer/buildSrc/src/main/kotlin/io/spine/dependency" \
           "$fake_config/scripts" "$fake_config/.github-workflows" \
           "$fake_config/.github/workflows"

  git -C "$consumer" init -q
  git -C "$consumer" config user.email test@example.com
  git -C "$consumer" config user.name  test
  printf 'consumer\n' > "$consumer/README.md"
  git -C "$consumer" add README.md buildSrc 2>/dev/null || true
  git -C "$consumer" add README.md
  git -C "$consumer" commit -qm "A JVM consumer"

  cp "$config_dir/migrate"                          "$fake_config/migrate"
  cp "$config_dir/scripts/update-gitignore.sh"      "$fake_config/scripts/"
  cp "$config_dir/.gitignore"                       "$fake_config/.gitignore"
  cp "$config_dir/.github-workflows/publish.yml"    "$fake_config/.github-workflows/"
  cp "$config_dir/.github/workflows/publishing.yml" "$fake_config/.github/workflows/"
  printf 'name: detekt\non: push\n'      > "$fake_config/.github/workflows/detekt-code-analysis.yml"
  printf 'name: Secret scan\non: push\n' > "$fake_config/.github/workflows/secret-scan.yml"
  printf '#!/usr/bin/env bash\nexit 0\n' > "$fake_config/adopt-shared-agents"
  chmod +x "$fake_config/adopt-shared-agents"

  if [ "$shape" = own-repo ]; then
    git -C "$fake_config" init -q
    git -C "$fake_config" config user.email test@example.com
    git -C "$fake_config" config user.name  test
    git -C "$fake_config" add -A
    git -C "$fake_config" commit -qm "config at some commit"
  fi
}

# Runs the real `migrate` exactly as `pull` does (CWD = `config`), capturing
# the log at $2. Returns migrate's exit status.
run_migrate() {
  ( cd "$1/config" && bash migrate ) > "$2" 2>&1
}

# The line the pin rewrites, in the consumer's copy under $1.
uses_line() { grep -E '^[[:space:]]*uses:' "$1/.github/workflows/publish.yml"; }

# =============================================================================
# (1) A regular pull: skip list and pin.
# =============================================================================
consumer="$work/consumer"
make_consumer "$consumer" own-repo
config_sha="$(git -C "$consumer/config" rev-parse HEAD)"
consumer_sha="$(git -C "$consumer" rev-parse HEAD)"

log1="$work/migrate-1.log"
if run_migrate "$consumer" "$log1"; then
  pass "migrate ran to completion (exit 0)"
else
  f-ail "migrate exited non-zero — see the log below"; cat "$log1" >&2
fi

if [ -f "$consumer/.github/workflows/publish.yml" ]; then
  pass "'publish.yml' distributed"
else
  f-ail "'publish.yml' was not distributed"
fi

for f in publishing.yml detekt-code-analysis.yml; do
  if [ -e "$consumer/.github/workflows/$f" ]; then
    f-ail "config-only '$f' was copied into the consumer"
  else
    pass "config-only '$f' kept out of the consumer"
  fi
done

if [ -f "$consumer/.github/workflows/secret-scan.yml" ]; then
  pass "other live workflows ('secret-scan.yml') still distributed"
else
  f-ail "'secret-scan.yml' was not distributed — the skip list is too wide"
fi

if [ "$(uses_line "$consumer")" = \
     "    uses: SpineEventEngine/config/.github/workflows/publishing.yml@$config_sha" ]; then
  pass "'publish.yml' pinned to config's commit ${config_sha:0:8}"
else
  f-ail "'publish.yml' not pinned to config's commit; uses line: $(uses_line "$consumer")"
fi

if grep -q "@$consumer_sha" "$consumer/.github/workflows/publish.yml"; then
  f-ail "'publish.yml' was pinned to the CONSUMER's commit — git addressed the wrong repository"
else
  pass "pin is not the consumer's commit"
fi

if grep -q 'CONFIG_COMMIT' <<< "$(uses_line "$consumer")"; then
  f-ail "the '@CONFIG_COMMIT' placeholder survived in the uses line"
else
  pass "placeholder replaced"
fi

if grep -q "@master" "$consumer/.github/workflows/publish.yml"; then
  f-ail "'publish.yml' references a branch ('@master') — the pin must be a commit"
else
  pass "no branch reference in 'publish.yml'"
fi

if [ "$(mode_of "$consumer/.github/workflows/publish.yml")" = 644 ]; then
  pass "'publish.yml' kept mode 644"
else
  f-ail "'publish.yml' has mode $(mode_of "$consumer/.github/workflows/publish.yml"), expected 644"
fi

leaked="$(find "$consumer/.github/workflows" -name 'publish.yml.??????' -print)"
if [ -z "$leaked" ]; then
  pass "no 'publish.yml.XXXXXX' temp files left behind"
else
  f-ail "temp files leaked into the consumer: $leaked"
fi

# =============================================================================
# (2) A later pull follows config to its new commit.
# =============================================================================
printf 'changed\n' >> "$consumer/config/README.md"
git -C "$consumer/config" add -A
git -C "$consumer/config" commit -qm "config moves on"
config_sha2="$(git -C "$consumer/config" rev-parse HEAD)"

log2="$work/migrate-2.log"
if run_migrate "$consumer" "$log2"; then
  pass "second migrate run completed (exit 0)"
else
  f-ail "second migrate run exited non-zero — see the log below"; cat "$log2" >&2
fi

if [ "$(uses_line "$consumer")" = \
     "    uses: SpineEventEngine/config/.github/workflows/publishing.yml@$config_sha2" ]; then
  pass "second run re-pinned to config's new commit ${config_sha2:0:8}"
else
  f-ail "second run did not follow config; uses line: $(uses_line "$consumer")"
fi

if [ "$(uses_line "$consumer" | wc -l | tr -d ' ')" = 1 ]; then
  pass "exactly one 'uses:' line after two runs"
else
  f-ail "'uses:' line duplicated or lost after two runs"
fi

# =============================================================================
# (3) A repo-specific replacement is left alone.
# =============================================================================
overridden="$work/overridden"
make_consumer "$overridden" own-repo
printf '# config:replaces publish.yml\nname: Custom publish\non: push\n' \
  > "$overridden/.github/workflows/publish-custom.yml"
custom_before="$(cat "$overridden/.github/workflows/publish-custom.yml")"

log3="$work/migrate-3.log"
if run_migrate "$overridden" "$log3"; then
  pass "migrate ran to completion for the overriding consumer (exit 0)"
else
  f-ail "migrate exited non-zero for the overriding consumer — see the log below"; cat "$log3" >&2
fi

if [ -e "$overridden/.github/workflows/publish.yml" ]; then
  f-ail "generic 'publish.yml' was distributed despite the 'config:replaces' directive"
else
  pass "generic 'publish.yml' withheld from the overriding consumer"
fi

if [ "$(cat "$overridden/.github/workflows/publish-custom.yml")" = "$custom_before" ]; then
  pass "repo-specific workflow untouched"
else
  f-ail "repo-specific workflow was modified"
fi

# =============================================================================
# (4) No resolvable config commit: the pull fails closed.
# =============================================================================
# `config/` as a plain directory inside the consumer's repository is the sneaky
# case: `git` still answers, but with the consumer's HEAD.
plain="$work/plain"
make_consumer "$plain" plain-dir

log4="$work/migrate-4.log"
if run_migrate "$plain" "$log4"; then
  f-ail "migrate succeeded although 'config' is not a repository of its own"
else
  pass "migrate failed when 'config' is not a repository of its own"
fi

if grep -q "cannot resolve the 'config' commit" "$log4"; then
  pass "failure names the unresolved config commit"
else
  f-ail "failure message missing — log below"; cat "$log4" >&2
fi

if grep -q "Pinned 'publish.yml'" "$log4"; then
  f-ail "migrate claimed to pin 'publish.yml' on the failing path"
else
  pass "no pin claimed on the failing path"
fi

plain_sha="$(git -C "$plain" rev-parse HEAD)"
if [ -f "$plain/.github/workflows/publish.yml" ] \
   && grep -qE "@master|@$plain_sha" "$plain/.github/workflows/publish.yml"; then
  f-ail "'publish.yml' was pinned to a branch or to the consumer's commit" \
        "on the failing path"
else
  pass "'publish.yml' carries no forged pin on the failing path"
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "OK: all migrate publishing-pin regression checks passed."
else
  echo "FAILED: one or more migrate publishing-pin regression checks failed." >&2
  exit 1
fi
