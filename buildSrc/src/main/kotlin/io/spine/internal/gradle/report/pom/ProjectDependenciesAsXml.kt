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

package io.spine.internal.gradle.report.pom

import groovy.xml.MarkupBuilder
import java.io.Writer
import java.util.*
import kotlin.reflect.full.isSubclassOf
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.dependencies.AbstractExternalModuleDependency
import org.gradle.kotlin.dsl.withGroovyBuilder

/**
 * Dependencies of the project expressed as XML.
 *
 * Subprojects dependencies are included, transitive dependencies are not included.
 *
 * Example:
 *
 * ```
 *  <dependencies>
 *      <dependency>
 *          <groupId>io.spine</groupId>
 *          <artifactId>base</artifactId>
 *          <version>2.0.0-pre1</version>
 *      </dependency>
 *      ...
 *  </dependencies>
 * ```
 */
class ProjectDependenciesAsXml
private constructor(
    private val firstLevelDependencies: SortedSet<DependencyWithScope>
){

    companion object {

        fun of(project: Project): ProjectDependenciesAsXml {
            val deps = projectDependencies(project)
            return ProjectDependenciesAsXml(deps)
        }

        private fun projectDependencies(project: Project): SortedSet<DependencyWithScope> {
            val firstLevelDependencies = mutableSetOf<DependencyWithScope>()
            firstLevelDependencies.addAll(dependenciesFromAllConfigurations(project))

            project.subprojects.forEach { subproject ->
                val subprojectDeps = dependenciesFromAllConfigurations(subproject)
                firstLevelDependencies.addAll(subprojectDeps)
            }
            return firstLevelDependencies.toSortedSet()
        }

        private fun dependenciesFromAllConfigurations(project: Project): Set<DependencyWithScope> {
            val result = mutableSetOf<DependencyWithScope>()
            project.configurations.forEach { configuration ->
                if (configuration.isCanBeResolved) {
                    // Force configuration resolution.
                    configuration.resolvedConfiguration
                }
                configuration.dependencies.forEach {
                    if (isExternal(it)) {
                        val dependency = DependencyWithScope.of(it, configuration)
                        result.add(dependency)
                    }
                }
            }
            return result
        }

        private fun isExternal(dependency: Dependency): Boolean {
            return AbstractExternalModuleDependency::class.isSubclassOf(dependency.javaClass.kotlin)
        }
    }

    /**
     * Writes the dependencies using the specified writer.
     *
     * <p>Used writer will not be closed.
     */
    fun writeUsing(writer: Writer) {
        val xmlBuilder = MarkupBuilder(writer)
        xmlBuilder.withGroovyBuilder {
            "dependencies" {
                firstLevelDependencies.forEach { projectDep ->
                    val dependency = projectDep.dependency()
                    "dependency" {
                        "groupId" to dependency.group
                        "artifactId" to dependency.name
                        "version" to dependency.version
                        if(projectDep.hasDefinedScope()) {
                            "scope" to projectDep.scopeName()
                        }
                    }
                }
            }
        }
    }
}
