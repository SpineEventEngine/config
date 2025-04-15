/*
 * Copyright 2024, TeamDev. All rights reserved.
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

import java.io.File

private data class ClassName(val qualifiedName: String) {
    val simpleName: String = qualifiedName.substringAfterLast(".")
}

/**
 * Creates a replacement API migration instruction
 */
private fun MutableMap<ClassName, ClassName>.migrate(
    oldClass: String,
    newClass: String
) = put(ClassName(oldClass), ClassName(newClass))

/**
 * Annotations.
 */
private val annotations = buildMap {
    migrate(
        "javax.annotation.ParametersAreNonnullByDefault",
        "org.jspecify.annotations.NullMarked"
    )
    migrate(
        "import javax.annotation.Nullable",
        "org.jspecify.annotations.Nullable"
    )
    migrate(
        "import org.checkerframework.checker.nullness.qual.Nullable",
        "org.jspecify.annotations.Nullable"
    )
    migrate(
        "import org.checkerframework.checker.nullness.qual.NonNull",
        "org.jspecify.annotations.NotNull"
    )
}

/**
 * Directories to be excluded from the traversal.
 */
private val excludedTopLevelDirs = setOf(
    ".git",
    ".github",
    ".github-workflows",
    ".gradle",
    ".idea",
    "build",
    "gradle",
    "quality",
    "BuildSpeed",
    "config"
)

private val excludedPaths = setOf(
    "buildSrc/.gradle",
    "buildSrc/build",
    "scripts/publish-documentation/buildSrc",
)

/**
 * Excludes from the traversal directories that should not be processed.
 *
 * 1. Top-level project directory with the names listed in [excludedTopLevelDirs].
 * 2. `scripts/publish-documentation/buildSrc` directory, which is a symlink.
 */
private val File.isExcluded: Boolean
    get() = if (parent == ".") {
        name in excludedTopLevelDirs
    } else {
        excludedPaths.any { path.contains(it) }
    }

/**
 * Extensions of files to be processed.
 */
private val extensions = arrayOf("java")

private val nl = System.lineSeparator()

fun applyClassReplacement() {
    val projectRoot = File(".")
    val allReplacements = annotations
    projectRoot.walk()
        .onEnter {
            val enter = !it.isExcluded
            if (enter) {
                println("$it".removePrefix("./"))
            }
            enter
        }
        .filter { it.isFile && it.extension in extensions }
        .forEach {
            val fileUpdated = it.applyClassReplacement(allReplacements)
            if (fileUpdated) {
                println("  ${it.name} -> Modified.")
            }
        }
}

private fun File.applyClassReplacement(map: Map<ClassName, ClassName>): Boolean {
    val lines = readText().lines()
    var anythingReplaced = false
    val result = StringBuilder()
    lines.forEachIndexed { index, line ->
        // Replace the fully-qualified name first.
        var oldClassName = map.keys.find {
            line.contains(it.qualifiedName)
        }
        if (oldClassName != null) {
            val newClassName = map[oldClassName]!!
            val replaced = line.replace(oldClassName.qualifiedName, newClassName.qualifiedName)
            result.append(replaced)
            anythingReplaced = true
            return@forEachIndexed
        }

        // See if we need to replace the simple name.
        oldClassName = map.keys.find {
            line.contains(it.simpleName)
        }
        if (oldClassName != null) {
            val newClassName = map[oldClassName]!!
            // Do nothing if the simple names are the same.
            if (oldClassName.simpleName == newClassName.simpleName) {
                result.append(line)
                return@forEachIndexed
            }
            val replaced = line.replace(oldClassName.simpleName, newClassName.simpleName)
            result.append(replaced)
            anythingReplaced = true
            return@forEachIndexed
        }

        // Nothing was replaced.
        result.append(line)

        if (index < lines.size - 1) {
            result.append(nl)
        }
    }
    if (anythingReplaced) {
        writeText(result.toString())
    }
    return anythingReplaced
}

fun main() {
    applyClassReplacement()
}

main()
