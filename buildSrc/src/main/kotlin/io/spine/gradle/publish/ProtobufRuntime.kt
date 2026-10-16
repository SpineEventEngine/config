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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import groovy.util.Node
import io.spine.dependency.lib.Protobuf
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named

/**
 * Leaves the Protobuf runtime ([Protobuf.libs]) out of the fat JAR built by
 * the `shadowJar` task of this project, declaring it as a `runtime` dependency of
 * the published artifacts instead.
 *
 * A bundled copy of the runtime cannot be relocated, as the runtime depends on
 * the names of its classes. Left unrelocated, it shadows the Protobuf runtime
 * a consumer resolves for the same classpath, and no `force(...)` can replace it:
 * the copy is bytecode inside another artifact, not a dependency. Should the copy
 * be older than the code generated for the consumer, loading that code fails with
 * `ProtobufRuntimeVersionException`. Declared as a dependency, the runtime takes part
 * in the dependency resolution of the consumer, so the version the consumer
 * resolves wins.
 *
 * The libraries are declared:
 *  1. in the `protobufRuntime` configuration, which
 *     the `shadowRuntimeElements` variant of the Shadow plugin extends, so that the
 *     Gradle module metadata and the POM of a publication of the shadowed variant,
 *     such as the one of a Gradle plugin, list them;
 *  2. in the POM of the given [publication], which publishes the fat JAR with
 *     `artifact(...)` and so receives no dependencies from Gradle.
 *
 * The `shadow` configuration is not used for the purpose: Shadow writes its files into
 * the `Class-Path` attribute of the JAR manifest. Such entries are relative to the JAR
 * and carry the version of the build in the file names, so in a directory holding
 * the JAR along with other Protobuf JARs, they would put the runtime of the build
 * first again.
 *
 * Each library is declared non-transitively. Of their own dependencies, `protobuf-java`
 * is declared too, and the rest stays where it was: bundled into the fat JAR (Guava, Gson)
 * or provided by its runtime (the Kotlin runtime). So consumers get the same classes
 * as before, except that those of the Protobuf runtime come from genuine artifacts.
 *
 * The libraries are excluded by the dependency filter of the `shadowJar` task rather
 * than by the paths of their entries, so that everything they contribute, classes and
 * resources alike, is left out, and the SBOM published with the fat JAR does not
 * describe them as [bundled][SbomContent.bundled].
 *
 * Usage, after the publication of the fat JAR is created:
 * ```kotlin
 * unbundleProtobufRuntime()                       // For `uber-jar-module`.
 * unbundleProtobufRuntime(publication = "cliFatJar")
 * ```
 *
 * @param publication The name of the publication of the fat JAR. Defaults to
 *   `fatJar`, the publication created by `uber-jar-module`.
 */
fun Project.unbundleProtobufRuntime(publication: String = "fatJar") {
    val protobufRuntime = configurations.create(PROTOBUF_RUNTIME) {
        description = "The Protobuf runtime, which the published artifacts declare" +
                " rather than bundle."
        isCanBeConsumed = false
        isCanBeResolved = false
    }
    Protobuf.libs.forEach {
        dependencies.add(protobufRuntime.name, it) {
            isTransitive = false
        }
    }
    configurations.named(SHADOW_RUNTIME_ELEMENTS) {
        extendsFrom(protobufRuntime)
    }
    tasks.named<ShadowJar>("shadowJar") {
        dependencies {
            exclude { "${it.moduleGroup}:${it.moduleName}" in protobufRuntimeModules }
        }
    }
    extensions.getByType<PublishingExtension>()
        .publications
        .named<MavenPublication>(publication) {
            declareRuntimeDependencies(Protobuf.libs)
        }
}

/**
 * The name of the configuration holding the Protobuf runtime, which the fat JAR
 * declares rather than bundles.
 */
private const val PROTOBUF_RUNTIME = "protobufRuntime"

/**
 * The name of the configuration of the Shadow plugin, from which the Gradle module
 * metadata of the shadowed variant is published.
 */
private const val SHADOW_RUNTIME_ELEMENTS = "shadowRuntimeElements"

/**
 * The `group:name` coordinates of the Protobuf runtime libraries.
 */
private val protobufRuntimeModules: Set<String> =
    Protobuf.libs.mapTo(mutableSetOf()) { it.substringBeforeLast(':') }

/**
 * Declares the dependencies on the given `group:name:version` [coordinates]
 * in the POM of this publication, in the `runtime` scope and without
 * transitive dependencies.
 *
 * The dependencies are added to the `dependencies` element of the POM,
 * which is created if the POM has none.
 */
private fun MavenPublication.declareRuntimeDependencies(coordinates: List<String>) {
    pom.withXml {
        val root = asNode()
        val dependencies = root.children()
            .filterIsInstance<Node>()
            .firstOrNull { it.localName == "dependencies" }
            ?: root.appendNode("dependencies")
        coordinates.forEach {
            // Not `name` and `version`, which would hide those of the publication.
            val (libGroup, libName, libVersion) = it.split(':')
            with(dependencies.appendNode("dependency")) {
                appendNode("groupId", libGroup)
                appendNode("artifactId", libName)
                appendNode("version", libVersion)
                appendNode("scope", "runtime")
                with(appendNode("exclusions").appendNode("exclusion")) {
                    appendNode("groupId", "*")
                    appendNode("artifactId", "*")
                }
            }
        }
    }
}

/**
 * The name of this node without its namespace.
 *
 * The name is a `QName` for a node parsed with namespaces, as the nodes of a POM
 * are, which renders as `{namespace}localName`.
 */
private val Node.localName: String
    get() = name().toString().substringAfterLast('}')
