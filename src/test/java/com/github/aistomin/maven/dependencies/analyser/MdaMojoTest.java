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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link MdaMojo}.
 *
 * @since 0.1
 */
final class MdaMojoTest {

    /**
     * The name of the pom file with outdated dependencies.
     */
    private static final String ERROR_POM_XML = "error_pom.xml";

    /**
     * The name of the pom file which mixes the artifacts that Maven Central
     * does not know with an outdated one which it does know.
     */
    private static final String UNKNOWN_POM_XML = "unknown_pom.xml";

    /**
     * The name of the pom file which can not be parsed at all.
     */
    private static final String CORRUPT_POM_XML = "corrupt_pom.xml";

    /**
     * The name of the pom file which pins one artifact to a release and
     * another one to a prerelease.
     */
    private static final String PRERELEASE_POM_XML = "prerelease_pom.xml";

    /**
     * The identifier of the artifact which the prerelease pom file pins to a
     * release.
     */
    private static final String RELEASE_PIN = "org.apache.maven:maven-artifact";

    /**
     * The identifier of the artifact which the prerelease pom file pins to a
     * prerelease.
     */
    private static final String PRERELEASE_PIN = "org.apache.maven:maven-model";

    /**
     * The prefix with which the genuine errors are reported.
     */
    private static final String ERROR_PREFIX = "Error occurred: ";

    /**
     * The shape of the line with which an outdated artifact is reported: the
     * artifact, the version which its pom.xml declares, the newest version
     * which exists and how many versions the artifact is behind.
     */
    private static final Pattern OUTDATED = Pattern.compile(
        "^(?<artifact>\\S+) \\(version (?<declared>\\S+)\\) is outdated: the"
            + " latest version is (?<latest>\\S+) \\((?<behind>\\d+) newer"
            + " versions? exists?\\)\\.$"
    );

    /**
     * Ctor.
     */
    MdaMojoTest() {
    }

    /**
     * Check that Mojo file can be created with default ctor.
     */
    @Test
    void testCtr() {
        new MdaMojo();
    }

    /**
     * Check that plugin can be successfully executed with warning level.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testWarning() throws Exception {
        new MdaMojo(
            FailureLevel.WARNING,
            Thread.currentThread().getContextClassLoader()
                .getResource(MdaMojoTest.ERROR_POM_XML).getPath()
        ).execute();
    }

    /**
     * Check that plugin can be successfully executed with error level.
     */
    @Test
    void testError() {
        final MdaMojo mojo = new MdaMojo();
        mojo.setLevel(FailureLevel.ERROR);
        mojo.setEnabled(true);
        mojo.setPom(
            Thread.currentThread().getContextClassLoader()
                .getResource(MdaMojoTest.ERROR_POM_XML).getPath()
        );
        Assertions.assertThrows(MojoFailureException.class, mojo::execute);
    }

    /**
     * Check that the validation can be skipped even if the failure level is
     * set to ERROR and the pom.xml contains outdated dependencies.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testSkipped() throws Exception {
        final MdaMojo mojo = new MdaMojo();
        mojo.setLevel(FailureLevel.ERROR);
        mojo.setEnabled(true);
        mojo.setSkip(true);
        mojo.setPom(
            Thread.currentThread().getContextClassLoader()
                .getResource(MdaMojoTest.ERROR_POM_XML).getPath()
        );
        mojo.execute();
    }

    /**
     * Check that plugin can be successfully disabled.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testDisabled() throws Exception {
        new MdaMojo(
            FailureLevel.WARNING,
            Thread.currentThread().getContextClassLoader()
                .getResource(MdaMojoTest.ERROR_POM_XML).getPath(),
            false
        ).execute();
    }

    /**
     * Check that we correctly work with the configurations with paren artifact.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testParent() throws Exception {
        new MdaMojo(
            FailureLevel.WARNING,
            Thread.currentThread().getContextClassLoader()
                .getResource("sample_pom.xml").getPath()
        ).execute();
    }

    /**
     * Check that an artifact which can not be analysed is reported, but does
     * not stop the analysis of the other artifacts.
     */
    @Test
    void testUnanalysableArtifacts() {
        final String msg = MdaMojoTest.report(
            MdaMojoTest.UNKNOWN_POM_XML, null
        );
        Assertions.assertTrue(
            msg.contains("Can not analyse com.github.aistomin"
                + ":no-such-artifact-alpha:1.0."),
            msg
        );
        Assertions.assertTrue(
            msg.contains("Can not analyse com.github.aistomin"
                + ":no-such-artifact-beta:2.0."),
            msg
        );
        Assertions.assertTrue(
            msg.contains(
                "com.github.aistomin:maven-browser (version 1.0) is outdated:"
            ),
            msg
        );
    }

