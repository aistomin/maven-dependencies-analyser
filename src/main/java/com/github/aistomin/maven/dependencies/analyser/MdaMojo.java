/*
 * Copyright (c) 2019, Istomin Andrei
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

import com.github.aistomin.maven.browser.MavenCentral;
import com.github.aistomin.maven.browser.MvnArtifactVersion;
import com.github.aistomin.maven.browser.MvnRepo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Maven Dependencies Analyser's Mojo class.
 *
 * @since 0.1
 */
@Mojo(
    name = "check", defaultPhase = LifecyclePhase.VERIFY,
    requiresDependencyResolution = ResolutionScope.TEST
)
public final class MdaMojo extends AbstractMojo {

    /**
     * Level of the validation.
     */
    @Parameter(property = "level", defaultValue = "WARNING")
    private String level;

    /**
     * The path to the pom.xml file.
     */
    @Parameter(property = "path", defaultValue = "pom.xml")
    private String pom;

    /**
     * Ctor.
     */
    @SuppressWarnings("PMD.UncommentedEmptyConstructor")
    public MdaMojo() {
    }

    /**
     * Ctor.
     *
     * @param level Level of the validation.
     * @param pom The path to the pom.xml file.
     */
    public MdaMojo(final String level, final String pom) {
        this.level = level;
        this.pom = pom;
    }

    /**
     * Main method.
     *
     * @throws MojoExecutionException Execution exception.
     * @throws MojoFailureException Failure exception.
     * @checkstyle NoJavadocForOverriddenMethodsCheck (10 lines)
     * @checkstyle IllegalCatchCheck (100 lines)
     */
    @SuppressWarnings(
        {"PMD.AvoidCatchingGenericException", "PMD.AvoidRethrowingException"}
    )
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            final List<MvnArtifactVersion> dependencies =
                new MdaPom(this.pom).dependencies();
            final Map<MvnArtifactVersion, List<MvnArtifactVersion>> outdated =
                new HashMap<>();
            final MvnRepo repo = new MavenCentral();
            for (final MvnArtifactVersion version : dependencies) {
                final List<MvnArtifactVersion> newer =
                    repo.findVersionsNewerThan(version);
                if (newer != null && !newer.isEmpty()) {
                    outdated.put(version, newer);
                }
            }
            if (outdated.keySet().size() > 0) {
                this.throwError(MdaMojo.message(outdated));
            }
        } catch (final MojoFailureException failure) {
            throw failure;
        } catch (final Exception exception) {
            throw new MojoExecutionException(
                "MdaMojo.execute() failed.", exception
            );
        }
    }

    /**
     * Throw pom.xml validation exception.
     *
     * @param msg Message.
     * @throws MojoFailureException Exception.
     */
    private void throwError(final String msg) throws MojoFailureException {
        if ("ERROR".equals(this.level)) {
            throw new MojoFailureException(msg);
        } else if ("WARNING".equals(this.level)) {
            getLog().warn(msg);
        } else {
            getLog().info(msg);
        }
    }

    /**
     * Build error message for the outdated dependencies.
     *
     * @param outdated Outdated dependencies.
     * @return Message.
     */
    private static String message(
        final Map<MvnArtifactVersion, List<MvnArtifactVersion>> outdated
    ) {
        final StringBuilder msg = new StringBuilder();
        for (
            final Map.Entry<MvnArtifactVersion, List<MvnArtifactVersion>> item
                : outdated.entrySet()
        ) {
            msg.append(
                String.format(
                    "%s (version %s) has newer versions: %s%n",
                    item.getKey().artifact().identifier(),
                    item.getKey().name(),
                    item.getValue()
                        .stream()
                        .map(MvnArtifactVersion::name)
                        .collect(Collectors.joining("; "))
                )
            );
        }
        return msg.toString();
    }
}
