/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.dependency.build

import io.spine.dependency.Dependency

/**
 * Kotlin Symbol Processing API.
 *
 * @see <a href="https://github.com/google/ksp">KSP GitHub repository</a>
 */
@Suppress("unused")
object Ksp : Dependency() {
    override val version = "2.3.11"
    val dogfoodingVersion = version
    override val group = "com.google.devtools.ksp"

    const val id = "com.google.devtools.ksp"
    const val gradlePluginArtifactName = "com.google.devtools.ksp.gradle.plugin"

    val symbolProcessingApi = "$group:symbol-processing-api"
    val symbolProcessing = "$group:symbol-processing"
    val symbolProcessingAaEmb = "$group:symbol-processing-aa-embeddable"
    val symbolProcessingCommonDeps = "$group:symbol-processing-common-deps"
    val gradlePlugin = "$group:symbol-processing-gradle-plugin"

    override val modules = listOf(
        symbolProcessingApi,
        symbolProcessing,
        symbolProcessingAaEmb,
        symbolProcessingCommonDeps,
        gradlePlugin,
    )
}
