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

package io.spine.gradle.publish

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import io.spine.gradle.VersionComparator
import io.spine.gradle.repo.Repository
import java.io.File
import java.io.FileNotFoundException
import java.net.URI
import java.net.URL
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * A task that verifies the project version is fit to be published.
 *
 * Two independent checks run:
 *
 *  1. [checkIncrementedAgainstBase] — for a GitHub Actions pull request, the project
 *     [version] must be strictly greater than the version declared by `version.gradle.kts`
 *     on the PR's base branch. This is deterministic and network-independent: it catches a
 *     behavior-changing PR that forgot to bump, and two parallel PRs that bumped to the
 *     same value, regardless of what is (or is not yet) published.
 *  2. [checkNotPublished] — the [version] must not already exist in the target Maven
 *     repository, so a publication cannot overwrite an immutable artifact.
 *
 * The two checks are complementary; neither subsumes the other.
 */
open class CheckVersionIncrement : DefaultTask() {

    /**
     * The Maven repository in which to look for published artifacts.
     *
     * We check both the `releases` and `snapshots` repositories. Artifacts in either of these repos
     * may not be overwritten.
     */
    @Input
    lateinit var repository: Repository

    @Input
    val version: String = project.version as String

    @TaskAction
    fun checkVersion() {
        checkIncrementedAgainstBase()
        checkNotPublished()
    }

    /**
     * Verifies that the project [version] is strictly greater than the version declared by
     * `version.gradle.kts` on the pull request's base branch.
     *
     * The check applies only inside a GitHub Actions pull request (when `GITHUB_BASE_REF`
     * is set); local Maven Local publishes rely on [checkNotPublished] instead. The base
     * branch tip is read with `git show origin/<base>:version.gradle.kts`, so the Version
     * Guard workflow must fetch the base ref before running the task.
     *
     * Failure modes are deliberately asymmetric:
     *  - base ref unresolvable — **fail closed** (a workflow misconfiguration must not pass
     *    silently);
     *  - `version.gradle.kts` absent on base — treated as a newly introduced file (**pass**);
     *  - the publishing-version property cannot be identified — **skip** with a warning,
     *    leaving [checkNotPublished] as the remaining guard, rather than blocking every PR in
     *    a repository whose `version.gradle.kts` uses an unrecognized shape.
     */
    private fun checkIncrementedAgainstBase() {
        val baseRef = System.getenv("GITHUB_BASE_REF")
        if (baseRef.isNullOrBlank()) {
            // Not a GitHub Actions pull request; `checkNotPublished` is the relevant guard.
            return
        }
        val baseVersion = baseVersionToCompare(baseRef)
        if (baseVersion != null && VersionComparator.compare(version, baseVersion) <= 0) {
            throw GradleException(
                """
                The project version `$version` is not greater than the base branch version
                `$baseVersion` (base `$baseRef`).

                A pull request that merges into `$baseRef` must increment the version in
                `$VERSION_FILE`. Publishing runs on every push to the base branch, so a
                non-incremented version would collide with the already-published artifact.

                Bump the version (e.g. run `/bump-version`) and push again.

                To disable this check, run Gradle with `-x $name`.
                """.trimIndent()
            )
        }
    }

    /**
     * Resolves the base-branch publishing version to compare [version] against, or `null`
     * when the comparison does not apply.
     *
     * Returns `null` (skipping the check) when the publishing-version property cannot be
     * identified in the working-tree `version.gradle.kts`, or when the base branch has no
     * comparable value (the file is absent or newly introduced). Throws via [baseVersionFile]
     * when the base ref itself cannot be resolved.
     */
    private fun baseVersionToCompare(baseRef: String): String? {
        val headContent = headVersionFile(project.rootDir)
        val key = headContent?.let { VersionGradleFile.keyForValue(it, version) }
        if (key == null) {
            logger.warn(
                "Could not identify the publishing-version property matching `$version` in " +
                    "`$VERSION_FILE`; skipping the base-branch increment check."
            )
            return null
        }
        val baseContent = baseVersionFile(project.rootDir, baseRef)
        val baseVersion = baseContent?.let { VersionGradleFile.valueForKey(it, key) }
        if (baseVersion == null) {
            logger.info(
                "No comparable `$key` in `$VERSION_FILE` on base `$baseRef` (absent or newly " +
                    "introduced); skipping the base-branch increment check."
            )
        }
        return baseVersion
    }

    /**
     * Verifies that the current [version] has not been published to the target Maven
     * repository yet.
     *
     * Both the `releases` and `snapshots` repositories are checked; artifacts in either
     * may not be overwritten.
     */
    private fun checkNotPublished() {
        val artifact = "${project.artifactPath()}/${MavenMetadata.FILE_NAME}"
        val snapshots = repository.target(snapshots = true)
        checkInRepo(snapshots, artifact)

        if (!repository.hasOneTarget()) {
            checkInRepo(repository.target(snapshots = false), artifact)
        }
    }

