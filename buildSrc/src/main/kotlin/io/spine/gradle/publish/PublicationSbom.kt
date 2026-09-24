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

import DocumentationSettings
import io.spine.gradle.SpineTaskGroup
import io.spine.gradle.artifactId
import java.net.URI
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.spdx.sbom.gradle.SpdxSbomExtension
import org.spdx.sbom.gradle.SpdxSbomPlugin
import org.spdx.sbom.gradle.SpdxSbomTask
import org.spdx.sbom.gradle.extensions.DefaultSpdxSbomTaskExtension

/**
 * Publishes a Software Bill of Materials (SBOM) with each artifact of this build.
 *
 * Usage:
 * ```
 * PublicationSbom.registerTasks(project, projectsToPublish)
 * ```
 *
 * The SBOM is an SPDX 2.3 document, written by the
 * [SPDX Gradle Plugin](https://github.com/spdx/spdx-gradle-plugin) and completed by
 * [PublicationSbomTask]. It lists what an artifact depends on at runtime — never the
 * build tooling, nor the libraries used only to test it. Being published next to the
 * artifact, as `<artifactId>-<version>.spdx.json`, it enters the manifest of
 * [PublicationChecksums], so the provenance attestation covers it too.
 *
 * ## One SBOM per published artifact
 *
 * A JVM module gets one SBOM of its `runtimeClasspath`, published with each of its
 * publications. A Kotlin Multiplatform module publishes an artifact per target, so it
 * gets an SBOM per target, published with the publication of that target only. The
 * `kotlinMultiplatform` umbrella publication has no runtime of its own, and gets none.
 * A Kotlin/Native compilation has no runtime configuration either: its klibs are linked
 * into the binary of the consumer, so the SBOM of a Native target describes the
 * dependencies of its compilation instead.
 *
 * The Gradle Plugin Portal does not receive the SBOM: its upload skips files other than
 * JARs, so the SBOM of a Gradle plugin is published to the Maven repositories only.
 * A plugin marker publication consists of a POM alone, and gets no SBOM.
 *
 * ## Naming the modules of this build
 *
 * The plugin lists a module of this build under the name of its Gradle project, with
 * neither a license nor a package URL — `client` rather than `io.spine:spine-client`.
 * [PublicationSbomTask] renames it after the publication of the module. The coordinates
 * are collected once all projects are evaluated, when the publications no longer change,
 * and handed to the tasks as plain values — so that no task reads the state of another
 * project while it runs.
 */
internal object PublicationSbom {

    /**
     * The name of the task writing the published SBOM of a JVM module.
     *
     * The task of a Kotlin Multiplatform target is named by [taskNameFor].
     */
    const val taskName = "publicationSbom"

    /**
     * The name of the SPDX target, and of the file it writes, for a JVM module.
     */
    private const val moduleUnit = "publication"

    /**
     * The target under which a JVM module looks up the modules it depends on.
     *
     * A JVM module resolves a multiplatform sibling to the artifact of its JVM target.
     */
    private const val jvmTarget = "jvm"

    /**
     * The organization supplying the published artifacts, as SPDX writes one.
     *
     * Matches the `Implementation-Vendor` in the manifest of each published JAR,
     * which `write-manifest.gradle.kts` sets.
     */
    private const val spineSupplier = "Organization: CodeMatters, Lda."

    /**
     * The commit recorded in the SBOM of a local build.
     *
     * GitHub Actions supplies `GITHUB_SHA` to every workflow, so a published SBOM names
     * the actual commit. A fixed value keeps the inputs of the SBOM task of a local build
     * stable, so the task stays up to date from one commit to the next.
     */
    private const val unknownCommit = "unknown"

    /**
     * Returns the name of the task writing the published SBOM of the given
     * Kotlin Multiplatform [target].
     */
    fun taskNameFor(target: String): String =
        target + taskName.replaceFirstChar { it.titlecase(Locale.ROOT) }

