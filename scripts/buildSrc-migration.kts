/*
 * Copyright 2024, TeamDev. All rights reserved.
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

import java.io.File

/*
 * Copyright 2024, TeamDev. All rights reserved.
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

private fun mv(oldPackage: String, newPackage: String, name: String): Pair<String, String> =
    "$oldPackage.$name" to "$newPackage.$name"

private val localDependencies = buildMap<String, String> {
    // The package we have local dependencies until mid-October '24.
    val older = "io.spine.internal.dependency"
    // The package we have for local dependencies after they moved recently.
    val interim = "io.spine.internal.dependency.spine"
    // New package.
    val new = "io.spine.dependency.local"
    
    // Older `local` deps.
    mv(older, new, "Logging")
    mv(older, new, "McJava")
    mv(older, new, "ProtoData")
    mv(older, new, "ProtoTap")
    mv(older, new, "Spine")
    mv(older, new, "Validation")

    // Interim `local` deps.
     mv(interim, new, "ArtifactVersion")
     mv(interim, new, "CoreJava")
     mv(interim, new, "Logging")
     mv(interim, new, "McJava")
     mv(interim, new, "ProtoData")
     mv(interim, new, "ProtoTap")
     mv(interim, new, "Spine")
     mv(interim, new, "ToolBase")
     mv(interim, new, "Validation")
}

private val libraries = buildMap<String, String> {
    val old = "io.spine.internal.dependency"
    val new = "io.spine.dependency.lib"

    mv(old, new, "Aedile")
    mv(old, new, "ApacheHttp")
    mv(old, new, "AppEngine")
    mv(old, new, "Auto")
    mv(old, new, "BouncyCastle")
    mv(old, new, "Caffeine")
    mv(old, new, "CommonsCli")
    mv(old, new, "CommonsCodec")
    mv(old, new, "CommonsCodec")
    mv(old, new, "CommonsLogging")
    mv(old, new, "Coroutines")
    mv(old, new, "Firebase")
    mv(old, new, "Flogger")
    mv(old, new, "GoogleApis")
    mv(old, new, "GoogleCloud")
    mv(old, new, "Grpc")
    mv(old, new, "GrpcKotlin")
    mv(old, new, "Gson")
    mv(old, new, "Guava")
    mv(old, new, "HttpClient")
    mv(old, new, "IntelliJ")
    mv(old, new, "J2ObjC")
    mv(old, new, "Jackson")
    mv(old, new, "JavaDiffUtils")
    mv(old, new, "JavaJwt")
    mv(old, new, "JavaPoet")
    mv(old, new, "JavaX")
    mv(old, new, "Klaxon")
    mv(old, new, "Kotlin")
    mv(old, new, "KotlinSemver")
    mv(old, new, "KotlinX")
    mv(old, new, "Log4j2")
    mv(old, new, "Netty")
    mv(old, new, "Okio")
    mv(old, new, "Plexus")
    mv(old, new, "Protobuf")
    mv(old, new, "Roaster")
    mv(old, new, "Slf4J")
}

/**
 * Build tools with the exception of [test] dependencies.
 */
private val buildTools = buildMap<String, String> {
    val old = "io.spine.internal.dependency"
    val new = "io.spine.dependency.build"

    mv(old, new, "AnimalSniffer")
    mv(old, new, "CheckerFramework")
    mv(old, new, "CheckStyle")
    mv(old, new, "Dokka")
    mv(old, new, "ErrorProne")
    mv(old, new, "FindBugs")
    mv(old, new, "GradleDoctor")
    mv(old, new, "Ksp")
    mv(old, new, "LicenseReport")
    mv(old, new, "OsDetector")
}

/**
 * Tools and libraries for testing.
 */
private val test = buildMap<String, String> {
    val old = "io.spine.internal.dependency"
    val new = "io.spine.dependency.build"

    mv(old, new, "AssertK")
    mv(old, new, "Clikt")
    mv(old, new, "Hamcrest")
    mv(old, new, "Jacoco")
    mv(old, new, "JUnit")
    mv(old, new, "Kotest")
    mv(old, new, "Kover")
    mv(old, new, "OpenTest4J")
    mv(old, new, "SystemLambda")
    mv(old, new, "TestKitTruth")
    mv(old, new, "Truth")
}

private val exclusions = setOf(
    ".github",
    ".github-workflows",
    ".gradle",
    "build",
    "BuildSpeed",
    "config"
)

fun applyReplacements() {
    val projectRoot = File(".")
    projectRoot.walk()
        .onEnter { it.name !in exclusions }
}
