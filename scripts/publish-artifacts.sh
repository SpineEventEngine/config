#!/bin/bash

# This script uses Travis environment variables to check if the artifacts should be published.
#
# If the build is successful and the branch is `master`, script triggers the publishing process.
#
# Tests are skipped during the publishing, as the script should be executed after their execution.
#
# If the publishing fails, the script returns an error code `1` that is treated by Travis as a
# failure and stops the build immediately.

echo " -- PUBLISHING: current branch is $TRAVIS_BRANCH."

if [ "$TRAVIS_BRANCH" == "master" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
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
