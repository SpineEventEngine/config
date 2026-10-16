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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.spine.dependency.lib.Protobuf
import java.io.File
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Element

/**
 * Verifies [unbundleProtobufRuntime] in a real build of a fat JAR, run via Gradle TestKit.
 *
 * The fixture is built once for the whole class, and each case inspects what
 * the build left behind. It runs `--offline`, resolving from a local `file:`
 * repository, which holds stand-ins for the Protobuf runtime libraries.
 *
 * The `fat` module of the fixture depends on the Protobuf runtime and on
 * `com.example:lib`. `protobuf-java-util` depends on `com.example:gson`, as
 * the genuine one depends on Gson. The module publishes its fat JAR in
 * the `fatJar` publication, with `artifact(...)`, and its Java component, which
 * carries the shadowed variant, in the `library` publication.
 */
@DisplayName("`unbundleProtobufRuntime` should")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ProtobufRuntimeIgTest {

    private lateinit var projectDir: File

    @BeforeAll
    fun buildFixture(@TempDir dir: File) {
        projectDir = dir
        writeRepository()
        writeBuild()
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                ":fat:shadowJar",
                ":fat:generatePomFileForFatJarPublication",
                ":fat:generateMetadataFileForLibraryPublication",
                "--offline",
                "--stacktrace",
            )
            .build()
    }

    @Test
    fun `leave the Protobuf runtime out of the fat JAR`() {
        val entries = fatJarEntries()

        entries.filter { it.startsWith("com/google/protobuf/") } shouldBe emptyList()
        entries shouldNotContain "META-INF/protobuf-kotlin.kotlin_module"
    }

    @Test
    fun `keep bundling the other dependencies, including those of the runtime`() {
        fatJarEntries() shouldContainAll listOf(
            "com/example/Lib.class",
            "com/example/gson/Gson.class",
        )
    }

    @Test
    fun `not name the runtime in the manifest of the fat JAR`() {
        JarFile(fatJar()).use { jar ->
            jar.manifest.mainAttributes.getValue("Class-Path").shouldBeNull()
        }
    }

    @Test
    fun `declare the runtime in the POM of the fat JAR`() {
        val pom = file("fat/build/publications/$fatJar/pom-default.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom)
        val dependencies = document.getElementsByTagName("dependency").elements()

        dependencies.map { it.childText("artifactId") } shouldContainExactly runtimeLibraries
        dependencies.forEach {
            it.childText("groupId") shouldBe Protobuf.group
            it.childText("version") shouldBe Protobuf.version
            it.childText("scope") shouldBe "runtime"
            val exclusion = it.getElementsByTagName("exclusion").elements().single()
            exclusion.childText("groupId") shouldBe "*"
            exclusion.childText("artifactId") shouldBe "*"
        }
    }

    @Test
    fun `declare the runtime in the shadowed variant`() {
        val metadata = ObjectMapper().readTree(file("fat/build/publications/library/module.json"))
        val variant = metadata["variants"].single { it["name"].asText() == shadowedVariant }
        val dependencies = variant["dependencies"].toList()

        dependencies.map { it["module"].asText() } shouldContainExactlyInAnyOrder runtimeLibraries
        dependencies.forEach {
            it["group"].asText() shouldBe Protobuf.group
            it["version"]["requires"].asText() shouldBe Protobuf.version
            it.excludesAll() shouldBe true
        }
    }

    private fun fatJar(): File = file("fat/build/libs/fat-$moduleVersion-all.jar")

    private fun fatJarEntries(): List<String> =
        JarFile(fatJar()).use { jar -> jar.entries().toList().map { it.name } }

    /**
     * Writes the local repository with the stand-ins of the Protobuf runtime
     * and the other libraries of the fixture.
     */
    private fun writeRepository() {
        val protobufJava = dependencyOn(Protobuf.group, "protobuf-java", Protobuf.version)
        library(
            Protobuf.group, "protobuf-java", Protobuf.version,
            entries = listOf("com/google/protobuf/RuntimeVersion.class"),
        )
        library(
            Protobuf.group, "protobuf-java-util", Protobuf.version,
            entries = listOf("com/google/protobuf/util/JsonFormat.class"),
            dependencies = listOf(protobufJava, dependencyOn("com.example", "gson", "1.0")),
        )
        library(
            Protobuf.group, "protobuf-kotlin", Protobuf.version,
            entries = listOf(
                "com/google/protobuf/kotlin/DslList.class",
                "META-INF/protobuf-kotlin.kotlin_module",
            ),
            dependencies = listOf(protobufJava),
        )
        library("com.example", "lib", "1.0", entries = listOf("com/example/Lib.class"))
        library("com.example", "gson", "1.0", entries = listOf("com/example/gson/Gson.class"))
    }

    private fun library(
        groupId: String,
        artifactId: String,
        version: String,
        entries: List<String>,
        dependencies: List<String> = emptyList(),
    ) {
        val path = "repository/${groupId.replace('.', '/')}/$artifactId/$version"
        val dependenciesElement = if (dependencies.isEmpty()) {
            ""
        } else {
            dependencies.joinToString("\n", "  <dependencies>\n", "\n  </dependencies>")
        }
        write(
            "$path/$artifactId-$version.pom",
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<project xmlns="http://maven.apache.org/POM/4.0.0">
            |  <modelVersion>4.0.0</modelVersion>
            |  <groupId>$groupId</groupId>
            |  <artifactId>$artifactId</artifactId>
            |  <version>$version</version>
            |$dependenciesElement
            |</project>
            |""".trimMargin()
        )
        val jar = file("$path/$artifactId-$version.jar")
        JarOutputStream(jar.outputStream(), Manifest()).use { out ->
            entries.forEach {
                out.putNextEntry(ZipEntry(it))
                out.closeEntry()
            }
        }
    }

    private fun dependencyOn(groupId: String, artifactId: String, version: String): String =
        """
        |    <dependency>
        |      <groupId>$groupId</groupId>
        |      <artifactId>$artifactId</artifactId>
        |      <version>$version</version>
        |    </dependency>
        """.trimMargin()

    private fun writeBuild() {
        write(
            "settings.gradle.kts",
            """
            rootProject.name = "protobuf-runtime-sample"
            include("fat")
            """.trimIndent()
        )
        write(
            "build.gradle.kts",
            """
            buildscript {
                dependencies {
                    classpath(files(
            ${buildSrcClasspath()}
                    ))
                }
            }

            allprojects {
                group = "io.spine.test"
                version = "$moduleVersion"
                repositories {
                    maven { url = uri("${'$'}{rootDir}/repository") }
                }
            }
            """.trimIndent()
        )
        val runtime = Protobuf.libs.joinToString("\n") { "    implementation(\"$it\")" }
        write(
            "fat/build.gradle.kts",
            """
            |import io.spine.gradle.publish.unbundleProtobufRuntime
            |
            |plugins {
            |    `java-library`
            |    `maven-publish`
            |    id("com.gradleup.shadow")
            |}
            |
            |dependencies {
            |    implementation("com.example:lib:1.0")
            |$runtime
            |}
            |
            |publishing {
            |    publications {
            |        create<MavenPublication>("$fatJar") {
            |            artifactId = "fat-all"
            |            artifact(tasks.shadowJar)
            |        }
            |        create<MavenPublication>("library") {
            |            from(components["java"])
            |        }
            |    }
            |}
            |
            |unbundleProtobufRuntime()
            |""".trimMargin()
        )
    }

    private fun file(relativePath: String): File = projectDir.resolve(relativePath)

    private fun write(relativePath: String, text: String) {
        file(relativePath).apply {
            parentFile.mkdirs()
            writeText(text)
        }
    }

    /**
     * Renders the classpath with the production classes of `buildSrc` as
     * arguments of `files(...)`, for injection into the build script classpath
     * of the generated build.
     *
     * The classpath comes from the `test` task in `buildSrc/build.gradle.kts`.
     *
     * Each entry after the first starts a line of the generated script, so each
     * is prefixed with the indentation of the template it is inserted into, which
     * `trimIndent()` then removes along with the rest.
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

    private companion object {

        /** The version of the module of the fixture. */
        const val moduleVersion = "1.0.0"

        /** The publication of the fat JAR. */
        const val fatJar = "fatJar"

        /** The variant of the Java component published by the Shadow plugin. */
        const val shadowedVariant = "shadowRuntimeElements"

        /** The artifact IDs of the Protobuf runtime libraries, in their order in the POM. */
        val runtimeLibraries: List<String> =
            Protobuf.libs.map { it.substringBeforeLast(':').substringAfter(':') }
    }
}

private fun org.w3c.dom.NodeList.elements(): List<Element> =
    (0 until length).map { item(it) as Element }

private fun Element.childText(tagName: String): String =
    getElementsByTagName(tagName).item(0).textContent

/**
 * Tells if this dependency of the Gradle module metadata excludes all its transitive
 * dependencies.
 */
private fun JsonNode.excludesAll(): Boolean =
    this["excludes"]?.any { it["group"].asText() == "*" && it["module"].asText() == "*" } == true
