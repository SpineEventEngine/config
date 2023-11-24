#!/bin/bash
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

# This script is a part of a GitHub Actions workflow.
#
# Its purpose is to prevent PRs from leaving `pom.xml` and license report files
# from being untouched. In case any of these files are not updated, it exits with an error code 1.
# Otherwise, exits with a success code 0.
#
# According to https://github.com/SpineEventEngine/config/issues/498,
# `license-report.md` is now renamed to `dependencies.md`. However, not every Spine repository
# uses this convention yet, so this script ensures that either of files is modified.
#
# In its implementation, the script relies into the environment variables set by GitHub Actions.
# See https://docs.github.com/en/actions/reference/environment-variables.


# Detects if the file with the passed name has been updated in this changeset.
#
# Exits with the code 1, if such a file has NOT been modified.
# Does nothing, if any modification was found.
function ensureUpdated() {
	modificationCount=$(git diff --name-only remotes/origin/$GITHUB_BASE_REF...remotes/origin/$GITHUB_HEAD_REF | grep $1 | wc -l)
	if [ "$modificationCount" -eq "0" ];
	then
	   echo "ERROR: '$1' file has not been updated in this PR. Please re-check the changeset.";
	   exit 1;
	else
		echo "Detected the modifications in '$1'."
	fi
}

# Detects if any of TWO files with the passed names has been updated in this changeset.
#
# Exits with the code 1, if NONE of the files have been modified.
# Does nothing, if a modification in any of the files was found.
function ensureEitherUpdated() {
	firstModCount=$(git diff --name-only remotes/origin/$GITHUB_BASE_REF...remotes/origin/$GITHUB_HEAD_REF | grep $1 | wc -l)
	if [ "$firstModCount" -eq "0" ];
	then
	   echo "'$1' file has not been updated in this PR. Checking '$2'...";
	   secondModCount=$(git diff --name-only remotes/origin/$GITHUB_BASE_REF...remotes/origin/$GITHUB_HEAD_REF | grep $2 | wc -l)
	   if [ "$secondModCount" -eq "0" ];
	   then
	       echo "ERROR: Neither '$1' nor '$2' files have been updated in this PR. Please re-check the changeset.";
	       exit 1;
	   else
	       echo "Detected the modifications in '$2'."
	   fi
	else
		echo "Detected the modifications in '$1'."
	fi
}

echo "Starting to check if all required files were updated within this PR..."
echo "Comparing \"remotes/origin/$GITHUB_HEAD_REF\" branch to \"remotes/origin/$GITHUB_BASE_REF\" contents."

ensureUpdated "pom.xml"
ensureEitherUpdated "license-report.md" "dependencies.md"

echo "All good."
exit 0;

