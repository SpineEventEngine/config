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
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import com.github.jk1.license.render.ReportRenderer
import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.internal.gradle.report.license.Configuration.runtime
import io.spine.internal.gradle.report.license.Configuration.runtimeClasspath
import java.io.File
import kotlin.reflect.KCallable

/**
 * Renders the dependency report in markdown.
 */
internal class MarkdownReportRenderer(
    private val filename: String
) : ReportRenderer {

    override fun render(data: ProjectData) {
        val project = data.project
        val config =
            project.extensions.findByName("licenseReport") as LicenseReportExtension
        val outputFile = File(config.outputDir).resolve(filename)


        val template = Template(project, outputFile)
        template.writeHeader()
        printDependencies(outputFile, data)
        template.writeFooter()
    }

    private fun printDependencies(outputFile: File, data: ProjectData) {
        val deps = Dependencies.of(data)
        deps.printRuntime(outputFile)
            .printCompileTooling(outputFile)
    }
}

private class Dependencies(
    private val runtime: Iterable<ModuleData>,
    private val compileTooling: Iterable<ModuleData>

) {

    companion object {
        fun of(data: ProjectData): Dependencies {
            val runtimeDeps = mutableListOf<ModuleData>()
            val compileToolingDeps = mutableListOf<ModuleData>()
            data.configurations.forEach { config ->
                if (config.isOneOf(runtime, runtimeClasspath)) {
                    runtimeDeps.addAll(config.dependencies)
                } else {
                    compileToolingDeps.addAll(config.dependencies)
                }
            }
            return Dependencies(runtimeDeps.toSortedSet(), compileToolingDeps.toSortedSet())
        }
    }

    @CanIgnoreReturnValue
    fun printRuntime(outputFile: File): Dependencies {
        outputFile.printSection("Runtime", runtime)
        return this
    }

    @CanIgnoreReturnValue
    fun printCompileTooling(outputFile: File): Dependencies {
        outputFile.printSection("Compile, tests and tooling", compileTooling)
        return this
    }
}

private fun ModuleData.print(outputFile: File) {
    outputFile.appendText("\n1.")

    this.print(ModuleData::getGroup, outputFile, "Group")
        .print(ModuleData::getName, outputFile, "Name")
        .print(ModuleData::getVersion, outputFile, "Version")

    if (this.poms.isEmpty() && this.manifests.isEmpty()) {
        outputFile.appendText(" **No license information found**")
        return
    }

    var projectUrlDone = false
    if (this.manifests.isNotEmpty() && this.poms.isNotEmpty()) {
        val manifest = this.manifests.first()
        val pomData = this.poms.first()
        if (manifest.url != null && pomData.projectUrl != null && manifest.url == pomData.projectUrl) {
            outputFile.appendText("\n     * **Project URL:** [${manifest.url}](${manifest.url})")
            projectUrlDone = true
        }
    }

    if (this.manifests.isNotEmpty()) {
        val manifest = this.manifests.first()
        if (!manifest.url.isNullOrEmpty() && !projectUrlDone) {
            outputFile.appendText("\n     * **Manifest Project URL:** [${manifest.url}](${manifest.url})")
        }
        if (!manifest.license.isNullOrEmpty()) {
            when {
                manifest.license.startsWith("http") -> {
                    outputFile.appendText("\n     * **Manifest license URL:** [${manifest.license}](${manifest.license})")

                }
                manifest.hasPackagedLicense -> {
                    outputFile.appendText("\n     * **Packaged License File:** [${manifest.license}](${manifest.url})")
                }
                else -> {
                    outputFile.appendText("\n     * **Manifest License:** ${manifest.license} (Not packaged)")

                }
            }
        }
    }

    if (this.poms.isNotEmpty()) {
        val pomData = this.poms.first()
        if (!pomData.projectUrl.isNullOrEmpty() && !projectUrlDone) {
            outputFile.appendText("\n     * **POM Project URL:** [${pomData.projectUrl}](${pomData.projectUrl})")

        }
        if (pomData.licenses != null) {
            pomData.licenses.forEach { license ->
                outputFile.appendText("\n     * **POM License: ${license.name}**")

                if (!license.url.isNullOrEmpty()) {
                    when {
                        license.url.startsWith("http") -> {
                            outputFile.appendText(" - [${license.url}](${license.url})")
                        }
                        else -> {
                            outputFile.appendText(" **License:** ${license.url}")
                        }
                    }
                }
            }
        }
    }
    outputFile.appendText("\n")
}

private fun ModuleData.print(getter: KCallable<*>, outputFile: File, title: String): ModuleData {
    val value = getter.call(this)
    if (value != null) {
        outputFile.appendText(" **${title}:** ${value}")
    }
    return this
}

private fun File.printSection(title: String, modules: Iterable<ModuleData>) {
    this.appendText("\n## $title")
    modules.forEach {
        it.print(this)
    }
}
