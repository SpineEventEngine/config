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

package io.spine.internal.gradle

import com.google.common.base.Joiner
import java.io.File
import java.io.InputStream
import java.io.StringWriter
import java.lang.System.lineSeparator
import java.nio.file.Files.createTempDirectory
import java.nio.file.Path
import java.util.*
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.property

/**
 * Registers the `updateGitHubPages` task which performs the update of the GitHub Pages
 * with the Javadoc generated for a particular Gradle project. The generated documentation
 * is appended to the `spine.io` site via GitHub pages by pushing commits to the `gh-pages` branch.
 *
 * Please note that the update is only performed for the projects which are NOT snapshots.
 *
 * Users may supply [allowInternalJavadoc][UpdateGitHubPagesExtension.allowInternalJavadoc] option,
 * which if `true`, includes the documentation for types marked `@Internal`.
 * By default, this option is `false`.
 *
 * Usage:
 * ```
 *      configureGitHubPages {
 *
 *          // Include `@Internal`-annotated types.
 *          allowInternalJavadoc.set(true)
 *
 *          // Propagate the full path to the local folder of the repository root.
 *          rootFolder.set(rootDir.absolutePath)
 *      }
 * ```
 *
 * In order to work, the script needs a `deploy_key_rsa` private RSA key file in the repository
 * root. It is recommended to decrypt it in the repository and then decrypt it on CI upon
 * publication. Also, the script uses the `FORMAL_GIT_HUB_PAGES_AUTHOR` environment variable to
 * set the author email for the commits. The `gh-pages` branch itself should exist before the plugin
 * is run.
 *
 * NOTE: when changing the value of "FORMAL_GIT_HUB_PAGES_AUTHOR", one also must change
 * the SSH private (encrypted `deploy_key_rsa`) and the public ("GitHub Pages publisher (Travis CI)"
 * on GitHub) keys.
 *
 * Another requirement is an environment variable `REPO_SLUG`, which is set by the CI environment,
 * such as `Publish` GitHub Actions workflow. It points to the repository for which the update
 * is executed. E.g.:
 *
 * ```
 *      REPO_SLUG: SpineEventEngine/base
 * ```
 *
 * @see UpdateGitHubPagesExtension for the extension which is used to configure this plugin
 */
class UpdateGitHubPages : Plugin<Project> {

    /**
     * Root folder of the repository, to which this `Project` belongs.
     */
    private lateinit var rootFolder: File

    private val tempFolders: MutableList<Path> = mutableListOf()

    companion object {
        /**
         * The name of the task which updates the GitHub Pages.
         */
        const val taskName = "updateGitHubPages"

        /**
         * The name of the helper task to gather the generated Javadoc before updating GitHub Pages.
         */
        const val copyJavadoc = "copyJavadoc"

        /**
         * The name of the helper task which configures the Javadoc processing
         * to exclude `@Internal` types.
         */
        const val noInternalJavadoc = "noInternalJavadoc"

        /**
         * The name of the default Gradle Javadoc task.
         */
        const val defaultJavadoc = "javadoc"

        /**
         * The name of the environment variable that contains the email to use for authoring
         * the commits to the GitHub Pages branch.
         */
        const val formalGitHubPagesAuthorVar = "FORMAL_GIT_HUB_PAGES_AUTHOR"

        /**
         * The name of the environment variable containing the repository slug, for which
         * the Gradle build is performed.
         */
        const val repoSlugVar = "REPO_SLUG"

        /**
         * The branch to use when pushing the updates to the documentation.
         */
        const val gitHubPagesBranch = "gh-pages"
    }

    /**
     * Applies the plugin to the specified [project].
     *
     * If the project version says it is a snapshot, the plugin is not applied.
     */
    override fun apply(project: Project) {

        val extension = UpdateGitHubPagesExtension.create(project)
        project.extensions.add(UpdateGitHubPagesExtension::class, "configureGitHubPages", extension)


        project.afterEvaluate {
            val projectVersion = project.version.toString()
            val isSnapshot = isSnapshot(projectVersion)
            if (isSnapshot) {
                println(
                    "GitHub Pages update will be skipped since this project" +
                            " is a snapshot: `${project.name}-${project.version}`."
                )
            } else {
                registerTasks(extension, project)
            }
        }
    }

