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

package io.spine.dependency.build

/**
 * Dependencies on Checkstyle Java linter.
 *
 * @see <a href="https://checkstyle.sourceforge.io/">Checkstyle</a>
 * @see [io.spine.gradle.checkstyle.CheckStyleConfig]
 */
@Suppress("unused", "ConstPropertyName")
object CheckStyle {
    /**
     * The version to be used in the project.
     *
     * Pinned to `10.12.1`, the last release that resolves and runs in our builds.
     * `forceVersions()` forces our versions into every configuration, including
     * `checkstyle`, and enables `failOnVersionConflict()` there. Every newer
     * release fails for one of these reasons:
     *
     *  - `10.12.2` to `10.14.1` depend on Doxia, which brings
     *    `com.google.collections:google-collections:1.0`. Guava `32.1.0` and later
     *    declare the same capability in their Gradle metadata, so the forced
     *    [Guava][io.spine.dependency.lib.Guava] makes Gradle reject the graph with
     *    a capability conflict. `10.14.2` excludes `google-collections` from both
     *    Doxia modules.
     *  - `10.14.2` to `12.3.1` bring two versions each of `commons-lang3` and
     *    `httpcore` through Doxia, and `failOnVersionConflict()` rejects them.
     *    We do not force these modules.
     *  - `13.0.0` and later require Java 21, while our builds run Checkstyle on
     *    Java 17. From `13.11.0` on, they no longer depend on Doxia.
     *
     * To move to `12.3.1`, force `commons-lang3` and `httpcore` in the `checkstyle`
     * configuration. Moving further requires running Checkstyle on Java 21.
     *
     * @see <a href="https://checkstyle.sourceforge.io/releasenotes.html">Checkstyle</a>
     */
    const val version = "10.12.1"
}
