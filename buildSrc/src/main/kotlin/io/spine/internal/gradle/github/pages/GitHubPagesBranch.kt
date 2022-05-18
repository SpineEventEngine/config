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
import io.spine.internal.gradle.fs.LazyTempPath
import java.io.File
import org.gradle.api.GradleException

/**
 * Represents the branch used for publishing documentation.
 *
 * The repository's GitHub URL is derived from the `REPO_SLUG` environment variable.
 * The branch dedicated to publishing documentation is automatically checked out in
 * this repository. Also, the username and email of the git committer are
 * automatically configured. The username is set to "UpdateGitHubPages Plugin", and
 * the email is derived from the `FORMAL_GIT_HUB_PAGES_AUTHOR` environment variable.
 *
 * This class provides functionality to commit changes and push them to the
 * remote repository.
 */
internal class GitHubPagesBranch: AutoCloseable {

    /**
     * The branch dedicated to publishing documentation.
     */
    private val targetBranch = "gh-pages"

    /**
     * Path to the temporal folder for a clone of the project repository.
     */
    val repoFolder = LazyTempPath("repoTemp")

    internal constructor(rootProjectFolder: File) {
        SshKey(rootProjectFolder).register()
        clone()
        checkout()
        configureCommitter()
    }

    /**
     * Clones the Git repository with the GitHub URL retrieved from the `REPO_SLUG`
     * environment variable.
     */
    private fun clone() {
        val gitHost = RepoSlug.fromVar().gitHost()
        repoExecute(
            "git",
            "clone",
            gitHost,
            "."
        )
    }

    /** Executes a command in the [repoFolder]. */
    private fun repoExecute(vararg command: String): String = Cli(repoFolder.toFile()).execute(*command)

    /**
     * Configures Git to publish the changes under "UpdateGitHubPages Plugin" Git
     * username and email stored in the `FORMAL_GIT_HUB_PAGES_AUTHOR` environment
     * variable.
     */
    private fun configureCommitter() {
        repoExecute("git", "config", "user.name", "\"UpdateGitHubPages Plugin\"")
        val authorEmail = AuthorEmail.fromVar().toString()
        repoExecute("git", "config", "user.email", authorEmail)
    }

    private fun checkout() {
        repoExecute("git", "checkout", targetBranch)
    }

    /**
     * Stages all changes and commits with the provided message.
     */
    fun commitAllChanges(message: String) {
        stageAllChanges()
        commit(message)
    }

    private fun stageAllChanges() {
        repoExecute("git", "add", "--all")
    }

    private fun commit(message: String) {
        repoExecute(
            "git",
            "commit",
            "--allow-empty",
            "--message=${message}"
        )
    }

    /**
     * Pushes local branch named [targetBranch] to the remote repository.
     */
    fun push() {
        repoExecute("git", "push")
    }

    override fun close() {
        repoFolder.toFile().deleteRecursively()
    }
}

/**
 * Registers SSH key for further operations with GitHub Pages.
 */
private class SshKey(private val rootProjectFolder: File) {

    /**
     * Creates an SSH key with the credentials and registers it by invoking the
     * `register-ssh-key.sh` script.
     */
    fun register() {
        val gitHubAccessKey = gitHubKey()
        val sshConfigFile = sshConfigFile()
        sshConfigFile.appendPublisher(gitHubAccessKey)

        execute(
            "${rootProjectFolder.absolutePath}/config/scripts/register-ssh-key.sh",
            gitHubAccessKey.absolutePath
        )
    }

    /**
     * Locates `deploy_key_rsa` in the [rootProjectFolder] and returns it as a [File].
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
        val gitHubAccessKey = File("${rootProjectFolder.absolutePath}/deploy_key_rsa")

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

    /** Executes a command in the project [rootProjectFolder]. */
    private fun execute(vararg command: String): String = Cli(rootProjectFolder).execute(*command)
}
