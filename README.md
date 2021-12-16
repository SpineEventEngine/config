In Spine, dependencies and CI configurations are shared among the sub-projects. 

The code of this repository should be added to a target project as a Git sub-module.

## Adding a sub-module to your project

To add a sub-module:
```bash
git submodule add https://github.com/SpineEventEngine/config config
``` 
This will only add a sub-module with the reference to the repository.

In order to get the actual code for the `config` submodule, run the following command:
```bash
git submodule update --init --recursive
```

## Updating project with new configuration

Run the following command from the root of your project:
```bash
./config/pull
```

It will get the latest code from the remote repo, and then copy shared files into the root of your
project. The following files will be copied:
 
 * `.idea` - a directory with shared IntelliJ IDEA settings
 * `.codecov.yml`
 * `.gitattributes`
 * `.gitignore`
 * `buildSrc` — a folder containing the common build-time code, in Kotlin.
 
## Checking updated configuration

When changing the configuration (e.g. a version of a dependency, or adding a build script plugin),
it may be worth testing that the change does not break dependant projects. `ConfigTester` allows
to automate this process. This tool serves to probe the Spine repositories for compatibility with
the local changes in the `config` repository. The usage looks like this:

```kotlin
// A reference to `config` to use along with the `ConfigTester`.
val config = Paths.get("./")

// A temp folder to use to checkout the sources of other repositories with the `ConfigTester`.
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

The [`build.gradle.kts`](./build.gradle.kts) is already tuned to test changes against [`base`][base],
[`base-types`][base-types], and [`core-java`][core-java]. This takes slightly over half an hour,
depending on the local configuration. If you need to change the list of repositories, please
update `addRepo()` calls to `ConifigTester`.

The command to start the build process is:
```bash
./gradlew clean buildDependants 
```

## `.github-workflows` directory

This directory contains GitHub Workflow scripts that do not apply to the `config` repository, and
as such cannot be placed under `.github/workflows`.

These scripts are copied by the `pull` script when `config` is applied to a new repository.

## Further reading

  * [GitHub: Working with submodules](https://blog.github.com/2016-02-01-working-with-submodules/)
  * [Pro Git: Git Tools - Git Submodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules)
  
[base]: https://github.com/SpineEventEngine/base
[base-types]: https://github.com/SpineEventEngine/base-types
[core-java]: https://github.com/SpineEventEngine/core-java
