/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.gradle.report.coverage

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Credits the test coverage produced by the [contributor] module for the classes
 * of this project to this project's own Kover report.
 *
 * Some modules' production classes are exercised only by the tests of a sibling
 * module — for example, the language-neutral `psi` classes are tested through
 * the Java-PSI fixtures that live in `psi-java`. Kover's per-module report sees
 * only this module's own `test` execution data, so that cross-module coverage is
 * otherwise missing from the per-module report (which is what Codecov consumes),
 * even though the root aggregated report already accounts for it.
 *
 * This function adds the [contributor]'s JaCoCo execution data to this project's
 * `total` report as an additional binary report. Only this project's classes are
 * credited from it — coverage of unrelated classes in the same execution data is
 * ignored, because a Kover report is scoped to the owning project's classes. The
 * report tasks are wired to run after the contributor's `test` task so the data
 * is present when a report is generated.
 *
 * Requires the Kover plugin to be applied to this project.
 * A cross-project **task** dependency is used, not a project dependency,
 * so it does not introduce a dependency cycle even when the [contributor]
 * already depends on this project.
 */
fun Project.creditTestCoverageFrom(contributor: Project) {
    val execFile = contributor.layout.buildDirectory.file(TEST_EXEC_FILE)
    extensions.configure(KoverProjectExtension::class.java) {
        reports {
            total {
                additionalBinaryReports.add(execFile.map { it.asFile })
            }
        }
    }
    tasks.matching { it.consumesCoverageBinaryReports() }.configureEach {
        dependsOn("${contributor.path}:test")
    }
}

/**
 * The Kover/JaCoCo execution-data file produced by a module's `test` task when
 * the coverage engine is pinned to JaCoCo via `useJacoco(...)`.
 */
private const val TEST_EXEC_FILE: String = "kover/bin-reports/test.exec"

/**
 * Tells whether this is a Kover task that reads the binary reports and therefore
 * must run only after the [contributor's][creditTestCoverageFrom] test data exists.
 *
 * This matches both the report tasks (`koverXmlReport`, `koverHtmlReport`,
 * `koverBinaryReport`) and the verification tasks (`koverVerify` and its
 * cacheable companion `koverCachedVerify`) — the suffix test covers the
 * `Cached*` variants Kover registers, which are the ones that actually consume
 * the binary reports.
 */
private fun Task.consumesCoverageBinaryReports(): Boolean =
    name.startsWith("kover") && (name.endsWith("Report") || name.endsWith("Verify"))
