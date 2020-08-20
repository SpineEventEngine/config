In Spine, dependencies and CI configurations are shared among the sub-projects. 

The code of this repository should be added to a target project as a Git sub-module.

# This is 2.x `master` branch!

This branch contains the configuration for the development-only 2.x branches of the Spine libraries.

It must **NOT** be merged to the `master`, at least until the release of Spine 2.0.0.

The configuration in this branch is **not production-ready**. 

This branch must be treated as `master` for the configuration required during 
the Spine 2.0.0 development. Any the changes to it must go through the PR review process.
 
## `config` submodule in 2.x branch

The common configuration files are located in the [SpineEventEngine/config](https://github.com/SpineEventEngine/config)
repository. In scope of 2.x development a special `2.x-jdk8-master` branch has been created 
in the `config` repository as well. It has been connected to the current repository as a submodule.

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
 * `ext.gradle`
 
    This file will be copied only if it does not exist in your project. It defines the following:
    1. the version of Spine Base which is used by the project (which uses `config`)
    2. the version under which artifacts of the project will be published.

## Adding credentials to a new repository

For details, see [this page](https://github.com/SpineEventEngine/config/wiki/Encrypting-Credential-Files-for-Travis).

## Further reading

  * [GitHub: Working with submodules](https://blog.github.com/2016-02-01-working-with-submodules/)
  * [Pro Git: Git Tools - Git Submodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules)
