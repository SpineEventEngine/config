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

package io.spine.gradle.internal

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty

class Publish : Plugin<Project> {

    companion object {

        const val taskName = "publish"

        private const val ARCHIVES = "archives"
    }

    override fun apply(project: Project) {
        val extension = PublishExtension.create(project)
        project.extensions.add(PublishExtension::class.java, "publishing", extension)

        val publish = project.tasks.create(taskName)

        project.afterEvaluate {
            extension.projectsToPublish
                .get()
                .map { project.project(it) }
                .forEach { p ->
                    apply(plugin = "maven-publish")

                    logger.debug("Applying `maven-publish` plugin to ${name}.")

                    p.setUpdefaultArtifacts()

                    val publishingExtension = extensions.getByType(PublishingExtension::class)
                    val action = {
                        publishingExtension.createMavenPublication(p, extension)
                    }
                    if (state.executed) {
                        action()
                    } else {
                        afterEvaluate { action() }
                    }

                    publishingExtension.setUpRepositories(p, extension)
                    publish.dependsOn(getTasksByName(taskName, false))
                }
        }
    }

    private fun Project.setUpdefaultArtifacts() {
        val sourceJar = tasks.getByName("sourceJar", Jar::class)
        val testOutputJar = tasks.getByName("testOutputJar", Jar::class)
        val javadocJar = tasks.getByName("javadocJar", Jar::class)

        artifacts {
            add(ARCHIVES, sourceJar)
            add(ARCHIVES, testOutputJar)
            add(ARCHIVES, javadocJar)
        }
    }

    private fun PublishingExtension.createMavenPublication(project: Project,
                                                           extension: PublishExtension) {
        val artifactIdForPublishing = if(extension.spinePrefix.get()) {
            "spine-${project.name}"
        } else {
            project.name
        }
        publications {
            create("mavenJava", MavenPublication::class.java) {
                groupId = project.group.toString()
                artifactId = artifactIdForPublishing
                version = project.version.toString()

                from(project.components.getAt("java"))

                setArtifacts(project.configurations.getAt(ARCHIVES).allArtifacts)
            }
        }
    }

    private fun PublishingExtension.setUpRepositories(
        project: Project,
        extension: PublishExtension
    ) {
        val snapshots = project.version
            .toString()
            .matches(Regex(".+[-.]SNAPSHOT([+.]\\d+)?"))
        repositories {
            extension.targetRepositories.get().forEach { repo ->
                maven {
                    initialize(repo, project, snapshots)
                }
            }
        }
    }

    private fun MavenArtifactRepository.initialize(repo: Repository,
                                                   project: Project,
                                                   snapshots: Boolean) {
        val publicRepo = if(snapshots) {
            repo.snapshots
        } else {
            repo.releases
        }
        // Special treatment for CloudRepo URL.
        // Reading is performed via public repositories, and publishing via
        // private ones that differ in the `/public` infix.
        url = project.uri(publicRepo.replace("/public", ""))
        val creds = repo.credentials(project.rootProject)
        credentials {
            username = creds.username
            password = creds.password
        }
    }
}

class PublishExtension(
    val projectsToPublish: SetProperty<String>,
    val targetRepositories: SetProperty<Repository>,
    val spinePrefix: Property<Boolean>
) {

    internal companion object {
        fun create(project: Project): PublishExtension {
            val factory = project.objects
            return PublishExtension(
                projectsToPublish = factory.setProperty(String::class),
                targetRepositories = factory.setProperty(Repository::class),
                spinePrefix = factory.property(Boolean::class)
            )
        }
    }

    init {
        spinePrefix.convention(true)
    }
}
