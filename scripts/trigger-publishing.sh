#!/usr/bin/env bash

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
 "https://api.travis-ci.org/repo/SpineEventEngine%2Fpublishing/requests"
