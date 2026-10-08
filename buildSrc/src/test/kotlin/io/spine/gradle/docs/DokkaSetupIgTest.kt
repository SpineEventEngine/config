/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.gradle.docs

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SKIPPED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Verifies when the `dokka-setup` plugin lets Dokka tasks run in a real build
 * that uses the configuration cache.
 *
 * Every case runs the build twice: the first run stores the configuration cache entry,
 * and the second one reuses it. The task must reach the same outcome both times, so the
 * decision whether to run cannot rely on configuration that a reused entry skips.
 *
 * Dokka's own tasks resolve the Dokka engine from Maven Central, which an offline
 * build cannot reach. So the build declares a `docsProbe` task that extends
 * [DokkaBaseTask][org.jetbrains.dokka.gradle.tasks.DokkaBaseTask] and does nothing.
 * The plugin gates it like any other Dokka task.
 */
@DisplayName("`dokka-setup` should, with the configuration cache,")
internal class DokkaSetupIgTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun createBuild() {
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "documented-sample"
            include("lib")
            """.trimIndent()
        )
        // The standard library would be resolved from Maven Central otherwise,
        // which an offline build cannot reach.
        file("gradle.properties").writeText("kotlin.stdlib.default.dependency=false\n")
        file("build.gradle.kts").writeText(
            """
            buildscript {
                dependencies {
                    classpath(files(
            ${buildSrcClasspath()}
                    ))
                }
            }
            """.trimIndent()
        )
        file("lib").mkdirs()
        file("lib/build.gradle.kts").writeText(
            """
            import org.jetbrains.dokka.gradle.internal.InternalDokkaGradlePluginApi
            import org.jetbrains.dokka.gradle.tasks.DokkaBaseTask

            plugins {
                kotlin("jvm")
                `maven-publish`
                id("dokka-setup")
            }

            @OptIn(InternalDokkaGradlePluginApi::class)
            abstract class DocsProbe : DokkaBaseTask() {

                @TaskAction
                fun probe() = Unit
            }

            val docsProbe = tasks.register<DocsProbe>("docsProbe")

            tasks.named("publish") {
                dependsOn(docsProbe)
            }

            tasks.register("dokkaGenerateSample") {
                dependsOn(docsProbe)
            }
            """.trimIndent()
        )
    }

    @Test
    fun `skip a Dokka task when the build neither publishes nor generates documentation`() {
        runStoringThenReusing(":lib:docsProbe") {
            it.task(":lib:docsProbe")?.outcome shouldBe SKIPPED
        }
    }

    @Test
    fun `run a Dokka task when the build publishes`() {
        runStoringThenReusing(":lib:publish") {
            it.task(":lib:docsProbe")?.outcome shouldBe SUCCESS
        }
    }

    @Test
    fun `run a Dokka task when the build generates documentation`() {
        runStoringThenReusing(":lib:dokkaGenerateSample") {
            it.task(":lib:docsProbe")?.outcome shouldBe SUCCESS
        }
    }

    /**
     * Runs the given [tasks] twice with the configuration cache, and checks the result of
     * each run with [verify].
     *
     * The first run stores the cache entry, and the second one reuses it.
     */
    private fun runStoringThenReusing(vararg tasks: String, verify: (BuildResult) -> Unit) {
        val stored = runGradle(tasks)
        withClue("The run that stores the configuration cache entry") {
            stored.output shouldContain "Configuration cache entry stored"
            verify(stored)
        }
        val reused = runGradle(tasks)
        withClue("The run that reuses the configuration cache entry") {
            reused.output shouldContain "Reusing configuration cache"
            verify(reused)
        }
    }

    private fun runGradle(tasks: Array<out String>): BuildResult =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                *tasks,
                "--configuration-cache",
                "--offline",
                "--stacktrace"
            )
            .build()

    private fun file(relativePath: String): File = projectDir.resolve(relativePath)

    /**
     * Renders the classpath with the production classes of `buildSrc` as
     * arguments of `files(...)`, for injection into the build script classpath
     * of the generated build.
     *
     * The classpath comes from the `test` task in `buildSrc/build.gradle.kts`.
     */
    private fun buildSrcClasspath(): String {
        val classpath = requireNotNull(System.getProperty("buildSrc.classpath")) {
            "The `buildSrc.classpath` system property is not set." +
                    " It is supplied by the `test` task in `buildSrc/build.gradle.kts`."
        }
        return classpath.split(File.pathSeparator).joinToString(",\n") {
            "            \"${File(it).invariantSeparatorsPath}\""
        }
    }
}
