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

import io.spine.gradle.SpineTaskGroup
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.TaskProvider

/**
 * Writes a manifest of the artifacts published by this build, so that GitHub
 * Actions can attest their build provenance.
 *
 * ## Why Gradle computes the manifest
 *
 * The `actions/attest` action identifies an attested artifact by its digest and
 * by a name, both of which it takes **verbatim** from the manifest — it never
 * re-reads the artifacts. So the manifest must name each artifact exactly as it
 * is published, and cover all of them: an omission silently narrows a signed
 * claim, and a wrong digest signs a claim about the wrong bytes.
 *
 * Neither is obtainable by globbing the build directory. The name of an artifact
 * on disk is derived from the Gradle project name, while the published name uses
 * [the artifact ID][SpinePublishing.artifactPrefix] — `client-2.0.0.jar` is
 * published as `spine-client-2.0.0.jar`. The POM and the Gradle module metadata
 * are not in `build/libs` at all; they are generated under `build/publications`
 * under the fixed names `pom-default.xml` and `module.json`. Only the publication
 * knows how these map onto published names.
 *
 * ## Why a task per project
 *
 * A task of the root project may not reach into the state of another project.
 * As with [the POM report][io.spine.gradle.report.pom.PomGenerator], a
 * [collector task][registerCollectorIn] is therefore registered in every
 * published project, describing only its own publications, and
 * [an aggregator][registerAggregatorIn] in the root project merges their output.
 *
 * Neither task declares inputs or outputs, on purpose: both always run, so the
 * manifest cannot describe a previous state of the build.
 */
internal object PublicationChecksums {

    /**
     * The name of the per-project task registered by [registerCollectorIn].
     */
    const val collectorTaskName = "collectPublicationChecksums"

    /**
     * The name of the root-project task registered by [registerAggregatorIn].
     */
    const val aggregatorTaskName = "publicationChecksums"

    private const val collectorPath = "attestation/checksums.txt"

    private const val aggregatePath = "attestation/subject-checksums.txt"

    /**
     * Separates a digest from the name of its subject, as `sha256sum` writes it.
     *
     * The action reads the name as everything after the first space, dropping one
     * further `*` or space, so the two-space form leaves the name intact.
     */
    private const val digestSeparator = "  "

    /**
     * Registers the [collector][registerCollectorIn] tasks in the given
     * [published] projects, and the [aggregator][registerAggregatorIn] task in
     * the [root] project.
     */
    fun registerTasks(root: Project, published: Set<Project>) {
        val collectors = published.map { registerCollectorIn(it) }
        registerAggregatorIn(root, published, collectors)
    }

    /**
     * Registers the [collectorTaskName] task in the given [project].
     *
     * The task depends on everything that produces a published file: the
     * artifacts of the publications of this project, and the tasks generating
     * the POM and the Gradle module metadata.
     *
     * The task orders itself after `clean` — Gradle does not order the two
     * otherwise, so in a `gradle clean build` invocation a late-running `clean`
     * could delete a freshly written manifest.
     */
    private fun registerCollectorIn(project: Project): TaskProvider<Task> =
        project.tasks.register(collectorTaskName) {
            group = SpineTaskGroup.name
            description = "Computes the digests of the artifacts published by " +
                    "the `${project.name}` project"
            mustRunAfter(project.tasks.matching { it.name == "clean" })
            dependsOn(project.provider {
                project.mavenPublications().flatMap { publication ->
                    publication.artifacts.map { it.buildDependencies }
                }
            })
            dependsOn(project.tasks.withType(GenerateMavenPom::class.java))
            dependsOn(project.tasks.withType(GenerateModuleMetadata::class.java))
            doLast {
                val file = collectorOutput(project)
                file.parentFile.mkdirs()
                file.writeText(serialize(project.publishedArtifacts()))
            }
        }