    /**
     * Registers the tasks writing the SBOMs of the given [published] projects, and adds
     * the SBOMs to their publications.
     *
     * [host] is the project in which `spinePublishing { }` was opened.
     *
     * Whether a project is a JVM or a multiplatform module is known only once it is
     * evaluated, so the registration waits until then. It is idempotent, because
     * `spinePublishing { }` may reach one project twice, as the documentation of
     * `getOrRegister` in `PublicationChecksums.kt` explains.
     */
    fun registerTasks(host: Project, published: Set<Project>) {
        published.forEach { project ->
            project.afterEvaluate { registerUnits() }
        }
        host.rootProject.handOverCoordinatesOnceEvaluated()
    }

    /**
     * Registers the SBOMs of this project, now that its plugins are known.
     */
    private fun Project.registerUnits() {
        val kotlin = extensions.findByType(KotlinMultiplatformExtension::class.java)
        when {
            kotlin != null -> kotlin.targets
                .matching { it.platformType != KotlinPlatformType.common }
                .configureEach { this@registerUnits.registerTargetUnit(this) }

            pluginManager.hasPlugin("java") -> registerSbom(
                SbomUnit(
                    name = moduleUnit,
                    configuration = "runtimeClasspath",
                    platform = jvmTarget,
                    taskName = taskName,
                    publishedWith = { !it.isPluginMarker }
                )
            )

            else -> logger.info(
                "No SBOM for `$path`: it is neither a JVM nor a Kotlin Multiplatform module."
            )
        }
    }

    /**
     * Registers the SBOM of the given Kotlin Multiplatform [target] of this project.
     *
     * A target without a `main` compilation gets none. An Android target is one: it
     * compiles per build variant, and publishes a publication per variant.
     */
    private fun Project.registerTargetUnit(target: KotlinTarget) {
        val main = target.compilations.findByName("main")
        if (main == null) {
            logger.info(
                "No SBOM for the `${target.name}` target of `$path`: it has no `main`" +
                        " compilation."
            )
            return
        }
        val targetName = target.name
        registerSbom(
            SbomUnit(
                name = targetName,
                configuration = main.runtimeDependencyConfigurationName
                    ?: main.compileDependencyConfigurationName,
                platform = targetName,
                taskName = taskNameFor(targetName),
                publishedWith = { it.name == targetName }
            )
        )
    }

    /**
     * Configures the SPDX Gradle Plugin to describe the given [unit], registers the task
     * completing its SBOM, and adds the SBOM to the publications of the unit.
     *
     * The configuration of the plugin does not depend on the coordinates of the artifact,
     * which are final only after the publications are set up: [PublicationSbomTask] puts
     * them into the document instead.
     */
    private fun Project.registerSbom(unit: SbomUnit) {
        if (tasks.names.contains(unit.taskName)) {
            return
        }
        val repository = DocumentationSettings.repoUrl(this)
        val commit = providers.environmentVariable("GITHUB_SHA").orElse(unknownCommit)
        pluginManager.apply(SpdxSbomPlugin::class.java)
        val spdx = extensions.getByType(SpdxSbomExtension::class.java)
        spdx.onlyUseLocalLicenses.set(true)
        spdx.targets.create(unit.name) {
            configurations.set(listOf(unit.configuration))
            document {
                creator.set(spineSupplier)
                packageSupplier.set(spineSupplier)
            }
            scm {
                uri.set(repository)
                revision.set(commit)
            }
        }
        val generated = tasks.named(
            "spdxSbomFor${unit.name.replaceFirstChar { it.titlecase(Locale.ROOT) }}",
            SpdxSbomTask::class.java
        ) {
            taskExtension.set(NoAssertionForLocalRepositories())
        }
        val projectPath = path
        val output = layout.buildDirectory.file("sbom/${unit.name}.spdx.json")
        val sbom = tasks.register(unit.taskName, PublicationSbomTask::class.java) {
            group = SpineTaskGroup.name
            description = "Writes the SBOM published with the `${unit.name}` artifact" +
                    " of `$projectPath`"
            source.set(generated.flatMap { it.outputFile })
            modulePath.set(projectPath)
            platform.set(unit.platform)
            outputFile.set(output)
        }
        pluginManager.withPlugin("maven-publish") {
            extensions.getByType(PublishingExtension::class.java).publications
                .withType(MavenPublication::class.java)
                .matching { unit.publishedWith(it) }
                .configureEach {
                    artifact(sbom.flatMap { it.outputFile }) {
                        extension = "spdx.json"
                    }
                }
        }
    }