    private fun registerTasks(
        extension: UpdateGitHubPagesExtension,
        project: Project
    ) {
        val includeInternal = extension.allowInternalJavadoc()
        rootFolder = extension.rootFolder()
        val tasks = project.tasks
        if (!includeInternal) {
            InternalJavadocFilter.registerTask(noInternalJavadoc, project)
        }
        val javadocOutputPath = newTempFolder("javadoc")
        tempFolders.add(javadocOutputPath);
        registerCopyJavadoc(includeInternal, copyJavadoc, tasks, javadocOutputPath);

        val checkoutTempFolder = newTempFolder("repoTemp")
        tempFolders.add(checkoutTempFolder);
        val updatePagesTask =
            registerUpdateTask(
                project,
                javadocOutputPath,
                checkoutTempFolder
            )

        updatePagesTask.configure {
            dependsOn(copyJavadoc)
        }
    }

    private fun registerUpdateTask(
        project: Project,
        javadocOutputPath: Path,
        checkoutTempFolder: Path
    ): TaskProvider<Task> {
        return project.tasks.register(taskName) {
            try {
                updateGhPages(checkoutTempFolder, project, javadocOutputPath)
            } finally {
                cleanup(tempFolders)
            }
        }
    }

    private fun cleanup(folders: List<Path>) {
        println("Deleting the temp folders.")
        folders.forEach {
            println("Deleting `${it.toAbsolutePath()}`.")
            it.toFile().deleteRecursively()
        }
    }

    private fun Task.updateGhPages(
        checkoutTempFolder: Path,
        project: Project,
        javadocOutputPath: Path
    ) {
        val gitHubAccessKey = gitHubKey(rootFolder)
        val repoSlug = repoSlug()
        val gitHost = gitHost(repoSlug)
        val ghRepoFolder = File("$checkoutTempFolder/$gitHubPagesBranch")
        val docDirPostfix = "reference/$project.name"
        val mostRecentDocDir = File("$ghRepoFolder/$docDirPostfix")
        val versionedDocDir = File("$mostRecentDocDir/v/$project.version")
        val generatedDocs = project.files(javadocOutputPath)

        // Create SSH config file to allow pushing commits to the repository.
        registerSshKey(gitHubAccessKey)
        checkoutDocs(gitHost, ghRepoFolder)

        logger.debug("Replacing the most recent docs in `$mostRecentDocDir`.")
        copyDocs(project, generatedDocs, mostRecentDocDir)

        logger.debug("Storing the new version of docs in the directory `$versionedDocDir`.")
        copyDocs(project, generatedDocs, versionedDocDir)

        Cli(ghRepoFolder).execute("git", "add", docDirPostfix)
        configureCommitter(ghRepoFolder)
        commitAndPush(ghRepoFolder, project)
        logger.debug("The GitHub Pages contents were successfully updated.")
    }

    private fun registerCopyJavadoc(
        allowInternalJavadoc: Boolean,
        taskName: String,
        tasks: TaskContainer,
        javadocDir: Path
    ) {
        tasks.register(taskName, Copy::class.java) {
            if (allowInternalJavadoc) {
                from(tasks.javadocTask(noInternalJavadoc))
            } else {
                from(tasks.javadocTask(defaultJavadoc))
            }

            into(javadocDir)
        }
    }

    private fun commitAndPush(repoBaseDir: File, project: Project) {
        val cli = Cli(repoBaseDir)
        cli.execute(
            "git",
            "commit",
            "--allow-empty",
            "--message=\"Update Javadoc for module ${project.name} as for version ${project.version}\""
        )
        cli.execute("git", "push")
    }

    private fun copyDocs(project: Project, source: FileCollection, destination: File) {
        destination.mkdir()
        project.copy {
            from(source)
            into(destination)
        }
    }

    /**
     * Configures Git to publish the changes under "UpdateGitHubPages Plugin" Git user name
     * and email stored in "FORMAL_GIT_HUB_PAGES_AUTHOR" env variable.
     */
    private fun configureCommitter(repoBaseDir: File) {
        val cli = Cli(repoBaseDir)
        cli.execute("git", "config", "user.name", "\"UpdateGitHubPages Plugin\"")
        val authorEmail = System.getenv(formalGitHubPagesAuthorVar)
        cli.execute("git", "config", "user.email", authorEmail!!)
    }

