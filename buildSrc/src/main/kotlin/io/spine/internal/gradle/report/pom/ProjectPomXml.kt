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

import java.lang.System.lineSeparator
import org.gradle.api.Project

/**
 * A `pom.xml` file that contains dependencies of the project and its subprojects.
 *
 * It is not usable for `maven` build tasks and serves as a description of project first-level
 * dependencies, i.e. transitive dependencies are not included
 */
class ProjectPomXml
private constructor(
    private val project: Project,
    private val groupId: String,
    private val artifactId: String,
    private val version: String
) {

    companion object {
        const val XML_METADATA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        const val PROJECT_SCHEMA_LOCATION = "<project " +
                "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 " +
                "http://maven.apache.org/xsd/maven-4.0.0.xsd\" " +
                "xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
                "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
        const val MODEL_VERSION = "<modelVersion>4.0.0</modelVersion>"
        const val CLOSING_PROJECT_TAG = "</project>"
        const val SPINE_INCEPTION_YEAR = "2015"
        val NEW_LINE = lineSeparator()

        fun from(data: RootProjectData): ProjectPomXml {
            return ProjectPomXml(data.project, data.groupId, data.artifactId, data.version)
        }
    }
}
