/*
 * Copyright 2022, TeamDev. All rights reserved.
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

package io.spine.internal.gradle.javadoc

import java.io.File
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

/**
 * Javadoc processing settings.
 *
 * This type is named with `Config` suffix to avoid its confusion with the standard `Javadoc` type.
 */
@Suppress("unused")
object JavadocConfig {

    @Suppress("MemberVisibilityCanBePrivate") // opened to be visible from docs.
    val tags = listOf(
        JavadocTag("apiNote", "API Note"),
        JavadocTag("implSpec", "Implementation Requirements"),
        JavadocTag("implNote", "Implementation Note")
    )

    val encoding = Encoding("UTF-8")

    fun applyTo(project: Project) {
        val javadocTask = project.tasks.javadocTask()
        configureTask(javadocTask)
        val docletOptions = javadocTask.options as StandardJavadocDocletOptions
        configureDoclet(docletOptions)
    }

    private fun configureTask(javadocTask: Javadoc) {
        discardJavaModulesInLinks(javadocTask)
    }

    private fun configureDoclet(docletOptions: StandardJavadocDocletOptions) {
        docletOptions.encoding = encoding.name
        reduceParamWarnings(docletOptions)
        registerCustomTags(docletOptions)
        linkOpenJdkApi(docletOptions)
    }

    /**
     * Configures `javadoc` tool to avoid numerous warnings for missing `@param` tags.
     *
     * As suggested by Stephen Colebourne:
     *  [https://blog.joda.org/2014/02/turning-off-doclint-in-jdk-8-javadoc.html]
     *
     * See also:
     *  [https://github.com/GPars/GPars/blob/master/build.gradle#L268]
     */
    private fun reduceParamWarnings(docletOptions: StandardJavadocDocletOptions) {
        if (JavaVersion.current().isJava8Compatible) {
            docletOptions.addStringOption("Xdoclint:none", "-quiet")
        }
    }

    /**
     * Registers [tags] for `javadoc`.
     */
    fun registerCustomTags(docletOptions: StandardJavadocDocletOptions) {
        docletOptions.tags = tags.map { it.toString() }
    }

    /**
     * Links OpenJDK SE 11 API to be referenced to when navigating to the types
     * from the standard library (`String`, `List`, etc.).
     */
    private fun linkOpenJdkApi(docletOptions: StandardJavadocDocletOptions) {
        val standardLibraryAPI = "https://cr.openjdk.java.net/~iris/se/11/latestSpec/api/"
        docletOptions.addStringOption("link", standardLibraryAPI)
    }

    /**
     * Discards using of Java 9 modules in URL links generated by javadoc for our codebase.
     *
     * This fixes navigation to classes through the search results.
     *
     * The issue appeared after migration to Java 11. When javadoc is generated for a project
     * that does not declare Java 9 modules, search results contain broken links with appended
     * `undefined` prefix to the URL. This `undefined` was meant to be a name of a Java 9 module.
     *
     * See: [Issue #334](https://github.com/SpineEventEngine/config/issues/334)
     */
    private fun discardJavaModulesInLinks(javadoc: Javadoc) {

        // We ask `javadoc` task to modify `search.js` and override a method, responsible for
        // the formation of URL prefixes. We can't specify the option `--no-module-directories`,
        // because it leads to discarding of all module prefixes in generated links. That means,
        // links to the types from the standard library would not work, as they declared
        // within modules since Java 9.

        val discardModulePrefix = """
            
            getURLPrefix = function(ui) {
                return "";
            };
            """.trimIndent()

        javadoc.doLast {
            val destinationDir = javadoc.destinationDir!!.absolutePath
            val searchScript = File("$destinationDir/search.js")
            searchScript.appendText(discardModulePrefix)
        }
    }
}