    private fun checkInRepo(repoUrl: String, artifact: String) {
        val metadata = fetch(repoUrl, artifact)
        val versions = metadata?.versioning?.versions
        val versionExists = versions?.contains(version) ?: false
        if (versionExists) {
            throw GradleException(
                    """
                    The version `$version` is already published to the Maven repository `$repoUrl`.
                    Try incrementing the library version.
                    All available versions are: ${versions.joinToString(separator = ", ")}.

                    To disable this check, run Gradle with `-x $name`.
                    """.trimIndent()
            )
        }
    }

    private fun fetch(repository: String, artifact: String): MavenMetadata? {
        val url = URI.create("$repository/$artifact").toURL()
        return MavenMetadata.fetchAndParse(url)
    }

    private fun Project.artifactPath(): String {
        val group = this.group as String
        val name = "${artifactPrefix()}${this.name}"

        val pathElements = ArrayList(group.split('.'))
        pathElements.add(name)
        val path = pathElements.joinToString(separator = "/")
        return path
    }

    /**
     * Returns the artifact prefix used for the publishing of this project.
     *
     * All current Spine modules should be using `SpinePublishing`.
     * Therefore, the corresponding extension should be present in the root project.
     * However, just in case, we define the "standard" prefix here as well.
     *
     * This value MUST be the same as defined by the defaults in `SpinePublishing`.
     */
    private fun Project.artifactPrefix(): String {
        val ext = rootProject.extensions.findByType(SpinePublishing::class.java)
        val result = ext?.artifactPrefix ?: SpinePublishing.DEFAULT_PREFIX
        return result
    }
}

private data class MavenMetadata(var versioning: Versioning = Versioning()) {

    companion object {

        const val FILE_NAME = "maven-metadata.xml"

        private val mapper = XmlMapper()

        init {
            mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        /**
         * Fetches the metadata for the repository and parses the document.
         *
         * <p>If the document could not be found, assumes that the module was never
         * released and thus has no metadata.
         */
        fun fetchAndParse(url: URL): MavenMetadata? {
            return try {
                val metadata = mapper.readValue(url, MavenMetadata::class.java)
                metadata
            } catch (_: FileNotFoundException) {
                null
            }
        }
    }
}

private data class Versioning(var versions: List<String> = listOf())

private const val VERSION_FILE = "version.gradle.kts"

private data class GitResult(val exitCode: Int, val stdout: String, val stderr: String)

private fun headVersionFile(rootDir: File): String? =
    File(rootDir, VERSION_FILE).takeIf { it.exists() }?.readText()

/**
 * Reads `version.gradle.kts` from the tip of the `origin/<baseRef>` remote-tracking branch.
 *
 * Returns `null` when the file does not exist at the base — a newly introduced version file.
 * Throws a [GradleException] when the base ref cannot be resolved: the Version Guard workflow
 * is responsible for fetching it, and failing closed surfaces that misconfiguration instead
 * of silently passing the check.
 */
private fun baseVersionFile(rootDir: File, baseRef: String): String? {
    val result = gitShow(rootDir, "origin/$baseRef:$VERSION_FILE")
    if (result.exitCode == 0) {
        return result.stdout
    }
    // `git show` reports a missing path with these phrasings; everything else
    // (e.g. an unresolvable ref) is a configuration error we must not swallow.
    val missingPath = result.stderr.contains("does not exist") ||
            result.stderr.contains("exists on disk, but not in")
    if (missingPath) {
        return null
    }
    throw GradleException(
        "Unable to read `$VERSION_FILE` from base `origin/$baseRef` " +
                "(git exit code ${result.exitCode}): ${result.stderr.trim()}.\n" +
                "Ensure the Version Guard workflow fetches the base branch before this check."
    )
}

private fun gitShow(rootDir: File, spec: String): GitResult {
    // Redirect to files rather than reading the process pipes sequentially: draining
    // stdout fully before stderr can deadlock if a stream fills its pipe buffer.
    val outFile = File.createTempFile("git-show", ".out")
    val errFile = File.createTempFile("git-show", ".err")
    try {
        val exitCode = ProcessBuilder("git", "show", spec)
            .directory(rootDir)
            .redirectOutput(outFile)
            .redirectError(errFile)
            .start()
            .waitFor()
        return GitResult(exitCode, outFile.readText(), errFile.readText())
    } finally {
        outFile.delete()
        errFile.delete()
    }
}
