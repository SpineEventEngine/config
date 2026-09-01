#!/usr/bin/env bash

# Copyright 2026 CodeMatters, Lda.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.

PRIMARY_MARK="*"
USAGE_TEMPLATE="Usage: publish.sh repositoryUrl='' tags='x,${PRIMARY_MARK}y,z' paths='foo,bar/baz'"
USAGE_EXAMPLE="Example: publish.sh repositoryUrl='https://github.com/SpineEventEngine/base.git' tags='v1.7.0,${PRIMARY_MARK}v1.8.0' paths='base,tools/proto-js-plugin'"

# Checks that exactly three parameters were provided.
if [ "$#" -ne 3 ]; then
    echo "$USAGE_TEMPLATE"
    echo "$USAGE_EXAMPLE"
    exit 22 # Invalid argument.
fi

# Declares variables matching those passed to the script.
for arg in "$@"
do
   key=$(echo "$arg" | cut -f1 -d=)

   key_length=${#key}
   value="${arg:key_length+1}"

   declare "$key"="$value"
done

# Checks that all necessary for the script parameters were set.
if [ -z "$repositoryUrl" ] || [ -z "$tags" ] || [ -z "$paths" ]; then
    echo "$USAGE_TEMPLATE"
    echo "$USAGE_EXAMPLE"
    exit 22 # Invalid argument.
fi

mkdir "workspace" && cd "workspace" || exit 2 # Folder does not exist.
git clone "$repositoryUrl" "."

# Creates the `gh-pages` branch if it does not exist.
if ! [[ $(git branch --list --all origin/gh-pages) ]]; then
  git switch --orphan gh-pages
  git commit --allow-empty -m "Initial commit"
  git push -u origin gh-pages
fi

log() {
  echo "---$1---"
}

for tag in $(echo "$tags" | tr "," "\n")
do
  if [[ $tag == "$PRIMARY_MARK"* ]]; then
    # Removes the leading $PRIMARY_MARK.
    tag=${tag:1}
    is_primary=true
  else
    is_primary=false
  fi

  log "Started working on the \`$tag\` tag."
  git checkout -f "tags/$tag"
  git submodule update --init --recursive

  # Removes the leading 'v' to derive the version.
  version=${tag:1}

  jenv local 1.8

  # The version that will show up in Dokka-generated documentation.
  echo "extra.set(\"versionToPublish\", \"$version\")" >> "../version.gradle.kts"

  for path in $(echo "$paths" | tr "," "\n")
  do
      # Extracts the module name from a path. A path should use the "/" as a file separator.
      module="${path##*\/}"
      log "Started working on the \`$module\` module."

      ./gradlew ":$module:classes"
      mkdir "../$module"

      log "[$PWD] Copying '$path' to '../$module/'"
      cp -r "$path/" "../$module/"

      cd ..
      echo "include(\"$module\")" >> "settings.gradle.kts"

      # Configuration in module's build files is not needed for the `classes` task,
      # but if present can result in an error, so it is removed completely.
      echo "" > "$module/build.gradle"
      echo "" > "$module/build.gradle.kts"

      log "[$PWD] Executing ':$module:dokkaHtml'"
      ./gradlew ":$module:dokkaHtml"

      cd "workspace" || exit 2 # Folder does not exist.
  done

  git switch gh-pages
  git clean -fdx

  # For some reason, recent usage of this script failed since `.config` folder
  # was preventing from making a commit, as it was "untracked" change.
  # Therefore, this folder is pre-emptively removed here, just in case.
  # We don't need it in `gh-pages` branch anyway.
  log "[$PWD] Removing ./config folder."
  rm -rf "./config"

  for path in $(echo "$paths" | tr "," "\n")
  do
    # Extracts the module name from a path. A path should use the "/" as a file separator.
    module="${path##*\/}"

    log "[$PWD] Creating directory 'dokka-reference/$module/v/$version'"
    mkdir -p "dokka-reference/$module/v/$version"

    # Previous versions of Dokka produced their output in `dokka` subfolder.
    # Most recent versions do that differently, generating the documentation
    # into `dokkaJava` folder when used in JVM mode.
    # We treat the observed behaviour as "new normal".
    log "[$PWD] Copying recursively: '../$module/build/docs/dokkaJava/' -> 'dokka-reference/$module/v/$version/'"
    cp -r "../$module/build/docs/dokkaJava/" "dokka-reference/$module/v/$version/"

    commit_message="Publish Dokka documentation for \`$module\` of \`$version\` "
    if [ $is_primary = true ]; then
      log "[$PWD] PRIMARY Copying recursively: '../$module/build/docs/dokkaJava/' -> 'dokka-reference/$module/'"
      cp -r "../$module/build/docs/dokkaJava/" "dokka-reference/$module/"
      commit_message+="as primary"
    else
      commit_message+="as secondary"
    fi

    log "[$PWD] Executing 'git add \"dokka-reference/$module/*\"', and committing."
    git add "dokka-reference/$module/*"
    git commit -m "$commit_message"

    rm -rf "../$module"
    log "Finished working on the \`$module\` module."
  done

  git push

  rm "../version.gradle.kts"
  rm "../settings.gradle.kts"

  log "Finished working on the \`$tag\` tag."
done

cd ..
rm -rf "workspace"
rm -rf ".gradle"
