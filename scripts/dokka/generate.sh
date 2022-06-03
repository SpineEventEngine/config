#!/usr/bin/env bash

#
# Copyright 2022, TeamDev. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Redistribution and use in source and/or binary forms, with or without
# modification, must retain the above copyright notice and the following
# disclaimer.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
# LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#

USAGE_TEMPLATE="Usage: generate.sh repositoryUrl='' releases='x,y,z' modules='x,y,z'"
USAGE_EXAMPLE="Example: generate.sh repositoryUrl='https://github.com/SpineEventEngine/core-java.git' releases='v1.8.0,v1.7.0' modules='core,client'"

#Check that exactly three parameters were provided.
if [ "$#" -ne 3 ]; then
    echo "$USAGE_TEMPLATE"
    echo "$USAGE_EXAMPLE"
    exit 1
fi

#Declare variables matching those passed to the script.
for arg in "$@"
do
   key=$(echo "$arg" | cut -f1 -d=)

   key_length=${#key}
   value="${arg:key_length+1}"

   declare "$key"="$value"
done

#Check that all necessary for the script parameters were set.
if [ -z "$repositoryUrl" ] || [ -z "$releases" ] || [ -z "$modules" ]; then
    echo "$USAGE_TEMPLATE"
    echo "$USAGE_EXAMPLE"
    exit 1
fi

mkdir "workspace" && cd "workspace" || exit 2
git clone --branch="1.x-dev" "$repositoryUrl" "."

log() {
  echo "-----------------$1-----------------"
}

for release in $(echo "$releases" | tr "," "\n")
do
  log "Started working on the $release release"
  git checkout -f "tags/$release"
  git submodule update --init --recursive

  #Remove leading 'v' in a release name.
  release="${release:1}"
  jenv local 1.8

  #The version that will show up in Dokka-generated documentation.
  echo "val versionToPublish: String by extra(\"$release\")" >> "../version.gradle.kts"

  for module in $(echo "$modules" | tr "," "\n")
  do
      log "Started working on the $module module for the $release release"
      ./gradlew ":$module:classes"
      mkdir "../$module"
      cp -r "$module/" "../$module/"

      cd ..
      echo "include(\"$module\")" >> "settings.gradle.kts"

      #Configuration in module's build files is not needed for the `classes` task,
      #but if present can result in an error, so it is removed completely.
      echo "" > "$module/build.gradle"
      echo "" > "$module/build.gradle.kts"

      ./gradlew ":$module:dokkaHtml"

      cd "workspace" || exit 2
  done

  git checkout gh-pages
  git clean -fdx

  for module in $(echo "$modules" | tr "," "\n")
  do
    mkdir -p "dokka-reference/$module/v/$release"
    cp -r "../$module/build/docs/dokka/" "dokka-reference/$module/v/$release/"

    git add "dokka-reference/$module/v/$release"
    git commit -m "Generate Dokka documentation for \`$module\` of \`$release\` version"

    rm -rf "../$module"
    log "Finished working on the $module module for the $release release"
  done

  git push

  rm "../version.gradle.kts"
  rm "../settings.gradle.kts"

  log "Finished working on the $release release"
done

cd ..
rm -rf "workspace"
rm -rf ".gradle"
