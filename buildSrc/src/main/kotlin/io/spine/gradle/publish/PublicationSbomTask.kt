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

import LicenseSettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the SBOM published with an artifact, naming the modules of this build by
 * the coordinates they are published under.
 *
 * The task rewrites the document that the SPDX Gradle Plugin writes for the artifact:
 *
 *  - the document is named after the artifact, and given a namespace derived from its
 *    coordinates, in place of the placeholder the plugin leaves there;
 *  - each package describing a module of this build — the artifact itself, and each
 *    sibling module it depends on — declares the [license][LicenseSettings.spdxId] of
 *    the modules of this build, is named `group:artifactId`, and gets the version and
 *    the package URL of the published artifact. The package URL replaces the external
 *    references the plugin wrote, if any. The plugin lists such a module under the name
 *    of its Gradle project, with neither a license nor a package URL.
 *
 * A module of this build is recognized by the source information the plugin gives it,
 * which ends with the path of the Gradle project in brackets; no other package has it.
 * A sibling without a single Maven publication to name it after — typically a module
 * bundled into the artifact of another one, such as a fat JAR — has no coordinates of
 * its own: it only gets the license, and keeps the name of its project.
 *
 * @see PublicationSbom
 */
@DisableCachingByDefault(because = "Rewrites a small document; caching it would not pay off.")
internal abstract class PublicationSbomTask : DefaultTask() {

    /**
     * The document written by the SPDX Gradle Plugin.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val source: RegularFileProperty

    /**
     * The path of the project whose artifact the SBOM describes.
     */
    @get:Input
    abstract val modulePath: Property<String>

    /**
     * The name of the target whose artifact the SBOM describes, such as `jvm`.
     *
     * A multiplatform module publishes an artifact per target, and a dependency on it
     * resolves to the artifact of the target being built. So a module of this build —
     * the described one included — is looked up by its publication of this target first.
     */
    @get:Input
    abstract val platform: Property<String>

    /**
     * The coordinates of the non-marker Maven publications of this build, as
     * `group:artifactId:version`.
     *
     * Each publication is keyed by [publicationKey]. The only non-marker publication of
     * a project is also keyed by the path of that project alone.
     */
    @get:Input
    abstract val publishedCoordinates: MapProperty<String, String>

    /**
     * The coordinates naming the described artifact when its project has no single
     * Maven publication to name it after, as `group:artifactId:version`.
     */
    @get:Input
    abstract val fallbackCoordinates: Property<String>

    /**
     * The SBOM to publish.
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeSbom() {
        val mapper = ObjectMapper()
        val file = source.get().asFile
        val document = mapper.readTree(file) as? ObjectNode
            ?: error("`$file` is not an SPDX JSON document.")
        val published = publishedCoordinates.get()
        val own = ownCoordinates(published)
        document.put("name", own.artifactId)
        document.put("documentNamespace", own.namespace)
        document["packages"]
            ?.filterIsInstance<ObjectNode>()
            ?.forEach { describeModule(pkg = it, own = own, published = published) }
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.get().asFile, document)
    }

    /**
     * Returns the coordinates of the described artifact, falling back to the
     * [fallbackCoordinates] with a warning.
     */
    private fun ownCoordinates(published: Map<String, String>): Coordinates {
        val path = modulePath.get()
        published.lookUp(path, platform.get())?.let { return it }
        val fallback = Coordinates.parse(fallbackCoordinates.get())
        logger.warn(
            "The SBOM of `$path` is named `${fallback.artifactId}`, after its project:" +
                    " the project has no single Maven publication to name it after."
        )
        return fallback
    }

    /**
     * Describes the given package as the module of this build it stands for, if it stands
     * for one: by the license of this build and, if the module has a single Maven
     * publication to name it after, by the coordinates of that publication.
     *
     * Any other package is left as the plugin wrote it.
     */
    private fun describeModule(
        pkg: ObjectNode,
        own: Coordinates,
        published: Map<String, String>
    ) {
        val path = pkg["sourceInfo"]?.asText()?.let(::projectPathIn) ?: return
        pkg.put("licenseDeclared", LicenseSettings.spdxId)
        val module = if (path == modulePath.get()) own else published.lookUp(path, platform.get())
        if (module == null) {
            logger.info(
                "The SBOM of `${modulePath.get()}` lists the project `$path` under its" +
                        " project name: the project has no single Maven publication" +
                        " to name it after."
            )
            return
        }
        pkg.put("name", module.name)
        pkg.put("versionInfo", module.version)
        pkg.putArray("externalRefs").addObject().apply {
            put("referenceCategory", "PACKAGE-MANAGER")
            put("referenceLocator", module.purl)
            put("referenceType", "purl")
        }
    }
}

/**
 * Returns the key under which [PublicationSbomTask.publishedCoordinates] holds the
 * given [publication] of the project with the given path.
 */
internal fun publicationKey(projectPath: String, publication: String): String =
    "$projectPath#$publication"

/**
 * Returns the coordinates of the project with the given [path], preferring its
 * publication of the given [platform], or `null` if the project has neither that
 * publication nor a single one.
 */
private fun Map<String, String>.lookUp(path: String, platform: String): Coordinates? =
    (get(publicationKey(path, platform)) ?: get(path))?.let(Coordinates::parse)

/**
 * Matches the path of a Gradle project at the end of the source information that the
 * SPDX Gradle Plugin writes for a module of this build: `git+<uri>@<revision>#<name>[<path>]`.
 */
private val projectPathSuffix = Regex("""\[(:[^\[\]]*)]$""")

/**
 * Returns the path of the Gradle project named in the given source information,
 * or `null` if the information does not name one.
 */
private fun projectPathIn(sourceInfo: String): String? =
    projectPathSuffix.find(sourceInfo)?.groupValues?.get(1)

/**
 * The Maven coordinates of a published artifact.
 */
private data class Coordinates(val group: String, val artifactId: String, val version: String) {

    /** The name of the SPDX package describing the artifact. */
    val name: String get() = "$group:$artifactId"

    /** The package URL of the artifact. */
    val purl: String get() = "pkg:maven/$group/$artifactId@$version"

    /** The namespace of the SPDX document describing the artifact. */
    val namespace: String get() = "https://spine.io/spdxdocs/$group/$artifactId/$version"

    companion object {

        /** The number of parts in `group:artifactId:version`. */
        private const val partCount = 3

        /**
         * Parses coordinates written as `group:artifactId:version`.
         */
        fun parse(value: String): Coordinates {
            val parts = value.split(':')
            require(parts.size == partCount) {
                "Expected Maven coordinates as `group:artifactId:version`, got `$value`."
            }
            val (group, artifactId, version) = parts
            return Coordinates(group = group, artifactId = artifactId, version = version)
        }
    }
}
