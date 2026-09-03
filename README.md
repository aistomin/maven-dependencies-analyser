# Maven Dependencies Analyser

[![CI](https://github.com/aistomin/maven-dependencies-analyser/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/aistomin/maven-dependencies-analyser/actions/workflows/maven.yml)
[![Hits-of-Code](https://hitsofcode.com/github/aistomin/maven-dependencies-analyser)](https://hitsofcode.com/view/github/aistomin/maven-dependencies-analyser)
[![codecov](https://codecov.io/gh/aistomin/maven-dependencies-analyser/branch/master/graph/badge.svg)](https://codecov.io/gh/aistomin/maven-dependencies-analyser)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.aistomin/maven-dependencies-analyser)](https://central.sonatype.com/artifact/com.github.aistomin/maven-dependencies-analyser)
[![javadoc](https://javadoc.io/badge2/com.github.aistomin/maven-dependencies-analyser/javadoc.svg)](https://javadoc.io/doc/com.github.aistomin/maven-dependencies-analyser)
[![license](https://img.shields.io/github/license/aistomin/maven-dependencies-analyser)](https://github.com/aistomin/maven-dependencies-analyser/blob/master/LICENSE)

Maven plugin that analyses and validates whether all project dependencies are
up to date.

## Getting Started

### System Requirements

- JDK 21 or higher.
- Apache Maven 3.8.3 or higher.

### Validate Project Dependencies

Add the following configuration to your project's `pom.xml`:

```xml
<build>
    <plugins>
        <!-- other plugins are there -->
        <plugin>
            <groupId>com.github.aistomin</groupId>
            <artifactId>maven-dependencies-analyser</artifactId>
            <version>5.2</version>
            <configuration>
                <level>ERROR</level>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>check</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
        <!-- other plugins are there -->
    </plugins>
</build>
```

With this configuration, the Maven build will fail if any of the 
dependencies of your project are out of date. If you do not want the build to
fail, but rather just show a warning, please change the `level` 
configuration value from `ERROR` to `WARNING`. If `level` is not configured
at all, it defaults to `WARNING`.

With the `enabled` configuration, you can easily turn the dependencies 
validation off. The configuration section would then look like this:

```xml
<configuration>
    <level>ERROR</level>
    <enabled>false</enabled>
</configuration>
```

### Parallel Lookups

Every artifact is looked up in Maven Central over the network, and the lookups
run in parallel. The `threads` configuration bounds how many of them are in
flight at the same time, so that a big project stays polite to the repository
instead of firing all of its requests at once:

```xml
<configuration>
    <level>ERROR</level>
    <threads>16</threads>
</configuration>
```

It defaults to `8` and never exceeds the amount of the analysed artifacts. It
has to be a positive number: anything else fails the build regardless of
`level`, because a broken configuration is not the same thing as an outdated
dependency.

### Prerelease Versions

An alpha, a beta, a milestone, a release candidate or a snapshot is not
something a build should be pushed towards, so the plugin does not count them
as upgrades. A project sitting on the newest release is reported as up to date
even when a prerelease of the next version already exists. Otherwise, at the
`ERROR` level, your build would start failing the moment somebody publishes an
alpha.

There is one exception: when the version you declare is itself a prerelease,
the newer prereleases are still reported. Those are the only upgrades that
exist for such a dependency, and you have already opted into that train.

If you do want to hear about the prereleases, set `prereleases` to `true`:

```xml
<configuration>
    <level>ERROR</level>
    <prereleases>true</prereleases>
</configuration>
```

or, for a single build:

```bash
mvn verify -Dmda.prereleases=true
```

Whether a version is a prerelease is decided by Maven's own version ordering
rather than by a list of known qualifiers: a version is a prerelease when
Maven ranks it below the same version without its qualifier. So `2.1.0-alpha1`,
`4.0.0-rc-6`, `6.0.0-M1` and `1.0.0.RC1` are prereleases, while `1.0-sp1`,
`1.0.0.RELEASE` and the build number in `1.0-1` are not. A new qualifier is
therefore understood as soon as Maven itself understands it.

### Ignoring Selected Artifacts

Some pins are deliberate and permanent. A Maven plugin, for example, has to
compile against the oldest Maven it supports, so the `org.apache.maven`
artifacts it depends on will never be "up to date" and the report about them
is never actionable. The `ignores` configuration excludes such artifacts from
the analysis:

```xml
<configuration>
    <level>ERROR</level>
    <ignores>
        <ignore>org.apache.maven:maven-model</ignore>
        <ignore>org.apache.maven:maven-plugin-api</ignore>
        <ignore>org.apache.maven:maven-artifact</ignore>
    </ignores>
</configuration>
```

Each entry is a `groupId:artifactId` coordinate. `*` is accepted in place of
the whole groupId or the whole artifactId, so an entire group can be ignored
at once:

```xml
<ignore>org.apache.maven:*</ignore>
```

Partial patterns like `org.apache.*` are not supported. Anything which is not
a `groupId:artifactId` coordinate fails the build regardless of `level`,
because a broken configuration is not the same thing as an outdated
dependency.

The matching covers the parent, the dependencies and the plugins alike. An
ignored artifact is never reported, never fails the build and is not even
looked up in Maven Central; what was ignored is logged at the `debug` level,
so a surprising report can still be explained.

Like every other parameter, the list can be set from the command line,
comma-separated:

```bash
mvn verify -Dmda.ignores=org.apache.maven:maven-model,org.apache.maven:*
```

### Skip the Analysis

Even in parallel, every artifact is looked up in Maven Central, so on a big
project, or on a slow connection, the analysis still costs some build time. If
you do not need it in a particular build, for example in CI, skip it from the
command line:

```bash
mvn verify -Dmda.skip=true
```

The same flag can be set in the plugin's configuration:

```xml
<configuration>
    <level>ERROR</level>
    <skip>true</skip>
</configuration>
```

`skip` wins over both `level` and `enabled`: when it is set, nothing is
analysed and the build never fails because of an outdated dependency.

All the plugin's parameters can be set from the command line as
`mda.<parameter>`, e.g. `-Dmda.level=ERROR` or `-Dmda.enabled=false`.

### What Is Analysed

The plugin reads the `pom.xml` of the project it runs for and checks the
versions declared in:

- `<parent>`;
- `<dependencies>` and `<dependencyManagement>`;
- `<build><plugins>` and `<build><pluginManagement>`, including the
  `<dependencies>` of every plugin;
- `<build><extensions>`;
- `<reporting><plugins>`;
- all of the above inside every `<profile>`, no matter whether the profile is
  active or not, except `<extensions>`, which Maven allows only in the
  top-level `<build>`.

An artifact that is declared more than once is checked only once.

In a multi-module build every module is analysed against its own `pom.xml`,
not against the one of the directory Maven was started in. That is the
default of the `pom` parameter, `${project.file}` — the pom of the project
the goal runs for. It can be pointed at any other file, e.g.
`-Dmda.pom=path/to/pom.xml`.

Artifacts without a version are skipped, because their version is inherited
from a parent pom. If a version is declared as a `${property}` which can not
be resolved from the pom itself — its `<properties>`, the properties of its
profiles, and the built-in `project.version` and `project.parent.version` —
the artifact is skipped as well and a warning is logged, so that you can see
that the analysis was not complete.

### Run the Plugin Manually

If you want to run the plugin explicitly (instead of binding it to a phase),
execute the following command in your project:

```bash
mvn maven-dependencies-analyser:check
```

Please keep in mind that currently we validate only dependencies that are 
published in the [Maven Central Repository](https://search.maven.org/).

## Licence

The project is licensed under the terms of the 
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

## Have You Found a Bug? Do You Have Any Suggestions?

Although we try our best, we're not robots and bugs are possible :) Also we're
always happy to hear suggestions, ideas, and thoughts from you. Don't
hesitate to [create an issue](https://github.com/aistomin/maven-dependencies-analyser/issues/new). 
It will help us make our project better. Thank you in advance!

## How to Contribute?

Do you want to help us with the project? We will be more than just happy. 
Please: fork the repository, make changes, submit a pull request. We promise
to review your changes in the next couple of days and merge them to the master
branch, if they look correct. To avoid frustration, before sending us your pull
request please run the following command and make sure there are no errors:

```
$ mvn clean install
```

Keep in mind our [system requirements](#system-requirements).
