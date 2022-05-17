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

    val updateJavadoc = with(plugin) {
        UpdateJavadoc(project, rootFolder, checkoutTempFolder, javadocOutputFolder, logger)
    }
    updateJavadoc.run()

    val updateDokka = with(plugin) {
        UpdateDokka(project, rootFolder, checkoutTempFolder, dokkaOutputFolder, logger)
    }
    updateDokka.run()
}

private abstract class UpdateOperation(
    private val project: Project,
    private val rootFolder: File,
    private val checkoutTempFolder: Path,
    private val docsOutputFolder: Path,
    private val logger: Logger
) {

    /**
     * The folder under the repository's root(`/`) for storing documentation.
     *
     * The value should not contain any leading or trailing file separators.
     *
     * The absolute path to the project's documentation is made by appending its
     * name to the end, making `/[documentationRoot]/[project.name]`.
     */
    protected abstract val documentationRoot: String

    /**
     * The name of the tool used to generate the documentation to update.
     *
     * This name will appear in logs as part of a message.
     */
    protected abstract val toolName: String

    private val ghRepoFolder: File = File("${checkoutTempFolder}/${Branch.ghPages}")
    private val mostRecentFolder = File("${ghRepoFolder}/${documentationRoot}/${project.name}")

    fun run() {
        logger.debug("Update of the ${toolName} documentation for `${project.name}` started.")
        SshKey(rootFolder).register()
        checkoutDocs()

        val documentation = replaceMostRecentDocs()
        copyIntoVersionDir(documentation)

        commitAndPush()
        logger.debug("Update of the ${toolName} documentation for `${project.name}` successfully finished.")

        cleanup()
    }

    private fun checkoutDocs() {
        val gitHost = RepoSlug.fromVar().gitHost()

        execute("git", "clone", gitHost, ghRepoFolder.absolutePath)
        pagesExecute("git", "checkout", Branch.ghPages)
    }

    /** Executes a command in the project [rootFolder]. */
    private fun execute(vararg command: String): String = Cli(rootFolder).execute(*command)

    /** Executes a command in the [ghRepoFolder]. */
    private fun pagesExecute(vararg command: String): String = Cli(ghRepoFolder).execute(*command)

    private fun replaceMostRecentDocs(): ConfigurableFileCollection {
        val generatedDocs = project.files(docsOutputFolder)

        logger.debug("Replacing the most recent ${toolName} documentation in ${mostRecentFolder}.")
        copyDocs(generatedDocs, mostRecentFolder)

        return generatedDocs
    }

    private fun copyDocs(source: FileCollection, destination: File) {
        destination.mkdir()
        project.copy {
            from(source)
            into(destination)
        }
    }

    private fun copyIntoVersionDir(generatedDocs: ConfigurableFileCollection) {
        val versionedDocDir = File("$mostRecentFolder/v/${project.version}")

        logger.debug("Storing the new version of ${toolName} documentation in `${versionedDocDir}.")
        copyDocs(generatedDocs, versionedDocDir)
    }

    private fun commitAndPush() {
        stageChanges()
        configureCommitter()
        commit()
        push()
    }

    private fun stageChanges() {
        pagesExecute("git", "add", documentationRoot)
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

    private fun commit() {
        val updateMesssage = "Update ${toolName} documentation for ${project.name} as for version" +
                " ${project.version}"

        pagesExecute(
            "git",
            "commit",
            "--allow-empty",
            "--message=${updateMesssage}"
        )
    }

    private fun push() {
        pagesExecute("git", "push")
    }

    private fun cleanup() {
        ghRepoFolder.deleteRecursively();
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
        sshConfigFile.appendPublisher(gitHubAccessKey)

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

    private fun File.appendPublisher(privateKey: File) {
        val nl = System.lineSeparator()
        this.appendText(
            nl +
                    "Host github.com-publish" + nl +
                    "User git" + nl +
                    "IdentityFile ${privateKey.absolutePath}" + nl
        )
    }

    /** Executes a command in the project [rootFolder]. */
    private fun execute(vararg command: String): String = Cli(rootFolder).execute(*command)
}

private class UpdateJavadoc(
    project: Project,
    rootFolder: File,
    checkoutTempFolder: Path,
    docsOutputFolder: Path,
    logger: Logger
) : UpdateOperation(project, rootFolder, checkoutTempFolder, docsOutputFolder, logger) {

    override val documentationRoot: String
        get() = "reference"
    override val toolName: String
        get() = "Javadoc"
}

private class UpdateDokka(
    project: Project,
    rootFolder: File,
    checkoutTempFolder: Path,
    docsOutputFolder: Path,
    logger: Logger
) : UpdateOperation(project, rootFolder, checkoutTempFolder, docsOutputFolder, logger) {

    override val documentationRoot: String
        get() = "dokka-reference"
    override val toolName: String
        get() = "Dokka"
}
