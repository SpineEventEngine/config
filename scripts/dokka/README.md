### Description

This directory contains a script and serves as a workspace for it. The script generates and publishes
Dokka documentation for 'old'(`<2.0.0`) releases of Spine projects. Documentation is published to 
the `gh-pages` branch of the target repository.

### Important details

- Documentation is generated using [this configuration](../../buildSrc/src/main/kotlin/dokka-for-java.gradle.kts).
- It is assumed that 'old' releases of Spine projects use Java 8.
- The script works with the repository over HTTPS, so if you are not authenticated in `git`, 
  you will be prompted to do so using a username and a personal access token.

### Prerequisites

Prerequisites for the target repository:
- be a Gradle project;
- have at least one release and module.

Prerequisites for running the script:
- have [`git`](https://git-scm.com/downloads) and [`jenv`](https://github.com/jenv/jenv#12-adding-your-java-environment) installed;
- have Java **1.8** installed and [registered](https://github.com/jenv/jenv#12-adding-your-java-environment) in `jenv`;
- have Java **11** installed, registered and [set as global](https://github.com/jenv/jenv#13-setting-a-global-java-version) in `jenv`.

### Usage

The script should be launched from this directory and follow the template provided below:
```Bash
./generate.sh repositoryUrl='' releases='x,y,z*' modules='x,y,z'
```

Description of parameters:
* `repositoryUrl` - a GitHub HTTPS URL.
* `releases` - a list of comma-separated release(tag) names. The release with an asterisk will 
overwrite the published 'primary' one. The release without an asterisk is considered 'secondary', 
so it is published only to the 'v' directory.
* `modules` - a list of comma-separated module names.

An example is provided below:
```Bash
./generate.sh repositoryUrl='https://github.com/SpineEventEngine/core-java.git' releases='v1.7.0,v1.8.0*' modules='core,client'
```

### OS details

The script was developed under and for the macOS. It should not have problems working on a Linux 
distribution. However, it was not meant and tested to do so.