    private fun checkoutDocs(gitHost: String, repoBaseDir: File) {
        Cli(rootFolder).execute("git", "clone", gitHost, repoBaseDir.absolutePath)
        Cli(repoBaseDir).execute("git", "checkout", gitHubPagesBranch)
    }

    /**
     * Creates an SSH key with the credentials from [gitHubAccessKey]
     * and registers it by invoking the `register-ssh-key.sh` script.
     */
    private fun registerSshKey(gitHubAccessKey: File) {
        val sshConfigFile = File("${System.getProperty("user.home")}/.ssh/config")
        if (!sshConfigFile.exists()) {
            val parentDir = sshConfigFile.canonicalFile.parentFile
            parentDir.mkdirs()
            sshConfigFile.createNewFile()
        }
        sshConfigFile.appendText(
            lineSeparator() +
                    "Host github.com-publish" + lineSeparator() +
                    "User git" + lineSeparator() +
                    "IdentityFile ${gitHubAccessKey.absolutePath}" + lineSeparator()
        )

        Cli(rootFolder).execute(
            "${rootFolder.absolutePath}/config/scripts/register-ssh-key.sh",
            gitHubAccessKey.absolutePath
        )
    }

    /**
     * Reads `REPO_SLUG` environment variable and returns its value.
     *
     * In case it is not set, a [GradleException] is thrown.
     */
    private fun repoSlug(): String {
        val repoSlug = System.getenv(repoSlugVar)
        if (repoSlug == null || repoSlug.isEmpty()) {
            throw GradleException("`REPO_SLUG` environment variable is not set.")
        }
        return repoSlug
    }

    /**
     * Returns the GitHub URL to the project repository.
     *
     * <p>A CI instance comes with an RSA key. However, of course, the default key has no
     * privileges in Spine repositories. Thus, we add our own RSA key — `deploy_rsa_key`.
     * It must have write rights in the associated repository. Also, we don't want that key
     * to be used for anything else but GitHub Pages publishing.
     * Thus, we configure the SSH agent to use the `deploy_rsa_key`
     * only for specific references, namely in `github.com-publish`.
     */
    private fun gitHost(repoSlug: String): String {
        return "git@github.com-publish:${repoSlug}.git"
    }

    /**
     * Locates `deploy_key_rsa` in the passed [rootFolder] and returns it as a [File].
     *
     * If it is not found, a [GradleException] is thrown.
     */
    private fun gitHubKey(rootFolder: File): File {
        val gitHubAccessKey = File("${rootFolder.absolutePath}/deploy_key_rsa")

        if (!gitHubAccessKey.exists()) {
            throw GradleException(
                "File $gitHubAccessKey does not exist. It should be encrypted" +
                        " in the repository and decrypted on CI."
            )
        }
        return gitHubAccessKey
    }

    private fun isSnapshot(version: String): Boolean {
        return version.contains("snapshot", true)
    }
}

/**
 * Executor of CLI commands.
 *
 * Uses the passed [workingFolder] as the directory in which the commands are executed.
 */
class Cli(private val workingFolder: File) {

    /**
     * Executes the given terminal command and retrieves the command output.
     *
     * <p>{@link Runtime#exec(String[], String[], File) Executes} the given {@code String} array as
     * a CLI command. If the execution is successful, returns the command output. Throws
     * an {@link IllegalStateException} otherwise.
     *
     * @param command the command to execute
     * @return the command line output
     * @throws IllegalStateException upon an execution error
     */
    fun execute(vararg command: String): String {
        val outWriter = StringWriter()
        val errWriter = StringWriter()

        val process = ProcessBuilder(*command)
            .directory(workingFolder)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        process.inputStream!!.pourTo(outWriter)
        process.errorStream!!.pourTo(errWriter)
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            return outWriter.toString()
        } else {
            val cmdAsString = Joiner.on(" ").join(command.iterator())
            val errorMsg = "Command `$cmdAsString` finished with exit code $exitCode:" +
                    " ${lineSeparator()}$errWriter" +
                    " ${lineSeparator()}$outWriter."
            throw IllegalStateException(errorMsg)
        }
    }
}

/**
 * A helper routine which configures the GitHub Pages updater to exclude `@Internal` types.
 */
object InternalJavadocFilter {

    /**
     * The name of the custom configuration in scope of which the exclusion of `@Internal` types
     * is performed.
     */
    private const val excludeDocletConfig = "excludeInternalDoclet"

