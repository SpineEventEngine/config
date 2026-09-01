/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

@file:Suppress("RemoveRedundantQualifierName") // To prevent IDEA replacing FQN imports.

import io.spine.gradle.applyStandard
import io.spine.gradle.javac.configureJavac
import io.spine.gradle.kotlin.applyJvmToolchain
import io.spine.gradle.kotlin.setFreeCompilerArgs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    io.spine.gradle.doApplyStandard(repositories)
}

plugins {
    kotlin("jvm")
}

allprojects {
    apply {
        from("$rootDir/version.gradle.kts")
    }

    repositories.applyStandard()

    version = extra["versionToPublish"]!!
}

subprojects {
    apply {
        plugin("kotlin")
        plugin("dokka-for-java")
    }

    java {
        tasks.withType<JavaCompile>().configureEach {
            configureJavac()
        }
    }

    kotlin {
        val javaVersion = JavaVersion.VERSION_11.toString()

        applyJvmToolchain(javaVersion)

        tasks.withType<KotlinCompile>().configureEach {
            setFreeCompilerArgs()
        }
    }
}
