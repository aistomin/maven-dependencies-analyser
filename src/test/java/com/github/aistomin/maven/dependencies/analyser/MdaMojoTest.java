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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
     * The prefix with which the genuine errors are reported.
     */
    private static final String ERROR_PREFIX = "Error occurred: ";

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
                "com.github.aistomin:maven-browser (version 1.0) has newer"
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
        Assertions.assertTrue(msg.contains("has newer versions:"), msg);
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