    /**
     * Check that the report does not depend on the order in which the
     * parallel lookups happen to finish: it is the same on every run and on
     * every amount of the threads, and both of its sections are ordered by
     * the artifacts' identifiers.
     */
    @Test
    void testDeterministicReport() {
        final String first = MdaMojoTest.report(
            MdaMojoTest.UNKNOWN_POM_XML, null
        );
        Assertions.assertEquals(
            first, MdaMojoTest.report(MdaMojoTest.UNKNOWN_POM_XML, null)
        );
        Assertions.assertEquals(
            first, MdaMojoTest.report(MdaMojoTest.UNKNOWN_POM_XML, 1)
        );
        MdaMojoTest.assertSorted(first, true);
        MdaMojoTest.assertSorted(first, false);
    }

    /**
     * Check that a non-positive amount of the threads fails the build even if
     * the failure level is set to WARNING: it is a broken configuration, not
     * an outdated dependency.
     */
    @Test
    void testInvalidThreads() {
        final MdaMojo mojo = new MdaMojo(
            FailureLevel.WARNING,
            Thread.currentThread().getContextClassLoader()
                .getResource(MdaMojoTest.ERROR_POM_XML).getPath()
        );
        mojo.setThreads(0);
        Assertions.assertThrows(MojoFailureException.class, mojo::execute);
        mojo.setThreads(null);
        Assertions.assertThrows(MojoFailureException.class, mojo::execute);
    }

    /**
     * Check that the build is failed with exactly the report which the
     * analysis built: an outdated dependency is a finding of the analysis,
     * not an internal error, so it must not be dressed up as one.
     */
    @Test
    void testReportIsNotWrapped() {
        final String msg = MdaMojoTest.report(
            MdaMojoTest.ERROR_POM_XML, null
        );
        Assertions.assertTrue(
            msg.contains("is outdated: the latest version is"), msg
        );
        Assertions.assertFalse(
            msg.contains(MdaMojoTest.ERROR_PREFIX), msg
        );
    }

    /**
     * Check that a pom.xml which can not be parsed is still reported as a
     * genuine error, together with the exception which caused it.
     */
    @Test
    void testCorruptPom() {
        final MdaMojo mojo = new MdaMojo(
            FailureLevel.ERROR,
            Thread.currentThread().getContextClassLoader()
                .getResource(MdaMojoTest.CORRUPT_POM_XML).getPath()
        );
        final MojoFailureException error = Assertions.assertThrows(
            MojoFailureException.class, mojo::execute
        );
        Assertions.assertTrue(
            error.getMessage().startsWith(MdaMojoTest.ERROR_PREFIX),
            error.getMessage()
        );
        Assertions.assertInstanceOf(
            XmlPullParserException.class, error.getCause()
        );
    }

    /**
     * Check that a pom.xml which can not be parsed respects the failure level
     * as well: with WARNING it is logged and the build goes on.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testCorruptPomWarning() throws Exception {
        new MdaMojo(
            FailureLevel.WARNING,
            Thread.currentThread().getContextClassLoader()
                .getResource(MdaMojoTest.CORRUPT_POM_XML).getPath()
        ).execute();
    }

    /**
     * Check that the prereleases are not reported as upgrades by default: the
     * artifact which is pinned to a release is offered a release, although
     * the newer prereleases exist as well.
     *
     * <p>The assertion is made on the reported version's kind rather than on
     * the version itself, because the report names only the newest version:
     * the moment Maven publishes another release of that artifact, a literal
     * version would be a false failure of this build.
     */
    @Test
    void testPrereleasesIgnoredByDefault() {
        final String line = MdaMojoTest.line(
            MdaMojoTest.analyse(MdaMojoTest.PRERELEASE_POM_XML, false),
            MdaMojoTest.RELEASE_PIN
        );
        Assertions.assertFalse(
            new MdaVersion(MdaMojoTest.latest(line)).prerelease(), line
        );
    }

