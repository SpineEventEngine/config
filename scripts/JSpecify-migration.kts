/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.File

private data class ClassName(val qualifiedName: String) {
    val simpleName: String = qualifiedName.substringAfterLast(".")
    val asAnnotation = "@$simpleName"
}

/**
 * Creates a replacement API migration instruction.
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
        "javax.annotation.Nullable",
        "org.jspecify.annotations.Nullable"
    )
    migrate(
        "org.checkerframework.checker.nullness.qual.Nullable",
        "org.jspecify.annotations.Nullable"
    )
    migrate(
        "org.checkerframework.checker.nullness.qual.NonNull",
        "org.jspecify.annotations.NonNull"
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

/**
 * The paths excluded from the traversal at all levels.
 */
private val excludedPaths = setOf(
    "buildSrc/.gradle",
    "buildSrc/build",
    "tmp/",
    "/build/",
    "/generated/",
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

        fun StringBuilder.appendLine(l: String, replaced: Boolean = false) {
            append(l)
            if (index < lines.size - 1) {
                result.append(nl)
            }
            if (replaced) {
                anythingReplaced = true
            }
        }

        // Replace the fully-qualified name first.
        var oldClassName = map.keys.find {
            line.contains(it.qualifiedName)
        }
        if (oldClassName != null) {
            val newClassName = map[oldClassName]!!
            val replaced = line.replace(oldClassName.qualifiedName, newClassName.qualifiedName)
            result.appendLine(replaced, true)
            return@forEachIndexed
        }

        // See if we need to replace the simple annotation name.
        oldClassName = map.keys.find {
            line.contains(it.asAnnotation)
        }
        if (oldClassName != null) {
            val newClassName = map[oldClassName]!!
            // Do nothing if the simple names are the same.
            if (oldClassName.simpleName == newClassName.simpleName) {
                result.appendLine(line)
                return@forEachIndexed
            }
            val replaced = line.replace(oldClassName.asAnnotation, newClassName.asAnnotation)
            result.appendLine(replaced, true)
            return@forEachIndexed
        }

        // Nothing was replaced.
        result.appendLine(line)
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
