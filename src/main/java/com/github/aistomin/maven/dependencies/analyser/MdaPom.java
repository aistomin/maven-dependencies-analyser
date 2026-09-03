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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The representation of pom.xml file.
 *
 * @since 0.1
 */
public final class MdaPom implements MdaBuildFile {

    /**
     * The marker which starts a property placeholder in a version.
     */
    private static final String MARKER = "${";

    /**
     * The pattern of a single property placeholder inside a version.
     */
    private static final Pattern PLACEHOLDER =
        Pattern.compile("\\$\\{([^}]+)}");

    /**
     * The maximum amount of the substitution rounds. It stops the
     * interpolation of the cyclic property definitions.
     */
    private static final int ROUNDS = 10;

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * The pom.xml file.
     */
    private final File file;

    /**
     * The parsed model of the pom.xml file. The file is parsed lazily, on
     * the first call of {@link MdaPom#model()}, and only once: the parent,
     * the dependencies and the plugins are all read from this one model.
     */
    private Model parsed;

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
    public MvnArtifactVersion parent()
        throws IOException, XmlPullParserException {
        final Model model = this.model();
        final Parent parent = model.getParent();
        final MvnArtifactVersion result;
        if (parent == null) {
            result = null;
        } else {
            result = this.artifact(
                model, parent.getGroupId(), parent.getArtifactId(),
                parent.getVersion(), MvnPackagingType.JAR
            );
        }
        return result;
    }

    @Override
    public List<MvnArtifactVersion> dependencies()
        throws IOException, XmlPullParserException {
        final Model model = this.model();
        final List<Dependency> found =
            new ArrayList<>(model.getDependencies());
        found.addAll(managed(model.getDependencyManagement()));
        for (final Profile profile : model.getProfiles()) {
            found.addAll(profile.getDependencies());
            found.addAll(managed(profile.getDependencyManagement()));
        }
        for (final Plugin plugin : declared(model)) {
            found.addAll(plugin.getDependencies());
        }
        return found.stream()
            .map(
                dependency -> this.artifact(
                    model, dependency.getGroupId(),
                    dependency.getArtifactId(), dependency.getVersion(),
                    find(dependency.getType())
                )
            )
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    }

