/*
 * Copyright 2021, TeamDev. All rights reserved.
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

@file:Suppress("unused")    /* Some constants may be used throughout the Spine repos. */

package io.spine.internal.gradle

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import org.ajoberstar.grgit.Grgit
import org.gradle.api.tasks.TaskContainer

/**
 * A tool to execute the Gradle `build` task in selected Git repositories,
 * with the specific version of [buildSrc] contents.
 *
 * Uses Gradle's [tasks] container to register itself as a Gradle task.
 *
 * Checks out the content of selected repositories into the specified [tempFolder]. The folder
 * is created if it does not exist. By default, uses `./tmp` as a temp folder.
 *
 * This tool uses `println`s to print out its state. This is done to simplify the configuration
 * and dependencies.
 *
 * When running the Gradle build for each repository, a [RunBuild] task is used. Error and debug
 * logs of each Gradle test build are written according to this task's implementation.
 *
 * If the selected repository already contains its own `buildSrc` folder, it is NOT overwritten,
 * but rather renamed into `buildSrc-original`. This allows further tracing if the build fails.
 */
class BuildSrcTester(
    private val buildSrc: Path,
    private val tasks: TaskContainer,
    private val tempFolder: File = File("./tmp")
) {
    /**
     * Git repositories to test.
     */
    private val repos: MutableList<GitRepository> = ArrayList()

    /**
     * Adds a Git [repo] into the test build by its URI.
     *
     * The `master` branch is used as the one to checkout.
     */
    fun addRepo(repo: URI): BuildSrcTester {
        repos.add(GitRepository(repo))
        return this
    }

    /**
     * Adds a test
     */
    fun addRepo(repo: URI, branch: Branch): BuildSrcTester {
        repos.add(GitRepository(repo, branch))
        return this
    }

    fun registerUnder(taskName: String) {
        val tasksPerRepo = repos.map { testWithBuildSrc(it) }

        tasks.register(taskName) {
            for (repoTaskName in tasksPerRepo) {
                dependsOn(repoTaskName)
            }
        }
    }

    private fun testWithBuildSrc(gitRepo: GitRepository): String {
        val runGradleName = runGradleTask(gitRepo)
        doRegisterRunBuild(runGradleName, gitRepo)

        val executeBuildName = executeBuildTask(gitRepo)
        doRegisterExecuteBuild(executeBuildName, gitRepo, runGradleName)
        return executeBuildName
    }

    private fun doRegisterExecuteBuild(
        executeBuildName: String,
        gitRepo: GitRepository,
        runGradleName: String
    ) {
        tasks.register(executeBuildName) {
            doLast {
                println(" *** Testing `config/buildSrc` with `${gitRepo.name}`. ***")
                val localRepo = gitRepo.checkout(tempFolder)
                localRepo.replaceBuildSrc(buildSrc)
            }
            finalizedBy(runGradleName)
        }
    }

    private fun doRegisterRunBuild(
        runGradleName: String,
        gitRepo: GitRepository,
    ) {
        tasks.register(runGradleName, RunBuild::class.java) {
            doFirst {
                println("`${gitRepo.name}`: starting Gradle build...")
            }
            doLast {
                println("*** `${gitRepo.name}`: Gradle build completed. ***")
            }
            directory = gitRepo.prepareCheckout(tempFolder).absolutePath
            maxDurationMins = 30
        }
    }

    private fun runGradleTask(repo: GitRepository): String {
        return "run-gradle-${repo.name}"
    }

    private fun executeBuildTask(repo: GitRepository): String {
        return "execute-build-${repo.name}"
    }
}

/**
 * A repository of source code hosted using Git.
 */
