/*
 * Copyright (c) 2019-2022, Istomin Andrei
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.aistomin.maven.dependencies.analyser;

import com.github.aistomin.maven.browser.MavenArtifact;
import com.github.aistomin.maven.browser.MavenArtifactVersion;
import com.github.aistomin.maven.browser.MavenGroup;
import com.github.aistomin.maven.browser.MvnArtifactVersion;
import com.github.aistomin.maven.browser.MvnPackagingType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link MdaPom}.
 *
 * @since 0.1
 */
final class MdaPomTest {

    /**
     * Sample pom file.
     */
    private final String sample = Thread
        .currentThread()
        .getContextClassLoader()
        .getResource("sample_pom.xml")
        .getFile();

    /**
     * Check that we can correctly read the dependencies from the pom.xml.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testDependencies() throws Exception {
        final String jupiter = "org.junit.jupiter";
        final String junit = "5.3.1";
        final List<MvnArtifactVersion> expected = Arrays.asList(
            new MavenArtifactVersion(
                new MavenArtifact(
                    new MavenGroup("com.github.aistomin"), "maven-browser"
                ), "1.0", MvnPackagingType.JAR, System.currentTimeMillis()
            ),
            new MavenArtifactVersion(
                new MavenArtifact(
                    new MavenGroup("org.apache.maven"), "maven-plugin-api"
                ), "2.0", MvnPackagingType.JAR, System.currentTimeMillis()
            ),
            new MavenArtifactVersion(
                new MavenArtifact(
                    new MavenGroup("org.apache.maven.plugin-tools"),
                    "maven-plugin-annotations"
                ), "3.4", MvnPackagingType.JAR, System.currentTimeMillis()
            ),
            new MavenArtifactVersion(
                new MavenArtifact(
                    new MavenGroup(jupiter),
                    "junit-jupiter-api"
                ), junit, MvnPackagingType.JAR, System.currentTimeMillis()
            ),
            new MavenArtifactVersion(
                new MavenArtifact(
                    new MavenGroup(jupiter),
                    "junit-jupiter-engine"
                ), junit, MvnPackagingType.JAR, System.currentTimeMillis()
            ),
            new MavenArtifactVersion(
                new MavenArtifact(
                    new MavenGroup("org.springframework.boot"),
                    "spring-boot-starter"
                ), "3.0.0-M5", MvnPackagingType.JAR, System.currentTimeMillis()
            )
        );
        final List<MvnArtifactVersion> dependencies =
            new MdaPom(this.sample).dependencies();
        Assertions.assertEquals(expected.size(), dependencies.size());
        for (final MvnArtifactVersion dependency : dependencies) {
            Assertions.assertTrue(
                expected.stream().anyMatch(exp -> exp.equals(dependency))
            );
        }
    }

    /**
     * Check that we can correctly read the plugins from the pom.xml.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testPlugins() throws Exception {
        final List<MvnArtifactVersion> expected = Arrays.asList(
            new MavenArtifactVersion(
                new MavenArtifact(
                    new MavenGroup("org.apache.maven.plugins"),
                    "maven-surefire-plugin"
                ), "2.22.1", MvnPackagingType.JAR, System.currentTimeMillis()
            ),
            new MavenArtifactVersion(
                new MavenArtifact(
                    new MavenGroup("com.qulice"), "qulice-maven-plugin"
                ), "0.18.19", MvnPackagingType.JAR, System.currentTimeMillis()
            ),
            new MavenArtifactVersion(
                new MavenArtifact(
                    new MavenGroup("org.jacoco"), "jacoco-maven-plugin"
                ), "0.8.4", MvnPackagingType.JAR, System.currentTimeMillis()
            )
        );
        final List<MvnArtifactVersion> plugins =
            new MdaPom(this.sample).plugins();
        Assertions.assertEquals(expected.size(), plugins.size());
        for (final MvnArtifactVersion plugin : plugins) {
            Assertions.assertTrue(
                expected.stream().anyMatch(exp -> exp.equals(plugin))
            );
        }
    }

    /**
     * Check that we can correctly read the parent artifact from the pom.xml.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testParent() throws Exception {
        final MvnArtifactVersion parent = new MdaPom(this.sample).parent();
        Assertions.assertEquals(
            "org.springframework.boot:spring-boot-starter-parent:3.0.4",
            parent.identifier()
        );
    }
}
