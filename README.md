Dependencies and build configurations shared among sub-projects.

The following files are to be copied to the root directory of a project 
which shares the configuration:

 * `.codacy.yaml`
 * `codecov.yml`
 * `gitattributes`
 * `gitignore`
 * `.travis._yml` — to be renamed to `.travis.yml`. 
    The file extension in this repository uses the underscore prefix to prevent Travis builds
    of this repository.
 * `ext.gradle` — defines:
    1. the version of Spine Base which is used by the project (which uses `config`)
    2. the version under which artifacts of the project will be published.
    
