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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.file.shouldNotExist
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir

/**
 * Verifies the SBOMs [PublicationSbom] adds to the publications of a real
 * multi-module build, run via Gradle TestKit.
 *
 * The fixture is built once for the whole class, because a build applying
 * Kotlin Multiplatform takes long to configure, and each case only inspects
 * what that build left behind. It runs `--offline`, resolving from a local
 * `file:` repository only — which also exercises the workaround for a plugin
 * bug that fails the SBOM task for such repositories.
 *
 * The fixture has:
 *  - `api` and `impl` — standard JVM modules, `impl` depending on `api` and
 *    `bundled`, on `com.example:lib` at runtime, and on two libraries it only
 *    compiles or tests with;
 *  - `bundled` — a JVM module left unpublished, like one bundled into the
 *    artifact of another module, such as a fat JAR;
 *  - `plugin` — a Gradle plugin, whose marker publication must stay without
 *    an SBOM;
 *  - `kmp` — a Kotlin Multiplatform module with a JVM target named `desktop`, not
 *    after its platform, and an umbrella publication that must stay without an
 *    SBOM. It has no sources, so it builds
 *    without the Kotlin compiler, which an offline build cannot fetch. As
 *    modules of the `logging` repository do, it is listed with custom publishing
 *    by the root project and also opens `spinePublishing` itself, so its SBOM is
 *    registered twice;
 *  - `twin` — a JVM module with custom publishing, whose two publications
 *    publish different artifacts, so each needs an SBOM naming its own, and
 *    whose third publication is removed once the module is evaluated;
 *  - `consumer` — a JVM module depending on `kmp`, whose SBOM must name the
 *    artifact of the JVM target of `kmp`.
 */
