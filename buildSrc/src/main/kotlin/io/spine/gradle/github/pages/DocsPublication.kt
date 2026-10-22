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

package io.spine.gradle.github.pages

import io.spine.gradle.VersionComparator
import io.spine.gradle.git.Repository
import java.io.File
import java.nio.file.Path
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger

/**
 * Publishes the API documentation of the given [project] to GitHub Pages.
 *
 * The documentation in each format is copied into a clone of the `gh-pages` branch
 * and committed there, and the commits are pushed.
 */
internal fun Task.publishToGitHubPages(project: Project) {
    val plugin = project.plugins.getPlugin(PublishApiDocs::class.java)

    SshKey(plugin.rootFolder, logger).register()

    val repository = Repository.forPublishingDocumentation(project)

    val javadoc = JavadocPublication(project, plugin.javadocOutputFolder, repository, logger)
    val html = HtmlPublication(project, plugin.htmlOutputFolder, repository, logger)

    repository.use {
        javadoc.publish()
        html.publish()
        repository.push()
    }
}

/**
 * Publishes the documentation of a [project] in one format to the clone of
 * the [repository] serving GitHub Pages.
 *
 * The documentation goes to the folder of its version, and also replaces the most
 * recent documentation of the project, unless a newer version is already published.
 */
private abstract class DocsPublication(
    private val project: Project,
    private val docsSourceFolder: Path,
    private val repository: Repository,
    private val logger: Logger
) {

    /**
     * The folder under the repository's root(`/`) for storing documentation.
     *
     * The value should not contain any leading or trailing file separators.
     *
     * The absolute path to the project's documentation is made by appending its
     * name to the end, making `/docsDestinationFolder/project.name`.
     */
    protected abstract val docsDestinationFolder: String

    /**
     * The name of the format of the documentation to publish.
     *
     * This name will appear in logs as part of a message.
     */
    protected abstract val formatName: String

    private val mostRecentFolder by lazy {
        File("${repository.location}/${docsDestinationFolder}/${project.name}")
    }

    /**
     * The folder holding the documentation of each published version in a subfolder
     * named after the version.
     */
    private val versionsFolder by lazy {
        File(mostRecentFolder, "v")
    }

    private fun log(message: () -> String) {
        if (logger.isDebugEnabled) {
            logger.debug(message())
        }
    }

    /**
     * Copies the documentation into the repository and commits it.
     */
    fun publish() {
        val module = project.name
        log { "Publishing the `$formatName` documentation of the module `$module`." }

        val documentation = replaceMostRecentDocs()
        copyIntoVersionDir(documentation)

        val version = project.version
        val commitMessage =
            "Publish the `$formatName` documentation of the module" +
                    " `$module` for the version `$version`."
        repository.commitAllChanges(commitMessage)

        log { "Published the `$formatName` documentation of the module `$module`." }
    }

    /**
     * Replaces the most recent documentation with the one of this version, unless
     * the documentation of a newer version is already published.
     *
     * The runs of `publish-api-docs.yml` are serialized, but not necessarily in the
     * order of their commits, and a release of an older line may come after a newer
     * one. Either way, the most recent documentation stays that of the newest version.
     */
    private fun replaceMostRecentDocs(): ConfigurableFileCollection {
        val generatedDocs = project.files(docsSourceFolder)

        val version = project.version.toString()
        val newer = publishedVersions().newestAbove(version)
        if (newer != null) {
            logger.lifecycle(
                "The most recent `$formatName` documentation of `${project.name}` stays" +
                        " that of `$newer`, which is newer than `$version`."
            )
            return generatedDocs
        }

        log { "Replacing the most recent `$formatName` documentation in `$mostRecentFolder`." }
        copyDocs(generatedDocs, mostRecentFolder)

        return generatedDocs
    }

    /**
     * Obtains the versions whose documentation is published, as named by the folders
     * under `v/`.
     */
    private fun publishedVersions(): List<String> =
        versionsFolder.listFiles(File::isDirectory)?.map { it.name } ?: emptyList()

    private fun copyDocs(source: FileCollection, destination: File) {
        destination.mkdir()
        project.copy {
            from(source)
            into(destination)
        }
    }

    private fun copyIntoVersionDir(generatedDocs: ConfigurableFileCollection) {
        val versionedDocDir = File(versionsFolder, "${project.version}")

        log { "Storing the new version of `$formatName` documentation in `${versionedDocDir}`." }
        copyDocs(generatedDocs, versionedDocDir)
    }
}

private class JavadocPublication(
    project: Project,
    docsSourceFolder: Path,
    repository: Repository,
    logger: Logger
) : DocsPublication(project, docsSourceFolder, repository, logger) {

    override val docsDestinationFolder: String
        get() = "javadoc"
    override val formatName: String
        get() = "javadoc"
}

private class HtmlPublication(
    project: Project,
    docsSourceFolder: Path,
    repository: Repository,
    logger: Logger
) : DocsPublication(project, docsSourceFolder, repository, logger) {

    override val docsDestinationFolder: String
        get() = "reference"
    override val formatName: String
        get() = "html"
}

/**
 * Returns the newest of these versions if it is newer than the given [version],
 * or `null` otherwise.
 *
 * The versions are compared by [VersionComparator], so that `2.10.0` is newer than
 * `2.9.0`, and a release is newer than the snapshots leading to it.
 */
internal fun Iterable<String>.newestAbove(version: String): String? =
    maxWithOrNull(VersionComparator)?.takeIf { VersionComparator.compare(it, version) > 0 }
