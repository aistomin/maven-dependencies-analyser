/*
 * Copyright (c) 2019-2021, Istomin Andrei
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
            )
        );
        final List<MvnArtifactVersion> dependencies = new MdaPom(
            Thread.currentThread().getContextClassLoader()
                .getResource("sample_pom.xml").getFile()
        ).dependencies();
        Assertions.assertEquals(expected.size(), dependencies.size());
        for (final MvnArtifactVersion dependency : dependencies) {
            Assertions.assertTrue(
                expected.stream().anyMatch(exp -> exp.equals(dependency))
            );
        }
    }
}
