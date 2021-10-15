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
 *
 * Use `generateLicenseReport` task to trigger the generation.
 */
object LicenseReporter {

    fun applyToSingle(project: Project) {
        project.applyPlugin(LicenseReportPlugin::class.java)
        val reportOutputDir = project.buildDir.resolve(Config.relativePath)

        with(project.the<LicenseReportExtension>()) {
            outputDir = reportOutputDir.absolutePath
            excludeGroups = arrayOf("io.spine", "io.spine.tools", "io.spine.gcloud")
            configurations = ALL

            renderers = arrayOf(MarkdownReportRenderer(Config.outputFilename))
        }
    }

    fun enableConsolidation(project: Project) {
        val rootProject = project.rootProject
        val reportLicensesInRepo = rootProject.tasks.register("reportLicensesInRepo") {
            val task = this
            val targetProjects: Iterable<Project> = if (rootProject.subprojects.isEmpty()) {
                println("Configuring the license report for a single root project.")
                listOf(this.project)
            } else {
                println("Configuring the license report for all subprojects of a root project.")
                rootProject.subprojects
            }

            targetProjects.forEach {
                val generateLicenseReport = it.findTask<Task>("generateLicenseReport")
                task.dependsOn(generateLicenseReport)
                generateLicenseReport.dependsOn(project.findTask("assemble"))
            }

            doLast {
                val paths = targetProjects.map {
                    "${it.buildDir}/${Config.relativePath}/${Config.outputFilename}"
                }

                println("Aggregating the reports from the target projects.")
                val aggregatedContent =
                    paths.map { (File(it)).readText() }.joinToString("\n\n\n")

                (File("${rootProject.rootDir}/${Config.outputFilename}")).writeText(
                    aggregatedContent
                )
            }
            dependsOn(project.findTask("assemble"))
        }
        project.findTask<Task>("build")
            .finalizedBy(reportLicensesInRepo)
    }
}
