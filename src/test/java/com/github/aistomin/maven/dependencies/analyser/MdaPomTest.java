/*
 * Copyright (c) 2019 Andrej Istomin
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
     * Parentless sample pom file.
     */
    private final String parentlessSample = Thread
        .currentThread()
        .getContextClassLoader()
        .getResource("parentless_pom.xml")
        .getFile();

    /**
     * The pom file which declares versions in all the possible sections.
     */
    private final String sections = Thread
        .currentThread()
        .getContextClassLoader()
        .getResource("sections_pom.xml")
        .getFile();

    /**
     * Ctor.
     */
    MdaPomTest() {
    }

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
     * Check that we read the dependencies from all the pom.xml sections: the
     * dependency management, the profiles and the plugins.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testDependenciesFromAllSections() throws Exception {
        final String slf4j = "org.slf4j";
        final List<MvnArtifactVersion> expected = Arrays.asList(
            artifact("com.github.aistomin", "maven-browser", "5.0"),
            artifact(slf4j, "slf4j-simple", "1.7.36"),
            artifact(slf4j, "slf4j-api", "2.0.9"),
            artifact("org.junit.jupiter", "junit-jupiter-api", "5.10.1"),
            artifact("org.mockito", "mockito-core", "5.8.0"),
            artifact("com.puppycrawl.tools", "checkstyle", "10.12.5")
        );
        final List<MvnArtifactVersion> dependencies =
            new MdaPom(this.sections).dependencies();
        Assertions.assertEquals(expected.size(), dependencies.size());
        for (final MvnArtifactVersion dependency : dependencies) {
            Assertions.assertTrue(
                expected.stream().anyMatch(exp -> exp.equals(dependency))
            );
        }
    }

    /**
     * Check that we read the plugins from all the pom.xml sections: the
     * plugin management, the profiles, the reporting and the extensions.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testPluginsFromAllSections() throws Exception {
        final String plugins = "org.apache.maven.plugins";
        final List<MvnArtifactVersion> expected = Arrays.asList(
            artifact(plugins, "maven-checkstyle-plugin", "3.3.1"),
            artifact(plugins, "maven-clean-plugin", "3.3.2"),
            artifact(plugins, "maven-gpg-plugin", "3.1.0"),
            artifact(plugins, "maven-source-plugin", "3.3.0"),
            artifact(plugins, "maven-project-info-reports-plugin", "3.5.0"),
            artifact(plugins, "maven-surefire-report-plugin", "3.2.3"),
            artifact("org.apache.maven.wagon", "wagon-ssh", "3.5.3"),
            artifact(
                "com.github.aistomin", "maven-dependencies-analyser", "4.2"
            )
        );
        final List<MvnArtifactVersion> found =
            new MdaPom(this.sections).plugins();
        Assertions.assertEquals(expected.size(), found.size());
        for (final MvnArtifactVersion plugin : found) {
            Assertions.assertTrue(
                expected.stream().anyMatch(exp -> exp.equals(plugin))
            );
        }
    }

    /**
     * Check that the artifact which is declared in several sections is
     * returned only once.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testDuplicatesAreRemoved() throws Exception {
        final MvnArtifactVersion duplicate = artifact(
            "com.github.aistomin", "maven-dependencies-analyser", "4.2"
        );
        Assertions.assertEquals(
            1,
            new MdaPom(this.sections)
                .plugins()
                .stream()
                .filter(duplicate::equals)
                .count()
        );
    }

    /**
     * Check that we can correctly read the parent artifact from the pom.xml.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testParent() throws Exception {
        Assertions.assertEquals(
            "org.springframework.boot:spring-boot-starter-parent:2.7.9",
            new MdaPom(this.sample).parent().identifier()
        );
        Assertions.assertNull(
            new MdaPom(this.parentlessSample).parent()
        );
    }

    /**
     * Create the expected artifact's version.
     *
     * @param group The artifact's group.
     * @param artifact The artifact's identifier.
     * @param version The artifact's version.
     * @return The artifact's version.
     */
    private static MvnArtifactVersion artifact(
        final String group, final String artifact, final String version
    ) {
        return new MavenArtifactVersion(
            new MavenArtifact(new MavenGroup(group), artifact),
            version, MvnPackagingType.JAR, System.currentTimeMillis()
        );
    }
}