    /**
     * Hands the coordinates of the Maven publications of this build to the SBOM tasks,
     * once all projects are evaluated.
     *
     * Registered once per build, on the root project. The values are plain, so no task
     * reads a provider, nor the state of another project, while it runs.
     */
    private fun Project.handOverCoordinatesOnceEvaluated() {
        val key = "io.spine.gradle.publish.sbomCoordinatesHandedOver"
        val properties = extensions.extraProperties
        if (properties.has(key)) {
            return
        }
        properties.set(key, true)
        val root = this
        gradle.projectsEvaluated {
            val published = root.collectPublishedCoordinates()
            root.allprojects.forEach { owner ->
                owner.tasks.withType(PublicationSbomTask::class.java).configureEach {
                    publishedCoordinates.set(published)
                    fallbackCoordinates.set(owner.presumedCoordinates())
                }
            }
        }
    }
}

/**
 * The coordinates of this publication, as `group:artifactId:version`.
 */
private val MavenPublication.coordinates: String
    get() = "$groupId:$artifactId:$version"

/**
 * Tells whether this publication is the marker of a Gradle plugin, which consists
 * of a POM pointing at the publication of the plugin.
 */
private val MavenPublication.isPluginMarker: Boolean
    get() = name.endsWith("PluginMarkerMaven")

/**
 * Returns the coordinates of every non-marker Maven publication of this build, keyed as
 * [PublicationSbomTask.publishedCoordinates] describes.
 */
private fun Project.collectPublishedCoordinates(): Map<String, String> = buildMap {
    allprojects.forEach { module ->
        val publications = module.mavenPublications().filterNot { it.isPluginMarker }
        publications.forEach { put(publicationKey(module.path, it.name), it.coordinates) }
        publications.singleOrNull()?.let { put(module.path, it.coordinates) }
    }
}

/**
 * Returns the coordinates `spinePublishing { }` gives this project, which name its SBOM
 * when the project has no single Maven publication to name it after.
 */
private fun Project.presumedCoordinates(): String = "$group:$artifactId:$version"

/**
 * An artifact described by one SBOM.
 */
private class SbomUnit(

    /** Names the SPDX target, and the file it writes. */
    val name: String,

    /** The configuration holding the dependencies of the artifact. */
    val configuration: String,

    /** The target under which the modules of this build are looked up. */
    val platform: String,

    /** The name of the task writing the published SBOM. */
    val taskName: String,

    /** Tells whether the SBOM is published with the given publication. */
    val publishedWith: (MavenPublication) -> Boolean
)

private val noAssertion = URI.create("NOASSERTION")

/**
 * Leaves the download location of a dependency resolved from a local repository
 * unasserted, keeping its package URL.
 *
 * The SPDX Gradle Plugin copies the URL of the repository a dependency comes from into
 * the download location of its package. SPDX 2 accepts no `file:` URL there, and since
 * version 0.12.0 the plugin fails the build on one — see
 * [spdx-gradle-plugin#192](https://github.com/spdx/spdx-gradle-plugin/issues/192).
 * Spine builds resolve modules from Maven Local, so any build using a locally published
 * one would fail. A local path means nothing to the reader of a published SBOM anyway.
 */
private class NoAssertionForLocalRepositories : DefaultSpdxSbomTaskExtension() {

    override fun mapRepoUri(original: URI?, moduleId: ModuleVersionIdentifier): URI? =
        if (original?.scheme == "file") noAssertion else original
}
