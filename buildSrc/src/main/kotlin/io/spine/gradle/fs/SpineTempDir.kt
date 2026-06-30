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

package io.spine.gradle.fs

import java.nio.file.Files.createDirectories
import java.nio.file.Path

/**
 * The common parent directory for the temporary directories created by the build.
 *
 * The directory is created [lazily][path] under the system temporary directory
 * (`java.io.tmpdir`) and is named after the package of [LazyTempPath], so that any
 * leftover files are easy to attribute.
 *
 * Upon creation, the directory is scheduled for recursive removal when the JVM shuts
 * down. This is a safety net should the explicit cleanup performed by the build tasks
 * not run — for example, when a build fails before reaching it. Grouping every temporary
 * directory under a single root also keeps the number of registered shutdown hooks at
 * one, regardless of how many temporary directories the build creates.
 *
 * @see LazyTempPath
 */
internal object SpineTempDir {

    /**
     * The name of the base directory, derived from the package of [LazyTempPath].
     */
    private val name: String = LazyTempPath::class.java.packageName

    /**
     * The base directory, created on the first access and removed on JVM shutdown.
     */
    val path: Path by lazy { createAndScheduleRemoval() }

    private fun createAndScheduleRemoval(): Path {
        val baseDir = Path.of(systemTempDir(), name)
        createDirectories(baseDir)
        deleteRecursivelyOnShutdown(baseDir)
        return baseDir
    }

    /**
     * Obtains the value of the system property pointing to the temporary directory.
     */
    private fun systemTempDir(): String = System.getProperty("java.io.tmpdir")

    /**
     * Requests the recursive removal of the given [directory] when the JVM shuts down.
     *
     * @see Runtime.addShutdownHook
     */
    private fun deleteRecursivelyOnShutdown(directory: Path) {
        val runtime = Runtime.getRuntime()
        runtime.addShutdownHook(Thread {
            val deleted = directory.toFile().deleteRecursively()
            if (!deleted) {
                System.err.println("Unable to delete the temporary directory `$directory`.")
            }
        })
    }
}