    /**
     * Registers the [aggregatorTaskName] task in the given [root] project.
     *
     * The task merges the manifests written by the [collectors] into a single
     * file in the `sha256sum` format, which `actions/attest` accepts as its
     * `subject-checksums` input, ordered by subject name so that two runs of the
     * same build produce a file that can be compared line by line.
     *
     * A manifest missing at merge time fails the task. Every collector writes
     * unconditionally, so an absent one means the subject set is incomplete —
     * and an attestation that silently covers less than it appears to is worse
     * than none.
     *
     * The task orders itself after `clean` for the reason given in
     * [registerCollectorIn]: it writes under the build directory that `clean`
     * removes.
     */
    private fun registerAggregatorIn(
        root: Project,
        published: Set<Project>,
        collectors: List<TaskProvider<Task>>
    ): TaskProvider<Task> =
        root.tasks.register(aggregatorTaskName) {
            group = SpineTaskGroup.name
            description = "Writes the digests of all published artifacts for attestation"
            dependsOn(collectors)
            mustRunAfter(root.tasks.matching { it.name == "clean" })
            doLast {
                val merged = published
                    .map { collectorOutput(it) }
                    .onEach {
                        check(it.exists()) {
                            "No checksum manifest at `$it`." +
                                    " The attestation would omit a published module."
                        }
                    }
                    .flatMap { it.readLines() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sortedBy { it.substringAfter(digestSeparator) }
                val file = root.layout.buildDirectory.file(aggregatePath).get().asFile
                file.parentFile.mkdirs()
                file.writeText(merged.joinToString(separator = "\n", postfix = "\n"))
                logger.lifecycle(
                    "Wrote ${merged.size} attestation subject(s) to `$file`."
                )
            }
        }

    private fun collectorOutput(project: Project): File =
        project.layout.buildDirectory.file(collectorPath).get().asFile

    private fun serialize(artifacts: Map<String, String>): String =
        artifacts.entries
            .sortedBy { it.key }
            .joinToString(separator = "\n", postfix = "\n") { (name, digest) ->
                "$digest$digestSeparator$name"
            }
}

/**
 * Returns the Maven publications of this project, or an empty collection if
 * the project does not publish.
 */
private fun Project.mavenPublications(): Collection<MavenPublication> {
    val publishing = extensions.findByType(PublishingExtension::class.java)
        ?: return emptyList()
    return publishing.publications.withType(MavenPublication::class.java)
}

/**
 * Returns the digest of every file this project publishes, keyed by the name
 * under which the file is published.
 */
private fun Project.publishedArtifacts(): Map<String, String> {
    val result = mutableMapOf<String, String>()
    mavenPublications().forEach { publication ->
        val base = "${publication.artifactId}-${publication.version}"
        publication.artifacts.forEach { artifact ->
            val classifier = artifact.classifier?.takeIf { it.isNotEmpty() }
            val suffix = classifier?.let { "-$it" } ?: ""
            result["$base$suffix.${artifact.extension}"] = artifact.file.sha256()
        }
        pomFileOf(publication)?.let { result["$base.pom"] = it.sha256() }
        moduleFileOf(publication)?.let { result["$base.module"] = it.sha256() }
    }
    return result
}

/**
 * Returns the generated POM of the given [publication], or `null` if the
 * generating task did not run.
 */
private fun Project.pomFileOf(publication: MavenPublication): File? {
    val task = tasks.findByName("generatePomFileFor${publication.taskSuffix()}")
    return (task as GenerateMavenPom?)?.destination?.takeIf { it.exists() }
}

/**
 * Returns the generated Gradle module metadata of the given [publication], or
 * `null` if the metadata is disabled or the generating task did not run.
 */
private fun Project.moduleFileOf(publication: MavenPublication): File? {
    val task = tasks.findByName("generateMetadataFileFor${publication.taskSuffix()}")
    return (task as GenerateModuleMetadata?)
        ?.outputFile?.get()?.asFile?.takeIf { it.exists() }
}

/**
 * Returns the part of the name of a `generate...` task that identifies
 * this publication.
 */
private fun MavenPublication.taskSuffix(): String =
    "${name.replaceFirstChar { it.titlecase(Locale.ROOT) }}Publication"

/**
 * Returns the SHA-256 digest of this file as a lowercase hexadecimal string.
 *
 * The file is streamed, as published artifacts may be large.
 */
private fun File.sha256(): String {
    check(exists()) {
        "Cannot attest a missing artifact file: `$this`."
    }
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) {
                break
            }
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