    /**
     * Creates a custom Javadoc task for the [project] which excludes the the types
     * annotated as `@Internal`.
     *
     * The task is registered under the specified [taskName].
     */
    fun registerTask(taskName: String, project: Project) {
        val excludeInternalDoclet = registerConfiguration(project)
        appendCustomJavadocTask(taskName, project, excludeInternalDoclet)
    }

    private fun registerConfiguration(project: Project): Configuration {
        val configurations = project.configurations
        val excludeInternalDoclet = configurations.create(excludeDocletConfig)
        val projectVersion = project.version.toString()
        project.dependencies.add(
            excludeInternalDoclet.name,
            "io.spine.tools:spine-javadoc-filter:$projectVersion"
        )
        return excludeInternalDoclet
    }

    private fun appendCustomJavadocTask(
        taskName: String,
        project: Project,
        excludeInternalDoclet: Configuration
    ) {
        val tasks = project.tasks
        val javadocTask = tasks.javadocTask(UpdateGitHubPages.defaultJavadoc)
        tasks.register(taskName, Javadoc::class.java) {

            source = project.sourceSets().getByName("main").allJava.filter {
                !it.absolutePath.contains("generated")
            }.asFileTree

            classpath = javadocTask.classpath

            options {
                encoding = JavadocConfig.encoding.name

                // Doclet fully qualified name.
                doclet = "io.spine.tools.javadoc.ExcludeInternalDoclet"

                // Path to the JAR containing the doclet.
                docletpath = excludeInternalDoclet.files.toList()
            }

            val docletOptions = options as StandardJavadocDocletOptions
            docletOptions.tags = JavadocConfig.tags.map { it.toString() }
        }
    }
}

/**
 * Obtains the Java plugin extension of the project.
 */
fun Project.javaPluginExtension(): JavaPluginExtension =
    extensions.getByType(JavaPluginExtension::class.java)

/**
 * Obtains source set container of the Java project.
 */
fun Project.sourceSets(): SourceSetContainer = javaPluginExtension().sourceSets

/**
 * Finds a [Javadoc] Gradle task by the passed name.
 */
fun TaskContainer.javadocTask(named: String) = this.getByName(named) as Javadoc

/**
 * Creates a temp folder with the passed [prefix] and configures it to be deleted on JVM exit.
 */
private fun newTempFolder(prefix: String): Path {
    val javadocDir = createTempDirectory(prefix)
    println("Creating the temp directory at `${javadocDir.toAbsolutePath()}`")
    javadocDir.toFile().deleteOnExit()
    return javadocDir
}

/**
 * Asynchronously reads all lines from this [InputStream] and appends them
 * to the passed [StringWriter].
 */
fun InputStream.pourTo(dest: StringWriter) {
    Thread {
        val sc = Scanner(this)
        while (sc.hasNextLine()) {
            dest.append(sc.nextLine())
        }
    }.start()
}

/**
 * The extension for configuring the `UpdateGitHubPages` plugin.
 */
class UpdateGitHubPagesExtension
private constructor(

    /**
     * Tells whether the types marked `@Internal` should be included into the doc generation.
     */
    val allowInternalJavadoc: Property<Boolean>,

    /**
     * The root folder of the repository to which the updated `Project` belongs.
     */
    var rootFolder: Property<File>
) {

    internal companion object {
        fun create(project: Project): UpdateGitHubPagesExtension {
            val factory = project.objects
            return UpdateGitHubPagesExtension(
                allowInternalJavadoc = factory.property(Boolean::class),
                rootFolder = factory.property(File::class),
            )
        }
    }

    /**
     * Returns `true` if the `@Internal`-annotated types should be included into the generated
     * documentation, `false` otherwise.
     */
    fun allowInternalJavadoc(): Boolean {
        return allowInternalJavadoc.get()
    }

    /**
     * Returns the local root folder of the repository, to which the handled Gradle Project belongs.
     */
    fun rootFolder(): File {
        return rootFolder.get()
    }
}

/**
 * Configures the `configureGitHubPages` extension.
 */
@Suppress("unused")
fun Project.configureGitHubPages(action: UpdateGitHubPagesExtension.() -> Unit) {
    apply<UpdateGitHubPages>()

    val extension = extensions.getByType(UpdateGitHubPagesExtension::class)
    extension.action()
}

