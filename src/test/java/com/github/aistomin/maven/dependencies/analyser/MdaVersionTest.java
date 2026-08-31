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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link MdaVersion}.
 *
 * @since 5.2
 */
final class MdaVersionTest {

    /**
     * Ctor.
     */
    MdaVersionTest() {
    }

    /**
     * Check that all the qualifiers with which the prereleases are published
     * are recognised: the alphas, the betas, the milestones, the release
     * candidates and the snapshots. Both separators are covered, because the
     * qualifier is attached with a dash by most of the projects and with a
     * dot by some of them.
     */
    @Test
    void testPrereleases() {
        final String[] versions = {
            "2.1.0-alpha1", "2.0.0-alpha", "3.0-alpha-1", "4.0.0-alpha-13",
            "2.0.0-beta1", "1.0-beta", "4.0.0-beta-2",
            "6.0.0-M1", "2.0.0-M4", "1.0-milestone-1",
            "4.0.0-rc-6", "5.0.0-RC3", "1.0.0-CR1", "1.0.0-cr1",
            "1.2.3-SNAPSHOT", "1.0-SNAPSHOT",
            "1.0.0.RC1", "7.0.0.Alpha2",
        };
        for (final String version : versions) {
            Assertions.assertTrue(
                new MdaVersion(version).prerelease(), version
            );
        }
    }

    /**
     * Check that the versions which are not prereleases are left alone. The
     * qualifiers which Maven ranks above a plain release are the interesting
     * ones here: a service pack and a release alias come after the release,
     * and a trailing number is a build number rather than a qualifier.
     */
    @Test
    void testReleases() {
        final String[] versions = {
            "2.0.18", "1.0", "1.0.0", "2.0", "0.8.15", "6.1.3", "3.9.12",
            "1.0-sp1", "1.0.0.RELEASE", "1.0.0.Final", "1.0-GA",
            "1.0-1", "1.0.0-1", "20240101",
        };
        for (final String version : versions) {
            Assertions.assertFalse(
                new MdaVersion(version).prerelease(), version
            );
        }
    }

    /**
     * Check that a version which does not begin with a numeric part is not
     * reported as a prerelease. There is nothing to compare such a version
     * against, and a version which we can not parse must not make the
     * analysis hide an upgrade.
     */
    @Test
    void testWithoutNumericPart() {
        final String[] versions = {"master-SNAPSHOT", "RELEASE", "", "alpha"};
        for (final String version : versions) {
            Assertions.assertFalse(
                new MdaVersion(version).prerelease(), version
            );
        }
    }
}
