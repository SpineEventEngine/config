#!/bin/bash

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

# This script is a part of a GitHub Actions workflow.
#
# Its purpose is to prevent PRs from leaving dependency report files
# from being untouched. In case any of these files are not updated, it exits with an error code 1.
# Otherwise, exits with a success code 0.
#
# In its implementation, the script relies into the environment variables set by GitHub Actions.
# See https://docs.github.com/en/actions/reference/environment-variables.


# Detects if the file with the passed name has been updated in this changeset.
#
# Exits with the code 1, if such a file has NOT been modified.
# Does nothing, if any modification was found.
function ensureUpdated() {
	modificationCount=$(git diff --name-only remotes/origin/$GITHUB_BASE_REF...remotes/origin/$GITHUB_HEAD_REF | grep -F -x "$1" | wc -l)
	if [ "$modificationCount" -eq "0" ];
	then
	   echo "ERROR: '$1' file has not been updated in this PR. Please re-check the changeset.";
	   exit 1;
	else
		echo "Detected the modifications in '$1'."
	fi
}

echo "Starting to check if all required files were updated within this PR..."
echo "Comparing \"remotes/origin/$GITHUB_HEAD_REF\" branch to \"remotes/origin/$GITHUB_BASE_REF\" contents."

ensureUpdated "docs/dependencies/pom.xml"
ensureUpdated "docs/dependencies/dependencies.md"

echo "All good."
exit 0;
