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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * The representation of pom.xml file.
 *
 * @since 0.1
 */
public final class MdaPom implements MdaBuildFile {

    /**
     * The pom.xml file.
     */
    private final File file;

    /**
     * Ctor.
     *
     * @param path The path to the pom.xml file.
     */
    public MdaPom(final String path) {
        this(new File(path));
    }

    /**
     * Ctor.
     *
     * @param pom The pom.xml file.
     */
    public MdaPom(final File pom) {
        this.file = pom;
    }

    @Override
    public List<MvnArtifactVersion> dependencies()
        throws IOException, XmlPullParserException {
        final Model model = new MavenXpp3Reader()
            .read(Files.newInputStream(this.file.toPath()));
        return model
            .getDependencies()
            .stream()
            .map(
                dependency ->
                    new MavenArtifactVersion(
                        new MavenArtifact(
                            new MavenGroup(dependency.getGroupId()),
                            dependency.getArtifactId()
                        ),
                        dependencyVersion(model, dependency.getVersion()),
                        find(dependency.getType()),
                        System.currentTimeMillis()
                    )
            )
            .filter(version -> version.name() != null)
            .collect(Collectors.toList());
    }

    @Override
    public List<MvnArtifactVersion> plugins()
        throws IOException, XmlPullParserException {
        final Model model = new MavenXpp3Reader()
            .read(Files.newInputStream(this.file.toPath()));
        final Build build = model.getBuild();
        return build != null ? build.getPlugins()
            .stream()
            .map(
                plugin ->
                    new MavenArtifactVersion(
                        new MavenArtifact(
                            new MavenGroup(plugin.getGroupId()),
                            plugin.getArtifactId()
                        ),
                        dependencyVersion(model, plugin.getVersion()),
                        MvnPackagingType.JAR,
                        System.currentTimeMillis()
                    )
            )
            .collect(Collectors.toList()) : new ArrayList<>();
    }

    /**
     * Sometimes the version of the artifact can be set as property. We need to
     * get the real value.
     *
     * @param model The pom.xml model.
     * @param version Dependency's version.
     * @return The real version.
     */
    private static String dependencyVersion(
        final Model model, final String version
    ) {
        final String marker = "${";
        String result = version;
        if (version != null && version.contains(marker)) {
            result = model
                .getProperties()
                .getProperty(
                    version.replace(marker, "").replace("}", "")
                );
        }
        return result;
    }

    /**
     * Find the {@link MvnPackagingType} by it's string representation.
     *
     * @param str String representation of the packaging type.
     * @return The corresponding enum instance.
     */
    private static MvnPackagingType find(final String str) {
        return Arrays.stream(MvnPackagingType.values())
            .filter(type -> type.packaging().equals(str))
            .findFirst().orElse(null);
    }
}
