#!/usr/bin/env bash

# Copyright 2026 CodeMatters, Lda.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.

# Decrypts a file via `gpg`.
#
# Params:
#   1. The secret passphrase used when encrypting the file.
#   2. The encrypted file.
#   3. The path where the decrypted file should go.
#

if [ "$#" -ne 3 ]; then
    echo "Usage: decrypt.sh <passphrase> <encrypted file> <target file>"
    exit 1
fi

gpg --quiet --batch --yes --decrypt --passphrase="$1" --output "$3" "$2"
