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

# This script triggers the build of the https://github.com/SpineEventEngine/publishing.
#
# The `publishing` repository advances the versions of other Spine libraries to the new version
# of the repository invoking this script.
#
# Execute this script after a successful `master` branch build, e.g.
# ```
# after_success:
# if [[ $TRAVIS_BRANCH == master ]] && [[ $TRAVIS_PULL_REQUEST == false ]]; then
#     chmod +x ./scripts/trigger-publishing.sh
#     sh ./scripts/trigger-publishing.sh
# fi
# ```
#
# This script relies on the "TRAVIS_TOKEN" env variable being set.
#
# More: https://docs.travis-ci.com/user/triggering-builds

body="{
       \"request\": {
         \"branch\": \"master\"
        }
      }"

curl -s -X POST \
 -H "Content-Type: application/json"\
 -H "Accept: application/json"\
 -H "Travis-API-Version: 3"\
 -H "Authorization: token $TRAVIS_TOKEN"\
 -d "$body"\
 "https://api.travis-ci.com/repo/SpineEventEngine%2Fpublishing/requests"
