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

#
# How to use this script:
#  1. Commit the changes you were working on to your branch.
#  2. Update the `config` submodule to fetch the latest version of the script.
#  3. Run the script. It will create a new branch from `master` automatically.
#  4. The script lets you edit the `version.gradle.kts` file if it exists and commits it.
#  5. In GitHub, press the `Create & pull request` button.
#

echo "Checking out 'master'..."

git checkout master
git pull

echo "Switching to branch 'update-copyright-notice'..."
git checkout -b update-copyright-notice

echo "Updating config..."

if [ -f "./pull" ]; then
    ./pull
elif [ -f "./config/pull" ]; then
    ./config/pull
fi

git commit -am "Update config"

echo "Switching locale-related env variables..."

formal_lc_ctype=$LC_CTYPE
formal_lang=$LANG

export LC_CTYPE=C
export LANG=C

echo "Running search & replace for the copyright notice..."

# Change this line sometime in January.
(( new_year = 2022 ))
(( old_year = new_year - 1 ))
grep  "Copyright $old_year, TeamDev. All rights reserved." -rl --exclude='**/build/**' . | xargs sed -i "" "s/Copyright $old_year, TeamDev. All rights reserved./Copyright $new_year, TeamDev. All rights reserved./g"

echo "Restoring env variables..."

export LC_CTYPE=$formal_lc_ctype
export LANG=$formal_lang

echo "Committing changes copyright notice..."

git commit -am "Update copyright notices"

version_file="./version.gradle.kts"
if [ -f "$version_file" ]; then
  nano "$version_file"

  echo "Committing version file changes..."
  git commit -am "Update version"
fi

git push origin update-copyright-notice

echo "Done."
