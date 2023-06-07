#!/usr/bin/env bash

#
# Copyright 2023, TeamDev. All rights reserved.
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

# This script uses Travis environment variables to check if the artifacts should be published.
#
# If the build is successful and the branch is `master`, script triggers the publishing process.
#
# Tests are skipped during the publishing, as the script should be executed after their execution.
#
# If the publishing fails, the script returns an error code `1` that is treated by Travis as a
# failure and stops the build immediately.

echo " -- PUBLISHING: current branch is $TRAVIS_BRANCH."

if { [ "$TRAVIS_BRANCH" == "master" ] || [ "$TRAVIS_BRANCH" == "2.x-jdk8-master" ]; } && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
    if [ "$TRAVIS_TEST_RESULT" == 0 ]; then
        echo " ------ Publishing the artifacts to the repository..."
        if ./gradlew publish -x test --stacktrace -Dorg.gradle.jvmargs="-Xmx4g -XX:+HeapDumpOnOutOfMemoryError"; then
          echo " ------ Artifacts published."
        else
          echo " ------ Artifacts publishing FAILED."
          exit 1
        fi
    else
        echo " ------ The build is broken. Publishing will not be performed."
    fi
else
    echo " ------ Publishing is DISABLED for the current branch."
fi

echo " -- PUBLISHING: completed."
