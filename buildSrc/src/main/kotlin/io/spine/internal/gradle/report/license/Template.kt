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

import io.spine.internal.gradle.publish.prefix
import java.io.File
import java.util.*
import org.gradle.api.Project

/**
 * The template text pieces of the license report.
 */
internal class Template(
    private val project: Project,
    private val file: File
) {

    internal fun writeHeader() {
        file.appendText(
            """
    # Dependencies of `${project.group}:${project.prefix()}${project.name}:${project.version}`
"""
        )
    }

    internal fun writeFooter() {
        file.appendText(
            "\n\n" +
                    "The dependencies distributed under several licenses, " +
                    "are used according their commercial-use-friendly license." +
                    "\n\n" +
                    "This report was generated on **${Date()}** " +
                    "using [Gradle-License-Report plugin]" +
                    "(https://github.com/jk1/Gradle-License-Report) by Evgeny Naumenko, " +
                    "licensed under [Apache 2.0 License]" +
                    "(https://github.com/jk1/Gradle-License-Report/blob/master/LICENSE)."
        )

    }

}
