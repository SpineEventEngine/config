Dependencies and build configurations shared among sub-projects.

## Updating project configuration

Run the following command from the root of your project.
```bash
./config/update
```

The following files will be copied to the root directory of a project 
which shares the configuration:

 * `.codacy.yaml`
 * `codecov.yml`
 * `gitattributes`
 * `gitignore`
 * `.travis.yml` 
 
    The file extension of `.travis._yml` in this repository uses the underscore prefix to prevent 
    unnecessary Travis builds of this repository.
 * `ext.gradle`
 
    This fill will be copied only if it does not exist in your project. It defines:
    1. the version of Spine Base which is used by the project (which uses `config`)
    2. the version under which artifacts of the project will be published.
     
