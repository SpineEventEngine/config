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

/**
 * This script-plugin sets up Kotlin code analyzing with Detekt.
 *
 * After apply, Detekt is configured to use `${rootDir}/config/quality/detekt-config.yml` file.
 * Projects can append their own config files to override some parts of the default one or drop it
 * at all in favor of their own one.
 *
 * An example of appending a custom config file along with the default one:
 *
 * ```
 * detekt {
 *     config.from("config/detekt-custom-config.yml")
 * }
 * ```
 *
 * In order to totally substite it, just overwrite the corresponding property:
 *
 * ```
 * detekt {
 *     config = files("config/detekt-custom-config.yml")
 * }
 * ```
 *
 * Also, it's possible to suppress Detekt findings using [baseline](https://detekt.dev/docs/introduction/baseline/)
 * file instead of suppressions in source code.
 *
 * An example of passing a baseline file:
 *
 * ```
 * detekt {
 *     baseline = file("config/detekt-baseline.yml")
 * }
 * ```
 */

plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    buildUponDefaultConfig = true
    config = files("${rootDir}/config/quality/detekt-config.yml")
}
