/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.gradle.publish

/**
 * Resolves the string values of `extra` properties declared in a `version.gradle.kts` file.
 *
 * Handles the shapes used across Spine repositories (see the `bump-version` skill):
 *
 *  1. a literal: `val versionToPublish: String by extra("2.0.0-SNAPSHOT.182")`;
 *  2. an alias to another `extra`: `val versionToPublish by extra(compilerVersion)` paired
 *     with `val compilerVersion: String by extra("2.0.0-SNAPSHOT.043")`;
 *  3. an alias to a plain `val`: `val versionToPublish by extra(base)` paired with
 *     `val base = "2.0.0-SNAPSHOT.043"`.
 *
 * The publishing-version property is identified by [keyForValue] using the already-resolved
 * project version as an oracle, so the specific property name (`versionToPublish`,
 * `validationVersion`, `compilerVersion`, …) does not need to be hard-coded.
 *
 * Only a single alias hop is resolved; an alias to another alias falls through to `null`,
 * which the caller treats as "publishing version not identified" and skips the check.
 */
internal object VersionGradleFile {

    private val literalExtra =
        Regex("""val\s+(\w+)\s*(?::\s*String)?\s+by\s+extra\(\s*"([^"]+)"\s*\)""")
    private val aliasExtra =
        Regex("""val\s+(\w+)\s*(?::\s*String)?\s+by\s+extra\(\s*([A-Za-z_]\w*)\s*\)""")
    private val plainAssignment =
        Regex("""val\s+(\w+)\s*(?::\s*String)?\s*=\s*"([^"]+)"""")

    /**
     * Resolves every named `extra` (and the plain `val`s an `extra` may alias) to its
     * string value.
     */
    private fun parse(content: String): Map<String, String> {
        val literals = literalExtra.findAll(content)
            .associate { it.groupValues[1] to it.groupValues[2] }
        val plains = plainAssignment.findAll(content)
            .associate { it.groupValues[1] to it.groupValues[2] }
        val resolved = literals.toMutableMap()
        aliasExtra.findAll(content).forEach { match ->
            val name = match.groupValues[1]
            val source = match.groupValues[2]
            if (name !in resolved) {
                (literals[source] ?: plains[source])?.let { resolved[name] = it }
            }
        }
        return resolved
    }

    /**
     * The name of the property whose resolved value equals [value], or `null` if none does.
     */
    fun keyForValue(content: String, value: String): String? =
        parse(content).entries.firstOrNull { it.value == value }?.key

    /**
     * The resolved value of the property named [key], or `null` if it is absent.
     */
    fun valueForKey(content: String, key: String): String? = parse(content)[key]
}
