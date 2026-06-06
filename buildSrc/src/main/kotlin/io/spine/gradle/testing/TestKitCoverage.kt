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

package io.spine.gradle.testing

import io.spine.dependency.test.Jacoco
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * Configures the `test` tasks of this project so that the JaCoCo agent is
 * attached to the Gradle TestKit worker JVMs they spawn.
 *
 * `Plugin<Settings>` implementations and other plugin code exercised through
 * [`GradleRunner`][org.gradle.testkit.runner.GradleRunner] run in a separate
 * worker JVM. Kover (and JaCoCo) instrument only the test JVM, so that
 * out-of-process execution is otherwise not credited to coverage.
 *
 * This method:
 *
 *  1. Resolves the standalone JaCoCo agent JAR pinned to [Jacoco.version]
 *     through a dedicated [AGENT_CONFIGURATION] configuration.
 *  2. Passes the agent JAR path and a per-module exec directory
 *     (`build/`[TESTKIT_COVERAGE_DIR]) to the test JVM as system properties.
 *     The `plugin-testlib` harness reads these and writes a `gradle.properties`
 *     into the worker's Gradle user home that adds `-javaagent:…` to the worker JVM.
 *  3. Wipes the exec directory once per build invocation — through a dedicated
 *     [CLEAN_TASK] `Delete` task that every `Test` task depends on — so stale
 *     worker coverage from a previous run does not accumulate. The cleanup is
 *     **not** done in each task's `doFirst`: a module may declare more than one
 *     TestKit `Test` task, and since the workers append to a single per-module
 *     exec file, a per-task wipe would erase the coverage of every task but the
 *     last one to run. A single shared clean lets the sequentially-run tasks
 *     accumulate into the same file.
 *
 * The produced `.exec` files are merged into the Kover reports by
 * [io.spine.gradle.report.coverage.KoverConfig]. The agent emits binary
 * execution data rather than an XML report because that is its only file output,
 * and because Kover merges binary data at the probe level — see `KoverConfig`
 * for why binary, not XML.
 *
 * The method is idempotent and may be called once per module that runs
 * TestKit-based tests.
 */
fun Project.enableTestKitCoverage() {
    val agent = configurations.maybeCreate(AGENT_CONFIGURATION).apply {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies.add(agent.name, "org.jacoco:org.jacoco.agent:${Jacoco.version}:runtime")

    val agentPath = agent.elements.map { it.single().asFile.absolutePath }
    val execDir = layout.buildDirectory.dir(TESTKIT_COVERAGE_DIR)

    // Wipe the shared exec directory once, before any `Test` task runs, rather
    // than in each task's `doFirst`. With several TestKit `Test` tasks per module
    // appending to the same exec file, a per-task wipe would keep only the last
    // task's coverage.
    val cleanCoverage = tasks.register<Delete>(CLEAN_TASK) {
        delete(execDir)
    }

    tasks.withType<Test>().configureEach {
        dependsOn(cleanCoverage)
        inputs.files(agent).withPropertyName(AGENT_CONFIGURATION)
        doFirst {
            val dir = execDir.get().asFile
            dir.mkdirs()
            systemProperty(AGENT_PROPERTY, agentPath.get())
            systemProperty(EXEC_DIR_PROPERTY, dir.absolutePath)
        }
    }
}

/**
 * The name of the directory under a module's `build` directory where the
 * coverage of Gradle TestKit worker JVMs is collected.
 *
 * The directory holds JaCoCo execution-data (`.exec`) files — one per test
 * project directory — written by the JaCoCo agent attached to the TestKit
 * worker. `KoverConfig` picks these files up and feeds them into the Kover
 * reports as additional binary reports.
 *
 * @see io.spine.gradle.report.coverage.KoverConfig
 */
internal const val TESTKIT_COVERAGE_DIR: String = "jacoco-testkit"

/**
 * The name of the `Delete` task that wipes the [TESTKIT_COVERAGE_DIR] once per
 * build invocation, before any TestKit `Test` task runs.
 *
 * Every `Test` task configured by [Project.enableTestKitCoverage] depends on this
 * task, so the shared exec directory is cleaned exactly once even when a module
 * declares several TestKit `Test` tasks.
 */
private const val CLEAN_TASK: String = "cleanTestKitCoverage"

/**
 * The name of the system property carrying the absolute path to the JaCoCo
 * agent JAR which the test harness attaches to TestKit worker JVMs.
 *
 * The value is read by `plugin-testlib` at test runtime.
 *
 * The constant is duplicated in `io.spine.tools.gradle.testing.TestKitCoverage`
 * of the `plugin-testlib` module (which cannot depend on `buildSrc`). Keep the
 * two values in sync.
 */
private const val AGENT_PROPERTY: String =
    "io.spine.tools.gradle.testkit.coverage.agent"

/**
 * The name of the system property carrying the absolute path to the directory
 * where TestKit workers write their JaCoCo execution data.
 *
 * The constant is duplicated in `io.spine.tools.gradle.testing.TestKitCoverage`
 * of the `plugin-testlib` module. Keep the two values in sync.
 */
private const val EXEC_DIR_PROPERTY: String =
    "io.spine.tools.gradle.testkit.coverage.execDir"

/**
 * The name of the dedicated, resolvable configuration that holds the standalone
 * JaCoCo agent JAR (`org.jacoco:org.jacoco.agent:<version>:runtime`) attached to
 * TestKit worker JVMs.
 *
 * The configuration is hidden and non-consumable; it exists only to resolve the
 * agent JAR and to register it as an input of the `test` tasks.
 */
private const val AGENT_CONFIGURATION: String = "testKitJacocoAgent"