    /**
     * Check that the prereleases are reported once they are asked for: the
     * artifact which is pinned to a release is behind more versions then than
     * it is by default.
     *
     * <p>The comparison of the two counts holds whatever Maven Central
     * publishes next, because a version which is already published never
     * disappears: the 4.0.0-* prereleases stay newer than the pinned version
     * and stay hidden by default.
     */
    @Test
    void testPrereleasesReportedOnDemand() {
        final int with = MdaMojoTest.behind(
            MdaMojoTest.line(
                MdaMojoTest.analyse(MdaMojoTest.PRERELEASE_POM_XML, true),
                MdaMojoTest.RELEASE_PIN
            )
        );
        final int without = MdaMojoTest.behind(
            MdaMojoTest.line(
                MdaMojoTest.analyse(MdaMojoTest.PRERELEASE_POM_XML, false),
                MdaMojoTest.RELEASE_PIN
            )
        );
        Assertions.assertTrue(
            with > without,
            String.format(
                "%d versions are reported with the prereleases, %d without",
                with, without
            )
        );
    }

    /**
     * Check that an artifact whose own declared version is a prerelease is
     * still offered the newer versions by default. Everything newer than that
     * artifact's version is a prerelease, so without the exception it would
     * silently drop out of the report altogether, and its author is already
     * on the prerelease train anyway.
     */
    @Test
    void testDeclaredPrereleaseIsReported() {
        final String line = MdaMojoTest.line(
            MdaMojoTest.analyse(MdaMojoTest.PRERELEASE_POM_XML, false),
            MdaMojoTest.PRERELEASE_PIN
        );
        Assertions.assertTrue(
            new MdaVersion(MdaMojoTest.latest(line))
                .compareTo(new MdaVersion(MdaMojoTest.declared(line))) > 0,
            line
        );
    }

    /**
     * Check that every outdated artifact is reported with one line which
     * names the newest version instead of listing all of them.
     */
    @Test
    void testOutdatedIsReportedWithOneLine() {
        final String report = MdaMojoTest.report(
            MdaMojoTest.ERROR_POM_XML, null
        );
        final List<String> lines = Arrays.stream(report.split("\\R"))
            .filter(line -> !line.startsWith("Can not analyse "))
            .collect(Collectors.toList());
        Assertions.assertFalse(lines.isEmpty(), report);
        for (final String line : lines) {
            Assertions.assertTrue(
                MdaMojoTest.OUTDATED.matcher(line).matches(), line
            );
        }
    }

    /**
     * Check that the newest version is the one which Maven ranks highest, not
     * the one which the repository happened to return first: the repository
     * answers in the order in which the versions were published.
     */
    @Test
    void testLatestVersionIsReported() {
        Assertions.assertEquals(
            String.format(
                "com.foo:bar (version 1.0) is outdated: the latest version is"
                    + " 10.0 (3 newer versions exist).%n"
            ),
            MdaMojo.message(
                MdaMojoTest.version("1.0"),
                Arrays.asList(
                    MdaMojoTest.version("2.0"),
                    MdaMojoTest.version("10.0"),
                    MdaMojoTest.version("9.5")
                )
            )
        );
    }

    /**
     * Check that an artifact which is behind exactly one version is reported
     * in the singular. It is the most common finding of all, so it must not
     * read like a template which nobody filled in.
     */
    @Test
    void testSingleNewerVersionIsReported() {
        Assertions.assertEquals(
            String.format(
                "com.foo:bar (version 1.0) is outdated: the latest version is"
                    + " 2.0 (1 newer version exists).%n"
            ),
            MdaMojo.message(
                MdaMojoTest.version("1.0"),
                Collections.singletonList(MdaMojoTest.version("2.0"))
            )
        );
    }

