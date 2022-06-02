### Description

This directory contains a script and serves as a workspace for it. The script generates and publishes
Dokka documentation for 'old' releases of Spine projects. Documentation is published to `gh-pages` 
of the target repository on behalf of the local git user. 

### Important details

- Documentation is generated following [this configuration](buildSrc/src/main/kotlin/dokka-for-java.gradle.kts).
- It is assumed that 'old' releases of Spine projects use Java 8, as Java 11 is planned for future(`>2.0.0`) releases.

### Prerequisites

Prerequisites for the target repository:
- be a Gradle project;
- have at least one release and module.

Prerequisites for running the script:
- have [`git`](https://git-scm.com/downloads) and [`jenv`](https://github.com/jenv/jenv#12-adding-your-java-environment) installed;
- have Java **1.8** [registered](https://github.com/jenv/jenv#12-adding-your-java-environment) in `jenv`;
- be authenticated in `git`.

### Usage

The script should be launched from this directory and follow the template provided below:
```Bash
./generate.sh repositoryUrl='' releases='x,y,z' modules='x,y,z'
```

Description of parameters:
* `repositoryUrl` - a GitHub HTTPS URL. 
* `releases` - a list of comma-separated release(tag) names. 
* `modules` - a list of comma-separated module names.

An example is provided below:
```Bash
./generate.sh repositoryUrl='https://github.com/SpineEventEngine/core-java.git' releases='v1.8.0,v1.7.0' modules='core,client'
```

### OS details

The script was developed under and for the macOS. 
It should not have problems working on a Linux distro, however it was not meant and tested to do so.
