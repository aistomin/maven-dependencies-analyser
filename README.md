# Maven Dependencies Analyser

[![CI](https://github.com/aistomin/maven-dependencies-analyser/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/aistomin/maven-dependencies-analyser/actions/workflows/maven.yml)
[![Hits-of-Code](https://hitsofcode.com/github/aistomin/maven-dependencies-analyser)](https://hitsofcode.com/view/github/aistomin/maven-dependencies-analyser)
[![codecov](https://codecov.io/gh/aistomin/maven-dependencies-analyser/branch/master/graph/badge.svg)](https://codecov.io/gh/aistomin/maven-dependencies-analyser)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.aistomin/maven-dependencies-analyser)](https://central.sonatype.com/artifact/com.github.aistomin/maven-dependencies-analyser)
[![javadoc](https://javadoc.io/badge2/com.github.aistomin/maven-dependencies-analyser/javadoc.svg)](https://javadoc.io/doc/com.github.aistomin/maven-dependencies-analyser)

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
            <version>5.0</version>
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
configuration value from `ERROR` to `WARNING`.

With the `enabled` configuration, you can easily turn the dependencies 
validation off. The configuration section would then look like this:

```xml
<configuration>
    <level>ERROR</level>
    <enabled>false</enabled>
</configuration>
```

### Skip the Analysis

Every artifact is looked up in Maven Central, so on a big project, or on a slow
connection, the analysis costs a noticeable amount of build time. If you do not
need it in a particular build, for example in CI, skip it from the command
line:

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

The plugin reads your `pom.xml` and checks the versions declared in:

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

Artifacts without a version are skipped, because their version is inherited
from a parent pom. If a version is declared as a `${property}` which can not be
resolved from the pom's own `<properties>`, the artifact is skipped as well and
a warning is logged, so that you can see that the analysis was not complete.

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
$ mvn clean install package javadoc:javadoc
```

Keep in mind our [system requirements](#system-requirements).
