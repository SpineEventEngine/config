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

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File
import java.security.MessageDigest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Verifies the `publicationChecksums` task against a real multi-module build
 * run via Gradle TestKit.
 *
 * The manifest this task writes is the sole input by which `actions/attest`
 * decides what to attest: it takes both the digest and the name from the file
 * and never reads the artifacts. A missing entry therefore narrows a signed
 * claim without any symptom, and a name that is merely close enough describes
 * something nobody publishes. Both are invisible to a unit test over the
 * derivation alone, so the fixture publishes to a local repository and compares
 * the manifest against the files that actually arrive there.
 */
@DisplayName("`publicationChecksums` task should")
internal class PublicationChecksumsIgTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setUpProject() {
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "checksums-sample"
            include("api", "backend")
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
                version = "$VERSION"
            }

            subprojects {
                apply(plugin = "java-library")
                apply(plugin = "maven-publish")

                // `javadocJar` of `spinePublishing` is wired to Dokka, which
                // this fixture does not apply: the manifest is what is under
                // test, not the documentation toolchain.
                tasks.register("dokkaGeneratePublicationJavadoc")
            }

            spinePublishing {
                modules = setOf("api", "backend")
                destinations = emptySet()
            }

            subprojects {
                the<PublishingExtension>().repositories {
                    maven {
                        name = "stage"
                        url = uri("${'$'}{rootDir}/staged")
                    }
                }
            }
            """.trimIndent()
        )
        sourceFile("api")
        sourceFile("backend")
    }

    @Test
    fun `list every published artifact under its published name`() {
        val result = runGradle("publicationChecksums", "publishAllPublicationsToStageRepository")

        result.task(":publicationChecksums")?.outcome shouldBe TaskOutcome.SUCCESS

        // Spelled out rather than only compared with the staged files: two empty
        // lists also "contain exactly" each other, and this suite must not be
        // able to pass by describing nothing.
        // The standard publication of a module without Proto or a test JAR:
        // the compilation output, the three documentation and source archives
        // added by `artifacts(JarFlags)`, and the two metadata files.
        val expected = listOf("api", "backend").flatMap { module ->
            listOf(
                "spine-$module-$VERSION.jar",
                "spine-$module-$VERSION-sources.jar",
                "spine-$module-$VERSION-javadoc.jar",
                "spine-$module-$VERSION-html-docs.jar",
                "spine-$module-$VERSION.pom",
                "spine-$module-$VERSION.module",
            )
        }.sorted()

        val manifest = manifestEntries().map { it.name }.sorted()
        manifest shouldContainExactly expected
        manifest shouldContainExactly publishedFileNames()
    }

    @Test
    fun `record the digest of each published file`() {
        runGradle("publicationChecksums", "publishAllPublicationsToStageRepository")

        manifestEntries().forEach { entry ->
            val published = stagedFile(entry.name)
            entry.digest shouldBe published.sha256()
        }
    }

    @Test
    fun `apply the artifact prefix to the published names`() {
        runGradle("publicationChecksums")

        val names = manifestEntries().map { it.name }
        names.forEach { it.startsWith("spine-") shouldBe true }
        names.contains("spine-api-$VERSION.jar") shouldBe true
        names.contains("spine-api-$VERSION.pom") shouldBe true
        names.contains("spine-api-$VERSION.module") shouldBe true
        names.contains("spine-api-$VERSION-sources.jar") shouldBe true
    }

    /**
     * The names of the files that the build actually publishes, excluding the
     * digest sidecars and the repository-level metadata, which the repository
     * generates rather than the build.
     */
    private fun publishedFileNames(): List<String> =
        file("staged").walkTopDown()
            .filter { it.isFile }
            .map { it.name }
            .filterNot { it.startsWith("maven-metadata") }
            .filterNot { SIDECARS.any(it::endsWith) }
            .toList()
            .sorted()

    private fun stagedFile(name: String): File =
        file("staged").walkTopDown().first { it.isFile && it.name == name }

    private fun manifestEntries(): List<ManifestEntry> =
        file("build/attestation/subject-checksums.txt")
            .readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val digest = line.substringBefore(' ')
                ManifestEntry(digest, line.substringAfter(' ').trim())
            }

    private fun sourceFile(module: String) {
        file("$module/src/main/java/sample/$module/Stub.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package sample.$module;
                public class Stub {}
                """.trimIndent()
            )
        }
    }

    private fun runGradle(vararg args: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*args, "--stacktrace")
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

    private data class ManifestEntry(val digest: String, val name: String)

    private companion object {

        const val VERSION = "1.0.0"

        val SIDECARS = listOf(".md5", ".sha1", ".sha256", ".sha512")
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(readBytes())
    return digest.joinToString("") { "%02x".format(it) }
}
