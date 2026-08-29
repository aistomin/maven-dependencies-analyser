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

import com.github.aistomin.maven.browser.MavenCentral;
import com.github.aistomin.maven.browser.MvnArtifactVersion;
import com.github.aistomin.maven.browser.MvnRepo;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * The default amount of the threads which look the versions up. The
     * lookups spend nearly all their time waiting for Maven Central to
     * answer, so the amount of the CPU cores is a poor measure for them: a
     * two-core CI machine would run two lookups at a time although none of
     * them is CPU bound. A constant also keeps the analysis equally fast
     * everywhere. The same value is repeated in the {@link Parameter}
     * annotation of {@link MdaMojo#threads} because an annotation only
     * accepts a string literal there.
     */
    private static final int DEFAULT_THREADS = 8;

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Failure level.
     */
    @Parameter(property = "mda.level", defaultValue = "WARNING")
    private FailureLevel level;

    /**
     * Whether the validation is enabled.
     */
    @Parameter(property = "mda.enabled", defaultValue = "true")
    private Boolean enabled;

    /**
     * Whether the validation must be skipped. Unlike {@link MdaMojo#enabled}
     * it is meant to be set from the command line, e.g. when we do not want
     * to spend the time on the analysis in the CI build.
     */
    @Parameter(property = "mda.skip", defaultValue = "false")
    private Boolean skip = false;

    /**
     * The path to the pom.xml file.
     */
    @Parameter(property = "mda.pom", defaultValue = "pom.xml")
    private String pom;

    /**
     * The maximal amount of the artifacts which are looked up in the
     * repository at the same time. It is bounded on purpose: the analysis
     * must stay polite to Maven Central instead of firing every request of a
     * big project at once.
     */
    @Parameter(property = "mda.threads", defaultValue = "8")
    private Integer threads = DEFAULT_THREADS;

    /**
     * Ctor.
     */
    public MdaMojo() {
        this(FailureLevel.WARNING, "pom.xml");
    }

    /**
     * Ctor.
     *
     * @param severity Failure level.
     * @param file The path to the pom.xml file.
     */
    public MdaMojo(final FailureLevel severity, final String file) {
        this(severity, file, true);
    }

    /**
     * Ctor.
     *
     * @param severity Failure level.
     * @param file The path to the pom.xml file.
     * @param active Is validation enabled?
     */
    public MdaMojo(
        final FailureLevel severity, final String file, final Boolean active
    ) {
        this.level = severity;
        this.pom = file;
        this.enabled = active;
    }

    @Override
    public void execute() throws MojoFailureException {
        if (this.skip || !this.enabled) {
            final String line =
                "***********************************************";
            this.logger.warn(line);
            this.logger.warn("Maven dependencies analysis is switched off.");
            this.logger.warn(line);
        } else {
            this.analyse();
        }
    }

    /**
     * Set failure level.
     *
     * @param lvl Failure level.
     */
    public void setLevel(final FailureLevel lvl) {
        this.level = lvl;
    }

    /**
     * Enable/disable the validation.
     *
     * @param active Is validation enabled?
     */
    public void setEnabled(final Boolean active) {
        this.enabled = active;
    }

    /**
     * Skip the validation.
     *
     * @param ignore Must the validation be skipped?
     */
    public void setSkip(final Boolean ignore) {
        this.skip = ignore;
    }

    /**
     * Set the path to the pom.xml file.
     *
     * @param path The path to the pom.xml file.
     */
    public void setPom(final String path) {
        this.pom = path;
    }

    /**
     * Set the amount of the parallel lookups.
     *
     * @param count The maximal amount of the artifacts which are looked up at
     *  the same time.
     */
    public void setThreads(final Integer count) {
        this.threads = count;
    }

    /**
     * Analyse the pom.xml file and report everything which the build's author
     * has to know: the artifacts which could not be analysed at all and the
     * ones which have newer versions.
     *
     * @throws MojoFailureException If the analysis found something to report
     *  and the failure level is set to ERROR, or if the plugin itself is
     *  misconfigured.
     */
    private void analyse() throws MojoFailureException {
        if (this.threads == null || this.threads < 1) {
            throw new MojoFailureException(
                String.format(
                    "mda.threads must be a positive number, but it is: %s.",
                    this.threads
                )
            );
        }
        final List<MvnArtifactVersion> artifacts;
        try {
            artifacts = sorted(this.artifacts());
        } catch (final Throwable error) {
            this.throwError(
                String.format("Error occurred: %s", error.getMessage()), error
            );
            return;
        }
        final String report = this.report(artifacts);
        if (report.isEmpty()) {
            this.logger.info("All the dependencies are up to date.");
        } else {
            this.throwError(report);
        }
    }

    /**
     * All the artifacts which have to be analysed: the parent, the
     * dependencies and the plugins.
     *
     * @return The artifacts.
     * @throws IOException If the pom.xml file is not found or corrupted.
     * @throws XmlPullParserException If the pom.xml parsing was not
     *  successful.
     */
    private Collection<MvnArtifactVersion> artifacts()
        throws IOException, XmlPullParserException {
        final Collection<MvnArtifactVersion> result = new LinkedHashSet<>();
        final MdaPom config = new MdaPom(this.pom);
        final MvnArtifactVersion parent = config.parent();
        if (parent != null) {
            result.add(parent);
        }
        result.addAll(config.dependencies());
        result.addAll(config.plugins());
        return result;
    }

    /**
     * Look the newer versions of the artifacts up and build the report. The
     * lookups run in parallel against a bounded pool; an artifact which can
     * not be analysed is reported, but it never stops the analysis of the
     * others.
     *
     * @param artifacts The artifacts, ordered the way they must be reported.
     * @return The report, or an empty string if there is nothing to report.
     */
    private String report(final List<MvnArtifactVersion> artifacts) {
        final MvnRepo repo = new MavenCentral();
        final var lookups = new LinkedHashMap<
            MvnArtifactVersion, CompletableFuture<List<MvnArtifactVersion>>
            >();
        try (
            ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(this.threads, Math.max(artifacts.size(), 1))
            )
        ) {
            for (final MvnArtifactVersion artifact : artifacts) {
                lookups.put(
                    artifact,
                    CompletableFuture.supplyAsync(
                        () -> newer(repo, artifact), pool
                    )
                );
            }
        }
        final StringBuilder failed = new StringBuilder();
        final StringBuilder outdated = new StringBuilder();
        for (final var lookup : lookups.entrySet()) {
            final MvnArtifactVersion artifact = lookup.getKey();
            try {
                final List<MvnArtifactVersion> newer = lookup.getValue().join();
                if (!newer.isEmpty()) {
                    outdated.append(message(artifact, newer));
                }
            } catch (final CompletionException exception) {
                failed.append(
                    String.format(
                        "Can not analyse %s. %s%n",
                        artifact, exception.getCause().getMessage()
                    )
                );
            }
        }
        return failed.append(outdated).toString();
    }

    /**
     * Throw pom.xml validation exception.
     *
     * @param msg Message.
     * @throws MojoFailureException Exception.
     */
    private void throwError(final String msg) throws MojoFailureException {
        this.throwError(msg, null);
    }

    /**
     * Report a failure the way the failure level demands: fail the build if it
     * is set to ERROR, only log the failure if it is set to WARNING.
     *
     * @param msg Message.
     * @param cause The exception which caused the failure, so that it stays
     *  visible in the build's log. NULL if the failure is a finding of the
     *  analysis rather than an error.
     * @throws MojoFailureException Exception.
     */
    private void throwError(final String msg, final Throwable cause)
        throws MojoFailureException {
        if (FailureLevel.ERROR.equals(this.level)) {
            throw new MojoFailureException(msg, cause);
        } else if (FailureLevel.WARNING.equals(this.level)) {
            this.logger.warn(msg, cause);
        } else {
            throw new IllegalStateException(
                String.format("Unknown level: %s", this.level.name())
            );
        }
    }

    /**
     * Order the artifacts by their identifiers, so that the same pom.xml file
     * is always reported in the same way, no matter in which order the
     * parallel lookups happened to finish.
     *
     * @param artifacts The artifacts.
     * @return The ordered artifacts.
     */
    private static List<MvnArtifactVersion> sorted(
        final Collection<MvnArtifactVersion> artifacts
    ) {
        return artifacts.stream()
            .sorted(Comparator.comparing(MvnArtifactVersion::identifier))
            .collect(Collectors.toList());
    }

    /**
     * Find the versions of the artifact which are newer than the given one.
     * The checked exceptions of the repository are re-thrown unchecked,
     * because the lookup runs as a task of a {@link CompletableFuture}.
     *
     * @param repo The repository which we ask.
     * @param artifact The artifact's version which we compare against.
     * @return The newer versions.
     */
    private static List<MvnArtifactVersion> newer(
        final MvnRepo repo, final MvnArtifactVersion artifact
    ) {
        try {
            return repo.findVersionsNewerThan(artifact);
        } catch (final Throwable exception) {
            throw new CompletionException(exception);
        }
    }

    /**
     * Build the report's line for an outdated artifact.
     *
     * @param artifact The outdated artifact.
     * @param newer The versions which are newer than the artifact's one.
     * @return The line.
     */
    private static String message(
        final MvnArtifactVersion artifact,
        final List<MvnArtifactVersion> newer
    ) {
        return String.format(
            "%s (version %s) has newer versions: %s%n",
            artifact.artifact().identifier(),
            artifact.name(),
            newer.stream()
                .map(MvnArtifactVersion::name)
                .collect(Collectors.joining("; "))
        );
    }
}
