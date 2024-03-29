# Maven Dependencies Analyser

[![CI](https://github.com/aistomin/maven-dependencies-analyser/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/aistomin/maven-dependencies-analyser/actions/workflows/maven.yml)
[![Hits-of-Code](https://hitsofcode.com/github/aistomin/maven-dependencies-analyser)](https://hitsofcode.com/view/github/aistomin/maven-dependencies-analyser)
[![codecov](https://codecov.io/gh/aistomin/maven-dependencies-analyser/branch/master/graph/badge.svg)](https://codecov.io/gh/aistomin/maven-dependencies-analyser)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.aistomin/maven-dependencies-analyser/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.aistomin/maven-dependencies-analyser)
[![javadoc](https://javadoc.io/badge2/com.github.aistomin/maven-dependencies-analyser/javadoc.svg)](https://javadoc.io/doc/com.github.aistomin/maven-dependencies-analyser)

Maven plugin that analyses and validates that all the project dependencies are
 up-to-date.

## Getting Started

### System Requirements

 - JDK 17 or higher.
 - Apache Maven 3.8.3 or higher
 
### Validate Project Dependencies

Add the following configuration to your project's `pom.xml`:

```maven
<build>
    <plugins>
        <!-- other plugins are there -->
        <plugin>
            <groupId>com.github.aistomin</groupId>
            <artifactId>maven-dependencies-analyser</artifactId>
            <version>3.0</version>
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

With this configuration the Maven build will fail in case any of the 
dependencies of your project is out-of-date. If you do not want the build to
 fail, but rather just to show the warning, please change the ```level``` 
 configuration value from ```ERROR``` to ```WARNING```.

Using the ```enabled``` configuration you may easily turn the complete 
dependencies validation off. Then the configuration section will look like:
```
<configuration>
    <level>ERROR</level>
    <enabled>false</enabled>
</configuration>
```
  
For the further information, please check out our 
[example project](https://github.com/aistomin/maven-dependencies-analyser-examples).

Please keep in mind that currently we validate only dependencies that are 
published in the [Maven Central Repository](https://search.maven.org/).
  
## Licence

The project is licensed under the terms of the 
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

## Have You Found a Bug? Do You Have Any Suggestions?

Although we try our best, we're not robots and bugs are possible :) Also we're
always happy to hear some suggestions, ideas and thoughts from you. Don't
 hesitate to [create an issue](https://github.com/aistomin/maven-dependencies-analyser/issues/new). 
It will help us to make our project better. Thank you in advance!!!

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
