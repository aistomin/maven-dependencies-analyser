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
import java.util.Map;
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
     * Whether the prerelease versions count as upgrades. They do not by
     * default: an alpha, a beta, a milestone or a release candidate is not
     * something a build must be pushed towards, and at the ERROR level a
     * project which sits on the newest release would start failing the moment
     * somebody publishes a prerelease. A project whose declared version is
     * itself a prerelease is the exception, see
     * {@link MdaMojo#reportable(MvnArtifactVersion, List)}.
     */
    @Parameter(property = "mda.prereleases", defaultValue = "false")
    private Boolean prereleases = false;

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
     * Report the prereleases as upgrades too.
     *
     * @param report Must the prerelease versions be reported?
     */
    public void setPrereleases(final Boolean report) {
        this.prereleases = report;
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
                final List<MvnArtifactVersion> newer = this.reportable(
                    artifact, lookup.getValue().join()
                );
                if (!newer.isEmpty()) {
                    this.logger.debug(
                        "{}: the newer versions are: {}",
                        artifact.artifact().identifier(),
                        newer.stream()
                            .map(MvnArtifactVersion::name)
                            .sorted(
                                Comparator.comparing(MdaVersion::new).reversed()
                            )
                            .collect(Collectors.joining("; "))
                    );
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
     * Keep only the versions which are worth reporting as upgrades. Unless
     * the prereleases were explicitly asked for, they are dropped, so that a
     * project which sits on the newest release is reported as up to date
     * instead of being pushed towards an alpha.
     *
     * <p>The exception is a declared version which is a prerelease itself:
     * its author is already on the prerelease train, so the newer
     * prereleases are the only upgrades they can be told about, and hiding
     * them would leave the artifact silently unanalysed.
     *
     * @param declared The version which the pom.xml file declares.
     * @param found The versions which are newer than the declared one.
     * @return The versions which have to be reported.
     */
    private List<MvnArtifactVersion> reportable(
        final MvnArtifactVersion declared,
        final List<MvnArtifactVersion> found
    ) {
        final List<MvnArtifactVersion> result;
        if (this.prereleases || new MdaVersion(declared.name()).prerelease()) {
            result = found;
        } else {
            final Map<Boolean, List<MvnArtifactVersion>> split = found.stream()
                .collect(
                    Collectors.partitioningBy(
                        version -> new MdaVersion(version.name()).prerelease()
                    )
                );
            result = split.get(false);
            final List<MvnArtifactVersion> ignored = split.get(true);
            if (!ignored.isEmpty()) {
                this.logger.debug(
                    "{}: ignored the prerelease versions: {}",
                    declared.artifact().identifier(),
                    ignored.stream()
                        .map(MvnArtifactVersion::name)
                        .collect(Collectors.joining("; "))
                );
            }
        }
        return result;
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
     * Build the report's line for an outdated artifact: the version which the
     * artifact has to be upgraded to, and how many versions it is behind.
     *
     * <p>Only the newest version is named on purpose. An artifact which is a
     * few years behind — exactly what the analysis exists to catch — has
     * dozens of newer versions, and a line which lists all of them is
     * unreadable both in the log and in the build's failure message, while
     * the actionable information is the version to move to. The whole list is
     * logged at the debug level by {@link MdaMojo#report(List)}.
     *
     * <p>The method is package private rather than private so that the tests
     * can pin the wording, the plural forms and the choice of the newest
     * version without asking the repository anything.
     *
     * @param artifact The outdated artifact.
     * @param newer The versions which are newer than the artifact's one. Must
     *  not be empty.
     * @return The line.
     */
    static String message(
        final MvnArtifactVersion artifact,
        final List<MvnArtifactVersion> newer
    ) {
        final String count;
        if (newer.size() == 1) {
            count = "1 newer version exists";
        } else {
            count = String.format("%d newer versions exist", newer.size());
        }
        return String.format(
            "%s (version %s) is outdated: the latest version is %s (%s).%n",
            artifact.artifact().identifier(),
            artifact.name(),
            latest(newer).name(),
            count
        );
    }

    /**
     * The newest of the versions, by Maven's own ordering rules rather than by
     * the order in which the repository returned them.
     *
     * @param versions The versions. Must not be empty.
     * @return The newest version.
     */
    private static MvnArtifactVersion latest(
        final List<MvnArtifactVersion> versions
    ) {
        return versions.stream()
            .max(
                Comparator.comparing(
                    version -> new MdaVersion(version.name())
                )
            )
            .orElseThrow();
    }
}
