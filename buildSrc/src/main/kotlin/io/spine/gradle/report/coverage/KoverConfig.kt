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

package io.spine.gradle.report.coverage

import io.spine.dependency.test.Jacoco
import io.spine.dependency.test.Kover
import io.spine.gradle.sourceSets
import java.io.File
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.configure

/**
 * Configures Kover at the root of a multi-module Gradle project to aggregate
 * coverage across subprojects and exclude classes that originate from
 * `generated/` source directories.
 *
 * Apply once from the root build script:
 * ```
 * KoverConfig.applyTo(rootProject)
 * ```
 *
 * The configuration:
 *
 *  - Applies the Kover plugin to the root project.
 *  - Pins the coverage engine to the JaCoCo version declared in
 *    [io.spine.dependency.test.Jacoco] via `useJacoco(...)`.
 *  - For every subproject that applies Kover, adds a `kover(project(...))`
 *    dependency so the subproject's coverage flows into the root rollup,
 *    and pushes the subproject's generated-class FQNs into its own
 *    `kover { reports { filters { excludes { classes(...) } } } }`.
 *  - Configures the root `koverXmlReport` task with `onCheck = true` and
 *    excludes the union of generated-class FQNs across all subprojects.
 *
 * This is the Kover-based successor to the deprecated
 * [io.spine.gradle.report.coverage.JacocoConfig]. The behaviour mirrors what
 * `JacocoConfig.applyTo(rootProject)` provided, but is wired through Kover
 * (`koverXmlReport`) instead of vanilla `jacocoRootReport`.
 */
@Suppress("unused")
class KoverConfig private constructor(
    private val rootProject: Project,
) {

    companion object {

        private const val GENERATED_MARKER: String = "generated"
        private const val KOTLIN_SOURCE_SET_EXT_NAME: String = "kotlin"
        private const val JAVA_SOURCE_SUFFIX: String = ".java"
        private const val KOTLIN_SOURCE_SUFFIX: String = ".kt"
        private const val PROTO_KOTLIN_SUFFIX: String = ".proto.kt"
        private const val KOTLIN_FILE_CLASS_SUFFIX: String = "Kt"

        /**
         * Configures Kover aggregation and generated-code exclusion at the
         * root of a multi-module Gradle project.
         *
         * Must be called with the root project; throws an
         * [IllegalArgumentException] if called with a non-root project, and
         * an [IllegalStateException] if [project] has no subprojects —
         * a single-module Gradle project does not need root aggregation,
         * so apply the `jvm-module` / `kmp-module` script plugin (or the
         * Kover plugin) directly to that module instead.
         *
         * Eligibility is determined per subproject: only subprojects that
         * apply the Kover plugin (directly or through `jvm-module` /
         * `kmp-module`) are wired into the rollup. Subprojects that apply
         * Kover after `applyTo` returns are still picked up — discovery
         * runs in `gradle.projectsEvaluated`.
         */
        fun applyTo(project: Project) {
            require(project == project.rootProject) {
                "`KoverConfig.applyTo` must be called with the root project. " +
                        "Received ${project.path}."
            }
            check(project.subprojects.isNotEmpty()) {
                "In a single-module Gradle project, `KoverConfig` is NOT needed. " +
                        "Apply the Kover plugin directly to the module instead."
            }
            project.pluginManager.apply(Kover.id)
            KoverConfig(project).configure()
        }
    }

    private fun configure() {
        rootProject.gradle.projectsEvaluated {
            val eligible = rootProject.subprojects.filter {
                it.pluginManager.hasPlugin(Kover.id)
            }
            val allGenerated = sortedSetOf<String>()
            eligible.forEach { sub ->
                addAggregationDependency(sub)
                val perModule = generatedClassFqns(sub)
                applyExcludes(sub, perModule)
                allGenerated.addAll(perModule)
            }
            configureRoot(allGenerated)
        }
    }

    private fun addAggregationDependency(sub: Project) {
        rootProject.dependencies.add("kover", rootProject.project(sub.path))
    }

    private fun applyExcludes(sub: Project, fqns: Collection<String>) {
        if (fqns.isEmpty()) {
            return
        }
        sub.extensions.configure(KoverProjectExtension::class.java) {
            reports {
                filters {
                    excludes {
                        classes(fqns.toExclusionPatterns())
                    }
                }
            }
        }
    }

    private fun configureRoot(fqns: Collection<String>) {
        rootProject.extensions.configure(KoverProjectExtension::class.java) {
            useJacoco(Jacoco.version)
            reports {
                total {
                    xml {
                        onCheck.set(true)
                    }
                }
                if (fqns.isNotEmpty()) {
                    filters {
                        excludes {
                            classes(fqns.toExclusionPatterns())
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the fully-qualified names of all classes that originate from
     * `generated/` source directories of the [project]'s `main` source set.
     *
     * Returns an empty list if the project does not expose a Java/Kotlin
     * `main` source set (for example, a pure non-JVM module).
     */
    private fun generatedClassFqns(project: Project): List<String> {
        val main = project.sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME)
            ?: return emptyList()
        return generatedSrcDirs(main)
            .asSequence()
            .filter { it.exists() && it.isDirectory }
            .flatMap { root ->
                root.walk()
                    .filter { !it.isDirectory }
                    .flatMap { it.fqnsRelativeTo(root).asSequence() }
            }
            .distinct()
            .toList()
    }

    private fun generatedSrcDirs(main: SourceSet): Set<File> {
        val javaDirs = main.allJava.srcDirs
        val kotlinDirs =
            (main.extensions.findByName(KOTLIN_SOURCE_SET_EXT_NAME) as? SourceDirectorySet)
                ?.srcDirs
                ?: emptySet()
        return (javaDirs + kotlinDirs).filter { it.absolutePath.contains(GENERATED_MARKER) }
            .toSet()
    }

    /**
     * Derives one or more class FQNs from this source file's path relative
     * to [root].
     *
     *  - `.java` — one FQN.
     *  - `.kt` — the declared class plus the Kotlin file-class synthetic
     *    (`<Name>Kt`).
     *  - `.proto.kt` — `protoc-gen-kotlin` convention; the two-part suffix
     *    is stripped, otherwise treated as a `.kt` file.
     *  - any other extension — an empty list.
     *
     * Returns an empty list if this file is not under [root].
     */
    private fun File.fqnsRelativeTo(root: File): List<String> {
        if (!startsWith(root)) {
            return emptyList()
        }
        val relative = toRelativeString(root)
        return when {
            relative.endsWith(PROTO_KOTLIN_SUFFIX) -> {
                val base = relative.removeSuffix(PROTO_KOTLIN_SUFFIX).toFqn()
                listOf(base, base + KOTLIN_FILE_CLASS_SUFFIX)
            }
            relative.endsWith(KOTLIN_SOURCE_SUFFIX) -> {
                val base = relative.removeSuffix(KOTLIN_SOURCE_SUFFIX).toFqn()
                listOf(base, base + KOTLIN_FILE_CLASS_SUFFIX)
            }
            relative.endsWith(JAVA_SOURCE_SUFFIX) ->
                listOf(relative.removeSuffix(JAVA_SOURCE_SUFFIX).toFqn())
            else -> emptyList()
        }
    }
}

/**
 * Expands each fully-qualified class name into two Kover exclusion
 * patterns: the class itself, and `<FQN>$*` for any nested or anonymous
 * classes the compiler emits alongside it.
 */
private fun Collection<String>.toExclusionPatterns(): List<String> =
    flatMap { listOf(it, "$it\$*") }

private fun String.toFqn(): String = replace(File.separatorChar, '.')
