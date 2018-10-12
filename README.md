Dependencies and CI configurations shared among sub-projects. 

The code of this repository should be added to a target project as a Git sub-module.

## Adding to your project

To add a sub-module:
```bash
git submodule add https://github.com/SpineEventEngine/config config
``` 
This will only add a sub-module with the reference to the repository. 
In order to get the code, please run:
```bash
git submodule update --init --recursive
```

## Updating project with new configuration

Run the following command from the root of your project.
```bash
./config/pull
```

It will get the latest code from the remote repo, and then copy shared files into the root of your
project. 

The following files will be copied:
 
 * `.idea` directory with shared IDEA settings
 * `.codacy.yaml`
 * `.codecov.yml`
 * `.gitattributes`
 * `.gitignore`
 * `ext.gradle`
 
    This fill will be copied only if it does not exist in your project. It defines:
    1. the version of Spine Base which is used by the project (which uses `config`)
    2. the version under which artifacts of the project will be published.

## Adding credentials to new repository

Please see [this page](https://github.com/SpineEventEngine/config/wiki/Encrypting-Credential-Files-for-Travis).

## Further reading

  * [GitHub: Working with submodules](https://blog.github.com/2016-02-01-working-with-submodules/)
  * [Pro Git: Git Tools - Git Submodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules)
