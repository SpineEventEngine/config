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

package io.spine.internal.gradle.github.pages

import io.spine.internal.gradle.Cli
import io.spine.internal.gradle.RepoSlug
import java.io.File
import java.nio.file.Path
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger

/**
 * Performs the update of GitHub pages.
 */
fun Task.updateGhPages(project: Project) {
    val plugin = project.plugins.getPlugin(UpdateGitHubPages::class.java)
    val op = with(plugin) {
        Operation(project, rootFolder, checkoutTempFolder, javadocOutputPath, dokkaOutputPath, logger)
    }
    op.run()
}

private class Operation(
    private val project: Project,
    private val rootFolder: File,
    checkoutTempFolder: Path,
    private val javadocOutputPath: Path,
    private val dokkaOutputPath: Path,
    private val logger: Logger
) {

    private val ghRepoFolder: File = File("${checkoutTempFolder}/${Branch.ghPages}")

    private val javadocDirPostfix = "reference/${project.name}"
    private val dokkaDirPostfix = "dokka-reference/${project.name}"

    private val mostRecentJavadocDir = File("$ghRepoFolder/$javadocDirPostfix")
    private val mostRecentDokkaDir = File("$ghRepoFolder/$dokkaDirPostfix")

    fun run() {
        SshKey(rootFolder).register()
        checkoutDocs()

        val generatedJavadoc = replaceMostRecentJavadoc()
        copyIntoJavadocVersionDir(generatedJavadoc)

        val generatedDokka = replaceMostRecentDokka()
        copyIntoDokkaVersionDir(generatedDokka)

        addCommitAndPush()
        logger.debug("The GitHub Pages contents were successfully updated.")
    }

    private fun checkoutDocs() {
        val gitHost = RepoSlug.fromVar().gitHost()

        execute("git", "clone", gitHost, ghRepoFolder.absolutePath)
        pagesExecute("git", "checkout", Branch.ghPages)
    }

    /** Executes a command in the project [rootFolder]. */
    private fun execute(vararg command: String): String = Cli(rootFolder).execute(*command)

    /** Executes a command in the [ghRepoFolder] */
    private fun pagesExecute(vararg command: String): String = Cli(ghRepoFolder).execute(*command)

    private fun replaceMostRecentJavadoc(): ConfigurableFileCollection {
        val generatedDocs = project.files(javadocOutputPath)

        logger.debug("Replacing the most recent Javadoc in `$mostRecentJavadocDir`.")
        copyDocs(generatedDocs, mostRecentJavadocDir)

        return generatedDocs
    }

    private fun copyDocs(source: FileCollection, destination: File) {
        destination.mkdir()
        project.copy {
            from(source)
            into(destination)
        }
    }

    private fun copyIntoJavadocVersionDir(generatedDocs: ConfigurableFileCollection) {
        val versionedDocDir = File("$mostRecentJavadocDir/v/${project.version}")

        logger.debug("Storing the new version of Javadoc in `$versionedDocDir`.")
        copyDocs(generatedDocs, versionedDocDir)
    }

    private fun replaceMostRecentDokka(): ConfigurableFileCollection {
        val generatedDocs = project.files(dokkaOutputPath)

        logger.debug("Replacing the most recent Dokka documentation in `$mostRecentDokkaDir`.")
        copyDocs(generatedDocs, mostRecentDokkaDir)

        return generatedDocs
    }

    private fun copyIntoDokkaVersionDir(generatedDocs: ConfigurableFileCollection) {
        val versionedDocDir = File("$mostRecentDokkaDir/v/${project.version}")

        logger.debug("Storing the new version of Dokka documentation in `$versionedDocDir`.")
        copyDocs(generatedDocs, versionedDocDir)
    }

    private fun addCommitAndPush() {
        pagesExecute("git", "add", javadocDirPostfix, dokkaDirPostfix)
        configureCommitter()
        commitAndPush()
    }

    /**
     * Configures Git to publish the changes under "UpdateGitHubPages Plugin" Git
     * user name and email stored in "FORMAL_GIT_HUB_PAGES_AUTHOR" env variable.
     */
    private fun configureCommitter() {
        pagesExecute("git", "config", "user.name", "\"UpdateGitHubPages Plugin\"")
        val authorEmail = AuthorEmail.fromVar().toString()
        pagesExecute("git", "config", "user.email", authorEmail)
    }

    private fun commitAndPush() {
        pagesExecute(
            "git",
            "commit",
            "--allow-empty",
            "--message=\"Update documentation for module ${project.name}" +
                    " as for version ${project.version}\""
        )
        pagesExecute("git", "push")
    }
}

/**
 * Registers SSH key for further operations with GitHub Pages.
 */
private class SshKey(private val rootFolder: File) {

    /**
     * Creates an SSH key with the credentials and registers it by invoking the
     * `register-ssh-key.sh` script.
     */
    fun register() {
        val gitHubAccessKey = gitHubKey()
        val sshConfigFile = sshConfigFile()
        val nl = System.lineSeparator()
        sshConfigFile.appendText(
            nl +
                    "Host github.com-publish" + nl +
                    "User git" + nl +
                    "IdentityFile ${gitHubAccessKey.absolutePath}" + nl
        )

        execute(
            "${rootFolder.absolutePath}/config/scripts/register-ssh-key.sh",
            gitHubAccessKey.absolutePath
        )
    }

    /**
     * Locates `deploy_key_rsa` in the [rootFolder] and returns it as a [File].
     *
     * If it is not found, a [GradleException] is thrown.
     *
     * A CI instance comes with an RSA key. However, of course, the default key has
     * no privileges in Spine repositories. Thus, we add our own RSA key —
     * `deploy_rsa_key`. It must have `write` rights in the associated repository.
     * Also, we don't want that key to be used for anything else but GitHub Pages
     * publishing.
     *
     * Thus, we configure the SSH agent to use the `deploy_rsa_key` only for specific
     * references, namely in `github.com-publish`.
     */
    private fun gitHubKey(): File {
        val gitHubAccessKey = File("${rootFolder.absolutePath}/deploy_key_rsa")

        if (!gitHubAccessKey.exists()) {
            throw GradleException(
                "File $gitHubAccessKey does not exist. It should be encrypted" +
                        " in the repository and decrypted on CI."
            )
        }
        return gitHubAccessKey
    }

    private fun sshConfigFile(): File {
        val sshConfigFile = File("${System.getProperty("user.home")}/.ssh/config")
        if (!sshConfigFile.exists()) {
            val parentDir = sshConfigFile.canonicalFile.parentFile
            parentDir.mkdirs()
            sshConfigFile.createNewFile()
        }
        return sshConfigFile
    }

    /** Executes a command in the project [rootFolder]. */
    private fun execute(vararg command: String): String = Cli(rootFolder).execute(*command)
}
