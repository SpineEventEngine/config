/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.gradle.publish

import io.spine.gradle.repo.Repository
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

/**
 * A handler for custom publications, which are declared under the [publications]
 * section of a module.
 *
 * Such publications should be treated differently than [StandardJavaPublicationHandler],
 * which is <em>created</em> for a module. Instead, since the publications are already declared,
 * this class only [assigns Maven coordinates][copyProjectAttributes].
 *
 * A module that declares custom publications must be specified in
 * the [SpinePublishing.modulesWithCustomPublishing] property.
 *
 * If a module with [publications] declared locally is not specified as one with custom publishing,
 * it may cause a name clash between an artifact produced by
 * the [standard][org.gradle.api.publish.maven.MavenPublication] publication, and custom ones.
 * To have both standard and custom publications, please specify custom artifact IDs or
 * classifiers for each custom publication.
 *
 * @see StandardJavaPublicationHandler
 */
internal class CustomPublicationHandler private constructor(
    project: Project,
    destinations: Set<Repository>
) : PublicationHandler(project, destinations) {

    override fun handlePublications() {
        project.publications.forEach {
            (it as MavenPublication).copyProjectAttributes()
        }
    }

    companion object : HandlerFactory<CustomPublicationHandler>() {
        override fun create(
            project: Project,
            destinations: Set<Repository>,
            vararg params: Any
        ): CustomPublicationHandler = CustomPublicationHandler(project, destinations)
    }
}
