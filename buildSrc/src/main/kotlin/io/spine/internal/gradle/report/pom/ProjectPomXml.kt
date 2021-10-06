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

import SpineLicenceAsXml
import groovy.xml.MarkupBuilder
import java.io.File
import java.io.FileWriter
import java.io.StringWriter
import java.lang.System.lineSeparator
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withGroovyBuilder

/**
 * A `pom.xml` file that contains dependencies of the project and its subprojects.
 *
 * It is not usable for `maven` build tasks and serves as a description of project first-level
 * dependencies, i.e. transitive dependencies are not included
 */
internal class ProjectPomXml
private constructor(
    private val project: Project,
    private val groupId: String,
    private val artifactId: String,
    private val version: String
) {

    internal companion object {
        private const val XML_METADATA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        private const val PROJECT_SCHEMA_LOCATION = "<project " +
                "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 " +
                "http://maven.apache.org/xsd/maven-4.0.0.xsd\" " +
                "xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
                "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
        private const val MODEL_VERSION = "<modelVersion>4.0.0</modelVersion>"
        private const val CLOSING_PROJECT_TAG = "</project>"
        private const val SPINE_INCEPTION_YEAR = "2015"
        private val NEW_LINE = lineSeparator()

        fun from(data: RootProjectData): ProjectPomXml {
            return ProjectPomXml(data.project, data.groupId, data.artifactId, data.version)
        }

        /**
         * Writes the specified lines using the specified writer, dividing them by platforms line
         * separator.
         *
         * The written lines are also padded with platforms line separator from both sides
         */
        private fun writeBlocks(writer: StringWriter, vararg lines: String) {
            writer.write(NEW_LINE)
            lines.iterator().forEach {
                writer.write(it)
                writer.write(NEW_LINE)
                writer.write(NEW_LINE)
            }
            writer.write(NEW_LINE)
        }

        /**
         * Obtains a String that represents a tag with the inception year of Spine.
         */
        private fun inceptionYear(): String {
            val writer = StringWriter()
            val xmlBuilder = MarkupBuilder(writer)
            xmlBuilder.withGroovyBuilder {
                "inceptionYear" to SPINE_INCEPTION_YEAR
            }
            return writer.toString()
        }

        /**
         * Obtains licence information about Spine.
         *
         * <p>More on licences <a href="https://maven.apache.org/pom.html#Licenses">here</a>.
         */
        private fun licence(): String {
            val writer = StringWriter()
            SpineLicenceAsXml.writeUsing(writer)
            return writer.toString()
        }

        /**
         * Obtains a description comment that describes the nature of the generated `pom.xml` file.
         */
        private fun describingComment(): String {
            val description =
                lineSeparator() +
                        "This file was generated using the Gradle `generatePom` task. " +
                        lineSeparator() +
                        "This file is not suitable for `maven` build tasks. It only describes the " +
                        "first-level dependencies of " +
                        lineSeparator() +
                        "all modules and does not describe the project " +
                        "structure per-subproject." +
                        lineSeparator()
            val descriptionComment =
                String.format(
                    "<!-- %s %s %s -->",
                    lineSeparator(),
                    description,
                    lineSeparator()
                )
            return descriptionComment
        }


        /**
         * Writes the XML metadata using the specified writer.
         */
        private fun writeHeader(stringWriter: StringWriter) {
            stringWriter.write(XML_METADATA)
            stringWriter.write(lineSeparator())
            stringWriter.write(PROJECT_SCHEMA_LOCATION)
            stringWriter.write(lineSeparator())
            stringWriter.write(MODEL_VERSION)
            stringWriter.write(lineSeparator())
        }
    }

    /**
     * Writes the {@code pom.xml} file containing dependencies of this project and its subprojects to the specified
     * location.
     *
     * <p>If a file with the specified location exists, its contents will be substituted with a new
     * {@code pom.xml}.
     *
     * @param file a file to write {@code pom.xml} contents to
     */
    fun writeTo(file: File) {
        val fileWriter = FileWriter(file)
        val stringWriter = StringWriter()
        writeHeader(stringWriter)

        writeBlocks(
            stringWriter,
            describingComment(),
            rootProjectData(),
            inceptionYear(),
            licence(),
            projectDependencies()
        )
        fileWriter.write(stringWriter.toString())
        fileWriter.close()
    }

    /**
     * Obtains a string that contains project dependencies as XML.
     *
     * <p>Obtained string also contains a closing project tag.
     */
    private fun projectDependencies(): String {
        val writer = StringWriter()
        val projectDeps = ProjectDependenciesAsXml.of(project)
        projectDeps.writeUsing(writer)
        writer.write(NEW_LINE)
        writer.write(CLOSING_PROJECT_TAG)
        return writer.toString()
    }

    /**
     * Obtains a string that contains the name and the version of the current project.
     */
    private fun rootProjectData(): String {
        val writer = StringWriter()
        val xmlBuilder = MarkupBuilder(writer)
        xmlBuilder.withGroovyBuilder {
            "groupId" to groupId
            "artifactId" to artifactId
            "version" to version
        }
        return writer.toString()
    }
}
