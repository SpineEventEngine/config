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
./config/update
```

The following files will be copied to the root directory of a project 
which shares the configuration:

 * `.codacy.yaml`
 * `.codecov.yml`
 * `.gitattributes`
 * `.gitignore`
 * `ext.gradle`
 
    This fill will be copied only if it does not exist in your project. It defines:
    1. the version of Spine Base which is used by the project (which uses `config`)
    2. the version under which artifacts of the project will be published.
     
## Updating the `config` directory in your project 

In order to get point the `config` submodule to the latest version in the repository, please run: 
```bash
git submodule update
```
In this state the repository under `config` still does not have a current branch. 

You'll need to set the current branch. Please note that these operations are performed inside 
the `config` directory.

```bash
cd config
git checkout master
```

Once you have the current branch, pull the changes from the server via IDEA or command line:
```bash
git pull
``` 

Make sure to return to the root of your project:
```bash
cd ..
```
## Further reading

  * [GitHub: Working with submodules](https://blog.github.com/2016-02-01-working-with-submodules/)
  * [Pro Git: Git Tools - Git Submodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules)