@DisplayName("`PublicationSbom` should")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class PublicationSbomIgTest {

    private lateinit var projectDir: File

    private lateinit var result: BuildResult

    /**
     * Builds the fixture in a directory that JUnit keeps for all cases of the class.
     */
    @BeforeAll
    fun buildFixture(@TempDir dir: File) {
        projectDir = dir
        writeRepository()
        writeBuild()
        result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                ":api:$publishTask",
                ":impl:$publishTask",
                ":plugin:$publishTask",
                ":impl:${PublicationChecksums.collectorTaskName}",
                ":kmp:${PublicationSbom.taskNameFor("desktop")}",
                ":twin:${PublicationSbom.taskName}",
                ":consumer:${PublicationSbom.taskName}",
                "--offline",
                "--stacktrace",
            )
            .build()
    }

    @Test
    fun `publish the SBOM of a module next to its artifacts`() {
        val files = stagedFiles("io/spine/test/spine-impl/$moduleVersion")

        files shouldContainAll listOf(
            "spine-impl-$moduleVersion.jar",
            "spine-impl-$moduleVersion.spdx.json"
        )
        implSbom()["spdxVersion"].asText() shouldBe "SPDX-2.3"
    }

    @Test
    fun `list the SBOM among the subjects of the attestation`() {
        val manifest = file("impl/build/attestation/checksums.txt").readLines()

        manifest.map { it.substringAfter("  ") } shouldContain
                "spine-impl-$moduleVersion.spdx.json"
    }

    @Test
    fun `describe runtime dependencies only`() {
        val names = implSbom().packageNames()

        names shouldContain "com.example:lib"
        names shouldNotContain "com.example:testlib"
        names shouldNotContain "com.example:annotations"
    }

    /**
     * The POM of `lib` declares no license and inherits one from its parent,
     * and both POMs configure the same plugins. The SPDX plugin, running on the
     * classpath of `buildSrc`, builds effective POMs that merge the configuration
     * of those plugins — on a classpath that also carries the XML libraries of the
     * Shadow plugin. Finding the license proves that the merge works there.
     */
    @Test
    fun `take the license of a dependency from its parent POM`() {
        implSbom().packageNamed("com.example:lib")["licenseDeclared"].asText() shouldBe "MIT"
    }

    @Test
    fun `leave the download location of a dependency from a local repository unasserted`() {
        val lib = implSbom().packageNamed("com.example:lib")

        lib["downloadLocation"].asText() shouldBe "NOASSERTION"
        lib.purl() shouldBe "pkg:maven/com.example/lib@1.0"
    }

    @Test
    fun `name the module and its siblings by their published coordinates`() {
        val sbom = implSbom()

        sbom["name"].asText() shouldBe "spine-impl"
        sbom["documentNamespace"].asText() shouldBe
                "https://spine.io/spdxdocs/io.spine.test/spine-impl/$moduleVersion"
        sbom.packageNames() shouldNotContainAnyOf listOf("impl", "api")
        listOf("spine-impl", "spine-api").forEach { artifactId ->
            val module = sbom.packageNamed("io.spine.test:$artifactId")
            module.purl() shouldBe "pkg:maven/io.spine.test/$artifactId@$moduleVersion"
            module["licenseDeclared"].asText() shouldBe "Apache-2.0"
        }
    }

    /**
     * An unpublished module has no coordinates to be named after, so it keeps the
     * name the plugin gives it. It is still code of this build, under its license.
     */
    @Test
    fun `describe an unpublished sibling by its project and the license of the build`() {
        val bundled = implSbom().packageNamed("bundled")

        bundled["versionInfo"].asText() shouldBe moduleVersion
        bundled["licenseDeclared"].asText() shouldBe "Apache-2.0"
        bundled.purl().shouldBeNull()
        result.output shouldNotContain "`:bundled`"
    }

    @Test
    fun `attach an SBOM to every publication but plugin markers and the KMP umbrella`() {
        val reported = result.output.lines()
            .filter { it.startsWith(reportPrefix) }
            .map { it.removePrefix(reportPrefix) }

        reported shouldContainAll listOf(
            ":api mavenJava 1",
            ":impl mavenJava 1",
            ":consumer mavenJava 1",
            ":plugin pluginMaven 1",
            ":plugin samplePluginMarkerMaven 0",
            ":kmp desktop 1",
            ":kmp kotlinMultiplatform 0",
            ":twin main 1",
            ":twin extra 1",
        )
    }

    /**
     * `spinePublishing` prefixes the artifact ID of each publication, as it does that
     * of the project. Neither artifact is named as the project, which an SBOM falls
     * back to when it cannot find its publication.
     */
    @Test
    fun `name the SBOM of each publication after the artifact it is published with`() {
        val artifacts = mapOf("main" to "spine-twin-main", "extra" to "spine-twin-extra")
        artifacts.forEach { (publication, artifactId) ->
            val sbom = file("twin/build/sbom/$publication.spdx.json").readJson()

            sbom["name"].asText() shouldBe artifactId
            sbom.packageNamed("io.spine.test:$artifactId").purl() shouldBe
                    "pkg:maven/io.spine.test/$artifactId@$moduleVersion"
        }
    }

    @Test
    fun `write no SBOM for a publication the build removes`() {
        file("twin/build/sbom/dropped.spdx.json").shouldNotExist()
    }

    @Test
    fun `describe the runtime of a KMP target`() {
        val sbom = file("kmp/build/sbom/desktop.spdx.json").readJson()

        sbom["name"].asText() shouldBe "spine-kmp-desktop"
        sbom.packageNames() shouldContainAll listOf("com.example:lib", "io.spine.test:spine-api")
    }

    /**
     * Gradle resolves `consumer`'s dependency on `kmp` to the artifact of the JVM target
     * of `kmp` by its platform — the target is named `desktop`.
     */
    @Test
    fun `name a multiplatform sibling after its artifact for the same platform`() {
        val sbom = file("consumer/build/sbom/mavenJava.spdx.json").readJson()

        sbom.packageNamed("io.spine.test:spine-kmp-desktop").purl() shouldBe
                "pkg:maven/io.spine.test/spine-kmp-desktop@$moduleVersion"
    }

    private fun implSbom(): JsonNode {
        val directory = "staged/io/spine/test/spine-impl/$moduleVersion"
        return file("$directory/spine-impl-$moduleVersion.spdx.json").readJson()
    }

    private fun stagedFiles(directory: String): List<String> =
        file("staged/$directory").listFiles()?.map { it.name }.orEmpty()

    /**
     * Writes a Maven repository with the external dependencies of the fixture.
     *
     * `lib` takes its license from its parent POM, so that an SBOM naming it
     * shows that the POM was built with its parent resolved. Both POMs configure
     * the same plugins, as real POMs do, so building the effective one merges
     * plugin configuration — once through the plugin management of the parent,
     * and once through inheritance.
     */
    private fun writeRepository() {
        pom(
            groupId = "com.example",
            artifactId = "parent",
            version = "1.0",
            body = listOf(parentHead, parentBuild).joinToString("\n")
        )
        library(artifactId = "lib", body = listOf(libParent, libBuild).joinToString("\n"))
        library(artifactId = "testlib")
        library(artifactId = "annotations")
    }

    private fun library(artifactId: String, body: String = "") {
        pom(groupId = "com.example", artifactId = artifactId, version = "1.0", body = body)
        val jar = file("repository/com/example/$artifactId/1.0/$artifactId-1.0.jar")
        JarOutputStream(jar.outputStream(), Manifest()).use { }
    }

    private fun pom(groupId: String, artifactId: String, version: String, body: String) {
        val path = "repository/${groupId.replace('.', '/')}/$artifactId/$version"
        val lines = listOf(
            """<?xml version="1.0" encoding="UTF-8"?>""",
            """<project xmlns="http://maven.apache.org/POM/4.0.0">""",
            "  <modelVersion>4.0.0</modelVersion>",
            "  <groupId>$groupId</groupId>",
            "  <artifactId>$artifactId</artifactId>",
            "  <version>$version</version>",
        ) + body.lines().filter { it.isNotBlank() }.map { "  $it" } + "</project>"
        write("$path/$artifactId-$version.pom", lines.joinToString("\n", postfix = "\n"))
    }

    private fun writeBuild() {
        write(
            "settings.gradle.kts",
            """
            rootProject.name = "sbom-sample"
            include("api", "impl", "bundled", "plugin", "kmp", "twin", "consumer")
            """.trimIndent()
        )
        // The standard library would be resolved from Maven Central otherwise,
        // which an offline build cannot reach.
        write("gradle.properties", "kotlin.stdlib.default.dependency=false\n")
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

            import io.spine.gradle.publish.spinePublishing

            allprojects {
                group = "io.spine.test"
                version = "$moduleVersion"
                repositories {
                    maven { url = uri("${'$'}{rootDir}/repository") }
                }
            }

            subprojects {
                apply(plugin = "maven-publish")

                // `javadocJar` of `spinePublishing` is wired to Dokka, which
                // this fixture does not apply.
                tasks.register("dokkaGeneratePublicationJavadoc")
            }

            spinePublishing {
                modules = setOf("api", "impl", "consumer")
                modulesWithCustomPublishing = setOf("plugin", "kmp", "twin")
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

            // The SBOMs are added once all projects are evaluated, so report afterwards.
            gradle.taskGraph.whenReady {
                subprojects {
                    the<PublishingExtension>().publications
                        .withType<MavenPublication>()
                        .forEach { publication ->
                            val sboms = publication.artifacts.count { it.extension == "spdx.json" }
                            println("$reportPrefix${'$'}path ${'$'}{publication.name} ${'$'}sboms")
                        }
                }
            }
            """.trimIndent()
        )
        write("api/build.gradle.kts", "plugins { `java-library` }\n")
        javaSource(module = "api", packageName = "sample.api", declaration = "class Api {}")
        write(
            "impl/build.gradle.kts",
            """
            plugins { `java-library` }

            dependencies {
                implementation(project(":api"))
                implementation(project(":bundled"))
                implementation("com.example:lib:1.0")
                compileOnly("com.example:annotations:1.0")
                testImplementation("com.example:testlib:1.0")
            }
            """.trimIndent()
        )
        javaSource(module = "impl", packageName = "sample.impl", declaration = "class Impl {}")
        write("bundled/build.gradle.kts", "plugins { `java-library` }\n")
        javaSource(
            module = "bundled",
            packageName = "sample.bundled",
            declaration = "class Bundled {}"
        )
        write(
            "plugin/build.gradle.kts",
            """
            plugins { `java-gradle-plugin` }

            gradlePlugin {
                plugins {
                    create("sample") {
                        id = "io.spine.test.sample"
                        implementationClass = "sample.plugin.SamplePlugin"
                    }
                }
            }
            """.trimIndent()
        )
        javaSource(
            module = "plugin",
            packageName = "sample.plugin",
            declaration = """
                public class SamplePlugin implements org.gradle.api.Plugin<org.gradle.api.Project> {
                    @Override public void apply(org.gradle.api.Project project) {}
                }
                """.trimIndent()
        )
        write(
            "kmp/build.gradle.kts",
            """
            import io.spine.gradle.publish.spinePublishing

            plugins { kotlin("multiplatform") }

            kotlin {
                jvm("desktop")
                sourceSets.getByName("desktopMain").dependencies {
                    implementation(project(":api"))
                    implementation("com.example:lib:1.0")
                }
            }

            spinePublishing {
                customPublishing = true
                destinations = emptySet()
            }
            """.trimIndent()
        )
        write(
            "twin/build.gradle.kts",
            """
            plugins {
                `java-library`
                `maven-publish`
            }

            publishing {
                publications {
                    create<MavenPublication>("main") {
                        from(components["java"])
                        artifactId = "twin-main"
                    }
                    create<MavenPublication>("extra") {
                        from(components["java"])
                        artifactId = "twin-extra"
                    }
                    create<MavenPublication>("dropped") {
                        from(components["java"])
                        artifactId = "twin-dropped"
                    }
                }
            }

            // As builds do with the `pluginMaven` publication `java-gradle-plugin` adds.
            afterEvaluate {
                publishing.publications.removeIf { it.name == "dropped" }
            }
            """.trimIndent()
        )
        write(
            "consumer/build.gradle.kts",
            """
            plugins { `java-library` }

            dependencies {
                implementation(project(":kmp"))
            }
            """.trimIndent()
        )
    }

    private fun javaSource(module: String, packageName: String, declaration: String) {
        val className = declaration.substringAfter("class ").substringBefore(' ')
        val path = "$module/src/main/java/${packageName.replace('.', '/')}/$className.java"
        write(path, "package $packageName;\n\n$declaration\n")
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

        /** The version of the modules of the fixture. */
        const val moduleVersion = "1.0.0"

        const val publishTask = "publishAllPublicationsToStageRepository"

        /** Marks the lines in which the fixture reports the SBOMs of each publication. */
        const val reportPrefix = "SBOM-REPORT "

        /** The packaging and the license of the parent POM of `lib`. */
        val parentHead = """
            <packaging>pom</packaging>
            <licenses>
              <license>
                <name>The MIT License</name>
                <url>http://www.opensource.org/licenses/mit-license.php</url>
              </license>
            </licenses>
            """.trimIndent()

        /** The plugin configuration of the parent POM of `lib`. */
        val parentBuild = """
            <build>
              <pluginManagement>
                <plugins>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                      <release>8</release>
                    </configuration>
                  </plugin>
                </plugins>
              </pluginManagement>
              <plugins>
                <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-jar-plugin</artifactId>
                  <configuration>
                    <skipIfEmpty>true</skipIfEmpty>
                  </configuration>
                </plugin>
              </plugins>
            </build>
            """.trimIndent()

        /** Makes `com.example:parent` the parent POM of `lib`. */
        val libParent = """
            <parent>
              <groupId>com.example</groupId>
              <artifactId>parent</artifactId>
              <version>1.0</version>
            </parent>
            """.trimIndent()

        /** The plugin configuration of `lib`, merged with that of its parent. */
        val libBuild = """
            <build>
              <plugins>
                <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-compiler-plugin</artifactId>
                  <configuration>
                    <parameters>true</parameters>
                  </configuration>
                </plugin>
                <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-jar-plugin</artifactId>
                  <configuration>
                    <forceCreation>true</forceCreation>
                  </configuration>
                </plugin>
              </plugins>
            </build>
            """.trimIndent()
    }
}

private fun File.readJson(): JsonNode = ObjectMapper().readTree(this)

private fun JsonNode.packageNames(): List<String> =
    this["packages"].shouldNotBeNull().map { it["name"].asText() }

private fun JsonNode.packageNamed(name: String): JsonNode =
    this["packages"].shouldNotBeNull()
        .firstOrNull { it["name"].asText() == name }
        .shouldNotBeNull()

private fun JsonNode.purl(): String? =
    this["externalRefs"]
        ?.firstOrNull { it["referenceType"].asText() == "purl" }
        ?.get("referenceLocator")
        ?.asText()
