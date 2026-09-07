/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

/**
 * Anchors this build so that Gradle does not search parent directories for
 * a settings file.
 *
 * Without it, checking `config` out inside a superproject that has its own
 * settings file (such as `SpineEventEngine/summit`) makes Gradle bind this
 * directory to that build and fail with "is not part of the build defined by
 * settings file ...".
 *
 * The name matches the one Gradle previously derived from the project
 * directory, so nothing else about the build changes.
 */
rootProject.name = "config"