    /**
     * Check that one of the report's two sections is ordered by the
     * artifacts' identifiers. The sections are checked apart from each other,
     * because the report lists everything which could not be analysed before
     * everything which is outdated.
     *
     * @param report The whole report.
     * @param failures TRUE - check the lines about the artifacts which could
     *  not be analysed. FALSE - check the lines about the outdated ones.
     */
    private static void assertSorted(
        final String report, final boolean failures
    ) {
        final String marker = "Can not analyse ";
        final List<String> lines = Arrays.stream(report.split("\\R"))
            .filter(line -> line.startsWith(marker) == failures)
            .collect(Collectors.toList());
        Assertions.assertFalse(lines.isEmpty(), report);
        final List<String> sorted = new ArrayList<>(lines);
        Collections.sort(sorted);
        Assertions.assertEquals(sorted, lines, report);
    }

    /**
     * Run the analysis on the given pom file with the ERROR failure level and
     * the given treatment of the prereleases, and return the reported
     * message.
     *
     * @param pom The name of the pom file in the test resources.
     * @param prereleases Must the prerelease versions be reported?
     * @return The reported message.
     */
    private static String analyse(final String pom, final Boolean prereleases) {
        final MdaMojo mojo = new MdaMojo(
            FailureLevel.ERROR,
            Thread.currentThread().getContextClassLoader()
                .getResource(pom).getPath()
        );
        mojo.setPrereleases(prereleases);
        return Assertions.assertThrows(
            MojoFailureException.class, mojo::execute
        ).getMessage();
    }

    /**
     * An artifact's version which is not looked up anywhere, so that the
     * report's wording can be checked without asking the repository.
     *
     * @param name The version's name, e.g. "1.0".
     * @return The version of the "com.foo:bar" artifact.
     */
    private static MvnArtifactVersion version(final String name) {
        return new MavenArtifactVersion(
            new MavenArtifact(new MavenGroup("com.foo"), "bar"),
            name,
            MvnPackagingType.JAR,
            0L
        );
    }

    /**
     * The newest version which the report's line names.
     *
     * @param line The line about an outdated artifact.
     * @return The version.
     */
    private static String latest(final String line) {
        return MdaMojoTest.group(line, "latest");
    }

    /**
     * The version which the pom.xml file declares, as the report's line names
     * it.
     *
     * @param line The line about an outdated artifact.
     * @return The version.
     */
    private static String declared(final String line) {
        return MdaMojoTest.group(line, "declared");
    }

    /**
     * The amount of the versions which the report's line counts.
     *
     * @param line The line about an outdated artifact.
     * @return The amount.
     */
    private static int behind(final String line) {
        return Integer.parseInt(MdaMojoTest.group(line, "behind"));
    }

    /**
     * Read one part of the report's line about an outdated artifact.
     *
     * @param line The line.
     * @param name The name of the part in {@link MdaMojoTest#OUTDATED}.
     * @return The part.
     */
    private static String group(final String line, final String name) {
        final Matcher matcher = MdaMojoTest.OUTDATED.matcher(line);
        Assertions.assertTrue(matcher.matches(), line);
        return matcher.group(name);
    }

    /**
     * Find the report's line about the given artifact.
     *
     * @param report The whole report.
     * @param artifact The artifact's identifier.
     * @return The line about the artifact.
     */
    private static String line(final String report, final String artifact) {
        return Arrays.stream(report.split("\\R"))
            .filter(line -> line.startsWith(artifact))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError(
                    String.format(
                        "Nothing is reported about %s in: %s", artifact, report
                    )
                )
            );
    }

    /**
     * Run the analysis on the given pom file with the ERROR failure level and
     * return the reported message.
     *
     * @param pom The name of the pom file in the test resources.
     * @param threads The amount of the threads. NULL - use the default.
     * @return The reported message.
     */
    private static String report(final String pom, final Integer threads) {
        final MdaMojo mojo = new MdaMojo(
            FailureLevel.ERROR,
            Thread.currentThread().getContextClassLoader()
                .getResource(pom).getPath()
        );
        if (threads != null) {
            mojo.setThreads(threads);
        }
        return Assertions.assertThrows(
            MojoFailureException.class, mojo::execute
        ).getMessage();
    }
}
