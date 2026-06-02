In Spine, dependencies and CI configurations are shared among the subprojects. 

The code of this repository should be added to a target project as a Git submodule.

## Adding a submodule to your project

To add a submodule:
```bash
git submodule add https://github.com/SpineEventEngine/config config
``` 
This will only add a submodule with the reference to the repository.

To get the actual code for the `config` submodule, run the following command:
```bash
git submodule update --init --recursive
```

## Updating the project with a new configuration

Run the following command from the root of your project:
```bash
./config/pull
```

It will get the latest code from the remote repo and then copy the shared files into your project.
The following files will be copied:

 * `.idea` — shared IntelliJ IDEA settings.
 * `.codecov.yml`
 * `.gitattributes` and `.gitignore` (created on first run if absent; `.gitignore` is overwritten on update).
 * `.github` — created on first run if absent. GitHub workflows from `.github-workflows/` are then merged into `.github/workflows/` on every update.
 * `buildSrc` — common build-time code, in Kotlin. `module.gradle.kts` in the consuming repo is preserved.
 * `gradle/`, `gradlew`, `gradlew.bat` — the Gradle Wrapper.
 * `gradle.properties` — overwritten on update.
 * `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`.

### AI agent configuration

The `pull` script also wires up AI-agent configuration:

 * `AGENTS.md` and `CLAUDE.md` — copied entry points that direct any agent to
   `.agents/guidelines/_TOC.md`.
 * Shared **skills, scripts, and guidelines are _not_ copied.** They live in the
   [`SpineEventEngine/agents`][agents-repo] repository, mounted as a floating Git submodule
   at `.agents/shared` (tracking `master`) and exposed through symlinks: `.agents/skills`,
   `.agents/scripts`, `.agents/guidelines`, `.claude/commands`, and `.claude/agents` — plus
   `.claude/skills` and `.junie/skills`, which alias `.agents/skills`. `pull`
   runs the idempotent [`adopt-shared-agents`](./adopt-shared-agents) script, which sets up
   the submodule on the first run and floats it to the latest `agents@master` on every
   subsequent run — so shared skills update everywhere with **no file churn** in consumer
   pull requests.
 * `.claude/settings.json` — the permission allowlist distributed by `config` (Hugo-only
   repos receive a Hugo-tuned variant). JVM and mixed repos also get `settings.local.json`;
   Hugo-only repos do not.
 * `.junie/guidelines.md` — JetBrains Junie guidelines.

Per-repo content is never overwritten: `docs/project.md` (linked from `.agents/project.md`),
`.agents/memory/`, and `.agents/tasks/`.

The single source of truth for each workflow is its `SKILL.md` in the
[`agents`][agents-repo] repository; the Claude slash commands
and subagents are thin wrappers that point Claude Code at those files.
 
## Checking updated configuration

When changing the configuration (e.g. a version of a dependency, or adding a build script plugin),
it may be worth testing that the change does not break dependant projects. `ConfigTester` allows
 automating this process. This tool serves to probe the Spine repositories for compatibility with
the local changes in the `config` repository. The usage looks like this:

```kotlin
// A reference to `config` to use along with the `ConfigTester`.
val config = Paths.get("./")

// A temp folder to use to check out the sources of other repositories with the `ConfigTester`.
val tempFolder = File("./tmp")

// Creates a Gradle task which checks out and builds the selected Spine repositories
// with the local version of `config` and `config/buildSrc`.
ConfigTester(config, tasks, tempFolder)
    .addRepo(SpineRepos.baseTypes)  // Builds `base-types` at `master`.
    .addRepo(SpineRepos.base)       // Builds `base` at `master`.
    .addRepo(SpineRepos.coreJava)   // Builds `core-java` at `master`.

    // This is how one builds a specific branch of some repository:
    // .addRepo(SpineRepos.coreJava, Branch("grpc-concurrency-fixes"))

    // Register the produced task under the selected name to invoke manually upon need.
    .registerUnder("buildDependants")
```

The [`build.gradle.kts`](./build.gradle.kts) is already tuned to test changes
against these projects: 
 * [`base`][base],
 * [`base-types`][base-types], and
 * [`core-java`][core-java].

This takes slightly over half an hour, depending on the local configuration.
If you need to change the list of repositories, please update `addRepo()` calls to `ConifigTester`.

The command to start the build process is:
```bash
./gradlew clean buildDependants 
```

## `.github-workflows` directory

This directory contains GitHub Workflow scripts that do not apply to the `config` repository, and
as such cannot be placed under `.github/workflows`.

These scripts are copied by the `pull` script when `config` is applied to a new repository.

## Further reading

  * [GitHub: Working with submodules][working-with-submodules]
  * [Pro Git: Git Tools - Git Submodules][submodule-tools]
  
[agents-repo]: https://github.com/SpineEventEngine/agents
[base]: https://github.com/SpineEventEngine/base
[base-types]: https://github.com/SpineEventEngine/base-types
[core-java]: https://github.com/SpineEventEngine/core-java
[working-with-submodules]: https://blog.github.com/2016-02-01-working-with-submodules
[submodule-tools]: https://git-scm.com/book/en/v2/Git-Tools-Submodules 
