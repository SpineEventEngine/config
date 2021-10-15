/*
 * Copyright 2021, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.internal.gradle.publish

import io.spine.internal.gradle.Repository
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty

/**
 * The extension for configuring the `Publish` plugin.
 */
class PublishExtension
private constructor(
    val projectsToPublish: SetProperty<String>,
    val targetRepositories: SetProperty<Repository>,
    val spinePrefix: Property<Boolean>
) {

    internal companion object {

        /** The name of the extension as it appears in a Gradle project. */
        const val name = "spinePublishing"

        /** The prefix to be used before the project name if [spinePrefix] is set to `true`. */
        const val artifactPrefix = "spine-"

        /**
         * Creates a new instance of the extension and adds it to the given project.
         */
        fun createIn(project: Project): PublishExtension {
            val factory = project.objects
            val extension = PublishExtension(
                projectsToPublish = factory.setProperty(String::class),
                targetRepositories = factory.setProperty(Repository::class),
                spinePrefix = factory.property(Boolean::class)
            )
            project.extensions.add(PublishExtension::class.java, name, extension)
            return extension
        }

        /**
         * Obtains an artifact ID of the given project.
         *
         * If the project has a [PublishExtension] installed, then it is used for
         * [obtaining][PublishExtension.artifactId] the artifact ID.
         * Otherwise, the project name is returned.
         *
         * @see artifactId
         */
        fun artifactIdIn(project: Project): String {
            val publishExtension = project.extensions.findByType(PublishExtension::class.java)
            val result = publishExtension?.artifactId(project) ?: project.name
            return result
        }
    }

    /**
     * Obtains an artifact ID of the given project, taking into account the value of
     * the [spinePrefix] property. If the property is set to `true`, [artifactPrefix] will
     * be used before the project name. Otherwise, just the name of the project will be
     * used as the artifact ID.
     */
    fun artifactId(project: Project): String =
        if (spinePrefix.get()) {
            "$artifactPrefix${project.name}"
        } else {
            project.name
        }

    /**
     * The project to be published _instead_ of [projectsToPublish].
     *
     * If set, [projectsToPublish] will be ignored.
     */
    private var soloProject: Project? = null

    init {
        spinePrefix.convention(true)
    }

    /**
     * Instructs to publish the passed project _instead_ of [projectsToPublish].
     *
     * @see projectsToPublish
     */
    fun publish(project: Project) {
        soloProject = project
    }

    /**
     * Returns `true` if the extension is configured to publish only one project.
     * `false`, otherwise.
     *
     * @see publish
     * @see projectsToPublish
     */
    fun singleProject(): Boolean = soloProject != null
}
