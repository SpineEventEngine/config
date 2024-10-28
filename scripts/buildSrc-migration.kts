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