class GitRepository(

    /**
     * URI pointing to the location of the repository.
     */
    private val uri: URI,

    /**
     * A branch to checkout.
     *
     * By default, points to `master`.
     */
    private val branch: Branch = Branch("master"),
) {
    /**
     * The name of this repository.
     */
    val name: String

    init {
        name = repoName(uri)
    }

    fun prepareCheckout(destinationFolder: File): File {
        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs()
        }

        val result = destinationFolder.toPath().resolve(name)
        Files.createDirectories(result)
        return result.toFile()
    }

    /**
     * Performs the checkout of the source code for this repository
     * to the specified [destinationFolder].
     *
     * The source code is put to the sub-folder named after the repository.
     * E.g. for `https://github.com/acme-org/foobar` the code is placed under
     * the `destinationFolder/foobar` folder.
     *
     * If the supplied folder does not exist, it is created.
     */
    fun checkout(destinationFolder: File): ClonedRepo {
        val preparedFolder = prepareCheckout(destinationFolder).toPath()
        println(
            "Checking out the `$uri` repository at `${branch.name}` " +
                    "to `${preparedFolder.toAbsolutePath()}`."
        )

        Grgit.clone(
            mapOf(
                "dir" to preparedFolder,
                "uri" to uri
            )
        ).checkout(
            mapOf(
                "branch" to branch.name
            )
        )
        return ClonedRepo(this, preparedFolder)
    }

    private fun repoName(resourceLocation: URI): String {
        var path = resourceLocation.path
        if (path.endsWith('/')) {
            path = path.substring(0, path.length - 1)
        }
        val fromLastSlash = path.lastIndexOf('/') + 1
        val repoName = path.substring(fromLastSlash)
        return repoName
    }

    /**
     * Returns a new Git repository pointing to some particular Git [branch].
     */
    fun at(branch: Branch): GitRepository {
        return GitRepository(uri, branch)
    }
}

/**
 * The cloned Git repository.
 */
class ClonedRepo(

    /**
     * Origin Git repository which is cloned.
     */
    private val repo: GitRepository,

    /**
     * The location into which the [repo] is cloned.
     */
    private val location: Path
) {

    /**
     * Replaces the `buildSrc` folder in this cloned repository by the contents
     * of the folder defined by the [source].
     *
     * [source] is expected to be another `buildSrc` folder.
     *
     * The original `buildSrc` folder, if it exists in this cloned repo, is renamed
     * to `buildSrc-original`.
     *
     * Returns this instance of `ClonedRepo`, for call chaining.
     */
    fun replaceBuildSrc(source: Path): ClonedRepo {
        val buildSrc = location.resolve("buildSrc")
        val buildSrcFolder = buildSrc.toFile()
        if (buildSrcFolder.exists() && buildSrcFolder.isDirectory) {
            val toRenameInto = location.resolve("buildSrc-original")
            println("Renaming ${buildSrc.toAbsolutePath()} into ${toRenameInto.toAbsolutePath()}.")
            buildSrcFolder.renameTo(toRenameInto.toFile())
        }
        println("Copying the files from ${source.toAbsolutePath()} into ${buildSrc.toAbsolutePath()}.")
        copyFolder(source, buildSrc)
        return this
    }

    private fun copyFolder(sourceFolder: Path, destinationFolder: Path) {
        try {
            Files.walk(sourceFolder).forEach { file: Path ->
                try {
                    val destination = destinationFolder.resolve(sourceFolder.relativize(file))
                    if (Files.isDirectory(file)) {
                        if (!Files.exists(destination)) Files.createDirectory(destination)
                        return@forEach
                    }
                    Files.copy(file, destination)
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "Error copying folder `$sourceFolder` to `$destinationFolder`.", e
                    )
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Error copying folder `$sourceFolder` to `$destinationFolder`.", e
            )

        }
    }
}

/**
 * Spine repositories at GitHub.
 *
 * The list is expected to grow over time.
 */
object SpineRepos {

    const val libsOrg: String = "https://github.com/SpineEventEngine/"
    const val examplesOrg: String = "https://github.com/spine-examples/"

    val base: URI = library("base")
    val baseTypes: URI = library("base-types")
    val coreJava: URI = library("core-java")
    val web: URI = library("web")

    private fun library(repo: String) = URI(libsOrg + repo)
}

/**
 * A name of a Git branch.
 */
data class Branch(val name: String)
