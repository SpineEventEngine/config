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

package io.spine.gradle.publish

import io.kotest.matchers.shouldBe
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Verifies that the checksum tasks survive a module configuring `spinePublishing`
 * on its own, alongside the root project.
 *
 * This is a supported arrangement rather than an exotic one: a module with a
 * custom publication opens the extension with `customPublishing = true` — as
 * `uber-jar-module.gradle.kts` does — and the same module must also appear in
 * the root extension's `modulesWithCustomPublishing`, or its publication is
 * ignored. Both calls run `configured()` against the same module, so registering
 * a task unconditionally fails the build while it is still being configured,
 * which no amount of test coverage over a single-extension build would reveal.
 */
@DisplayName("`publicationChecksums` task should, with a custom publication,")
internal class PublicationChecksumsCustomPublishingIgTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `tolerate the extension configured in both the root and the module`() {
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "custom-sample"
            include("uber")
            """.trimIndent()
        )
        file("build.gradle.kts").writeText(
            """
            buildscript {
                dependencies {
                    classpath(files(
            ${buildSrcClasspath()}
                    ))
                }
            }

            import io.spine.gradle.publish.spinePublishing

            allprojects {
                group = "io.spine.sample"
                version = "1.0.0"
            }

            subprojects {
                apply(plugin = "java-library")
                apply(plugin = "maven-publish")
                tasks.register("dokkaGeneratePublicationJavadoc")
            }

            spinePublishing {
                modulesWithCustomPublishing = setOf("uber")
                destinations = emptySet()
            }
            """.trimIndent()
        )
        file("uber/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                import io.spine.gradle.publish.spinePublishing

                spinePublishing {
                    customPublishing = true
                    destinations = emptySet()
                }
                """.trimIndent()
            )
        }
        file("uber/src/main/java/sample/Stub.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package sample;
                public class Stub {}
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("publicationChecksums", "--stacktrace")
            .build()

        result.task(":publicationChecksums")?.outcome shouldBe TaskOutcome.SUCCESS
    }

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
