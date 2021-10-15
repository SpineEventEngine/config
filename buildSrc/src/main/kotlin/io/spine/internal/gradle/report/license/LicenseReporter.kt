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

package io.spine.internal.gradle.report.license

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.LicenseReportExtension.ALL
import com.github.jk1.license.LicenseReportPlugin
import io.spine.internal.gradle.applyPlugin
import io.spine.internal.gradle.findTask
import java.io.File
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.the

/**
 * Generates the license report for all dependencies used in a Gradle project.
 *
 * Transitive dependencies are included.
 */
object LicenseReporter {

    /**
     * The name of the Gradle task which generates the reports for a specific Gradle project.
     */
    private const val projectTaskName = "generateLicenseReport"

    /**
     * The name of the Gradle task merging the license reports across all Gradle projects
     * in the repository into a single report file.
     */
    private const val repoTaskName = "mergeAllLicenseReports"

    fun generateReportIn(project: Project) {
        project.applyPlugin(LicenseReportPlugin::class.java)
        val reportOutputDir = project.buildDir.resolve(Config.relativePath)

        with(project.the<LicenseReportExtension>()) {
            outputDir = reportOutputDir.absolutePath
            excludeGroups = arrayOf("io.spine", "io.spine.tools", "io.spine.gcloud")
            configurations = ALL

            renderers = arrayOf(MarkdownReportRenderer(Config.outputFilename))
        }
    }

    fun mergeAllReports(project: Project) {
        val rootProject = project.rootProject
        val consolidateAllLicenseReports = rootProject.tasks.register(repoTaskName) {
            val consolidationTask = this
            val assembleTask = project.findTask<Task>("assemble")

            val sourceProjects: Iterable<Project> = sourceProjects(rootProject)
            sourceProjects.forEach {
                val perProjectTask = it.findTask<Task>(projectTaskName)
                consolidationTask.dependsOn(perProjectTask)
                perProjectTask.dependsOn(assembleTask)
            }

            doLast {
                mergeReports(sourceProjects, rootProject)
            }

            dependsOn(assembleTask)
        }
        project.findTask<Task>("build")
            .finalizedBy(consolidateAllLicenseReports)
    }

    /**
     * Determines the source projects for which the resulting report will be produced.
     */
    private fun Task.sourceProjects(rootProject: Project): Iterable<Project> {
        val targetProjects: Iterable<Project> = if (rootProject.subprojects.isEmpty()) {
            println("The license report will be produced for a single root project.")
            listOf(this.project)
        } else {
            println("The license report will be produced for all subprojects of a root project.")
            rootProject.subprojects
        }
        return targetProjects
    }

    /**
     * Merges the license reports from all [sourceProjects] into a single file under
     * the [rootProject]'s root directory.
     */
    private fun mergeReports(
        sourceProjects: Iterable<Project>,
        rootProject: Project
    ) {
        val paths = sourceProjects.map {
            "${it.buildDir}/${Config.relativePath}/${Config.outputFilename}"
        }

        println("Merging the license reports from the all projects.")
        val mergedContent = paths.joinToString("\n\n\n") { (File(it)).readText() }

        val output = File("${rootProject.rootDir}/${Config.outputFilename}")
        output.writeText(
            mergedContent
        )
    }
}