    @Override
    public List<MvnArtifactVersion> plugins()
        throws IOException, XmlPullParserException {
        final Model model = this.model();
        final List<MvnArtifactVersion> found = new ArrayList<>(0);
        for (final Plugin plugin : declared(model)) {
            found.add(
                this.artifact(
                    model, plugin.getGroupId(), plugin.getArtifactId(),
                    plugin.getVersion(), MvnPackagingType.MAVEN_PLUGIN
                )
            );
        }
        for (final ReportPlugin plugin : reports(model)) {
            found.add(
                this.artifact(
                    model, plugin.getGroupId(), plugin.getArtifactId(),
                    plugin.getVersion(), MvnPackagingType.MAVEN_PLUGIN
                )
            );
        }
        for (final Extension extension : extensions(model)) {
            found.add(
                this.artifact(
                    model, extension.getGroupId(), extension.getArtifactId(),
                    extension.getVersion(), MvnPackagingType.JAR
                )
            );
        }
        return found.stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * The model of the pom.xml file. The file is parsed on the first call;
     * the following calls return the already parsed model.
     *
     * @return Model.
     * @throws IOException If the file is not found or corrupted.
     * @throws XmlPullParserException If file parsing was not successful.
     */
    private Model model() throws IOException, XmlPullParserException {
        if (this.parsed == null) {
            try (
                InputStream stream = Files.newInputStream(this.file.toPath())
            ) {
                this.parsed = new MavenXpp3Reader().read(stream);
            }
        }
        return this.parsed;
    }

    /**
     * Create the artifact's version. The artifacts which versions can not be
     * resolved are reported and skipped.
     *
     * @param model The pom.xml model.
     * @param group The artifact's group.
     * @param artifact The artifact's identifier.
     * @param version The version as it is written in the pom.xml.
     * @param type The artifact's packaging type.
     * @return The artifact's version or NULL if it can not be resolved.
     */
    private MvnArtifactVersion artifact(
        final Model model, final String group, final String artifact,
        final String version, final MvnPackagingType type
    ) {
        final String resolved = dependencyVersion(model, version);
        final MvnArtifactVersion result;
        if (resolved == null) {
            result = null;
            if (version == null) {
                this.logger.debug(
                    "{}:{} has no version. It is inherited, we skip it.",
                    group, artifact
                );
            } else {
                this.logger.warn(
                    "Can not analyse {}:{}. Version {} can not be resolved.",
                    group, artifact, version
                );
            }
        } else {
            result = new MavenArtifactVersion(
                new MavenArtifact(new MavenGroup(group), artifact),
                resolved, type, System.currentTimeMillis()
            );
        }
        return result;
    }

    /**
     * All the plugins which are declared in the build sections of the pom.xml.
     * The build sections of the profiles are taken into account too.
     *
     * @param model The pom.xml model.
     * @return The list of the plugins.
     */
    private static List<Plugin> declared(final Model model) {
        final List<Plugin> result = new ArrayList<>(built(model.getBuild()));
        for (final Profile profile : model.getProfiles()) {
            result.addAll(built(profile.getBuild()));
        }
        return result;
    }

    /**
     * The plugins of the build section: both the plugins themselves and the
     * ones which are declared in the plugin management section.
     *
     * @param build The build section. May be NULL.
     * @return The list of the plugins.
     */
    private static List<Plugin> built(final BuildBase build) {
        final List<Plugin> result = new ArrayList<>(0);
        if (build != null) {
            result.addAll(build.getPlugins());
            final PluginManagement management = build.getPluginManagement();
            if (management != null) {
                result.addAll(management.getPlugins());
            }
        }
        return result;
    }

    /**
     * All the reporting plugins of the pom.xml and of its profiles.
     *
     * @param model The pom.xml model.
     * @return The list of the reporting plugins.
     */
    private static List<ReportPlugin> reports(final Model model) {
        final List<ReportPlugin> result =
            new ArrayList<>(reported(model.getReporting()));
        for (final Profile profile : model.getProfiles()) {
            result.addAll(reported(profile.getReporting()));
        }
        return result;
    }

    /**
     * The plugins of the reporting section.
     *
     * @param reporting The reporting section. May be NULL.
     * @return The list of the reporting plugins.
     */
    private static List<ReportPlugin> reported(final Reporting reporting) {
        final List<ReportPlugin> result;
        if (reporting == null) {
            result = new ArrayList<>(0);
        } else {
            result = reporting.getPlugins();
        }
        return result;
    }

    /**
     * The extensions of the build section.
     *
     * @param model The pom.xml model.
     * @return The list of the extensions.
     */
    private static List<Extension> extensions(final Model model) {
        final List<Extension> result;
        final Build build = model.getBuild();
        if (build == null) {
            result = new ArrayList<>(0);
        } else {
            result = build.getExtensions();
        }
        return result;
    }

    /**
     * The dependencies of the dependency management section.
     *
     * @param management The dependency management section. May be NULL.
     * @return The list of the dependencies.
     */
    private static List<Dependency> managed(
        final DependencyManagement management
    ) {
        final List<Dependency> result;
        if (management == null) {
            result = new ArrayList<>(0);
        } else {
            result = management.getDependencies();
        }
        return result;
    }

    /**
     * Sometimes the version of the artifact contains property placeholders.
     * We need to get the real value: every placeholder is substituted using
     * {@link MdaPom#properties(Model)}, repeatedly, until the version stops
     * changing or {@link MdaPom#ROUNDS} is reached, whichever comes first.
     * The version which still contains a placeholder after that can not be
     * resolved.
     *
     * @param model The pom.xml model.
     * @param version Dependency's version.
     * @return The real version or NULL if it can not be resolved.
     */
    private static String dependencyVersion(
        final Model model, final String version
    ) {
        String result = version;
        if (version != null && version.contains(MARKER)) {
            final Properties properties = properties(model);
            for (int round = 0; round < ROUNDS; round = round + 1) {
                final String next = substituted(result, properties);
                if (next.equals(result)) {
                    break;
                }
                result = next;
            }
            if (result.contains(MARKER)) {
                result = null;
            }
        }
        return result;
    }

    /**
     * All the properties which the placeholders in the versions can be
     * resolved against: the built-in project.version and
     * project.parent.version, the top-level properties and the properties
     * of the profiles. The later sources win over the earlier ones.
     *
     * @param model The pom.xml model.
     * @return The properties.
     */
    private static Properties properties(final Model model) {
        final Properties result = new Properties();
        final Parent parent = model.getParent();
        if (parent != null && parent.getVersion() != null) {
            result.setProperty(
                "project.parent.version", parent.getVersion()
            );
        }
        final String version = projectVersion(model);
        if (version != null) {
            result.setProperty("project.version", version);
        }
        result.putAll(model.getProperties());
        for (final Profile profile : model.getProfiles()) {
            result.putAll(profile.getProperties());
        }
        return result;
    }

    /**
     * The version of the project: either the explicitly declared one or,
     * when it is omitted, the version of the parent it is then inherited
     * from.
     *
     * @param model The pom.xml model.
     * @return The version or NULL if the pom declares neither.
     */
    private static String projectVersion(final Model model) {
        String result = model.getVersion();
        if (result == null && model.getParent() != null) {
            result = model.getParent().getVersion();
        }
        return result;
    }

    /**
     * Substitute every placeholder of the version which is defined in the
     * properties. The placeholders which are not defined stay as they are.
     *
     * @param version The version.
     * @param properties The properties to resolve the placeholders against.
     * @return The version with the placeholders substituted.
     */
    private static String substituted(
        final String version, final Properties properties
    ) {
        final Matcher matcher = PLACEHOLDER.matcher(version);
        final StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = properties.getProperty(matcher.group(1));
            if (value == null) {
                value = matcher.group(0);
            }
            matcher.appendReplacement(
                result, Matcher.quoteReplacement(value)
            );
        }
        matcher.appendTail(result);
        return result.toString();
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
