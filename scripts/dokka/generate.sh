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

#Check that exactly three parameters were provided.
if [ "$#" -ne 3 ]; then
    echo "Usage: generateDokka.sh repositoryUrl='' releases='x,y,z' modules='x,y,z'"
    echo "Example: generateDokka.sh repositoryUrl='https://github.com/SpineEventEngine/core-java.git' releases='1.8.0,1.7.0' modules='core,client'"
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
    echo "Usage: generateDokka.sh repositoryUrl='' releases='x,y,z' modules='x,y,z'"
    echo "Example: generateDokka.sh repositoryUrl='https://github.com/SpineEventEngine/core-java.git' releases='1.8.0,1.7.0' modules='core,client'"
    exit 1
fi

mkdir "workspace" && cd "workspace"
git clone --branch="1.x-dev" "$repositoryUrl" "."

for release in $(echo "$releases" | tr "," "\n")
do
  echo "-----------------Started working on the $release release----------------"
  git checkout -f "tags/v$release"

  git submodule update --init --recursive

  jenv local 1.8

  #The version which will show up in the Dokka documentation
  echo "val versionToPublish: String by extra(\"$release\")" >> "../version.gradle.kts"
  echo "allprojects { version = extra[\"versionToPublish\"]!! }" >> "../build.gradle.kts"

  for module in $(echo "$modules" | tr "," "\n")
  do
      echo "-----------------Started working on the $module module for the $release release----------------"
      ./gradlew ":$module:classes"
      mkdir "../$module"
      cp -r "$module/" "../$module/"

      cd ..
      echo "include(\"$module\")" >> "settings.gradle.kts"
      echo "" > "$module/build.gradle.kts"
      ./gradlew ":$module:dokkaHtml"
      cd "workspace"
  done

  git checkout gh-pages
  git clean -fdx

  for module in $(echo "$modules" | tr "," "\n")
  do
    mkdir -p "dokka-reference/$module/v/$release"
    cp -r "../$module/build/docs/dokka/" "dokka-reference/$module/v/$release/"
    git add "dokka-reference/$module/v/$release"
    git commit -m "Generate Dokka documentation for \`$module\` as for version \`$release\`."
    rm -rf "../$module"
    echo "-----------------Finished working on the $module module for the $release release----------------"
  done

  git push

  rm "../version.gradle.kts"
  rm "../settings.gradle.kts"
  git restore "../build.gradle.kts"

  echo "-----------------Finished working on the $release release----------------"
done

cd ..
rm -rf "workspace"
rm -rf ".gradle"
