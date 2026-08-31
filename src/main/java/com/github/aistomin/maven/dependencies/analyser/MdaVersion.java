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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * An artifact's version which knows whether it is a prerelease.
 *
 * <p>The prereleases are recognised by Maven's own ordering rules rather than
 * by a blacklist of the qualifiers: the version is compared against its own
 * numeric part, and it is a prerelease when Maven ranks it below that part.
 * That is what makes {@code 2.1.0-alpha1}, {@code 4.0.0-rc-6},
 * {@code 6.0.0-M1} and {@code 1.0.0.RC1} prereleases, while the qualifiers
 * which Maven ranks above a plain release are not: a service pack
 * ({@code 1.0-sp1}), a release alias ({@code 1.0.0.RELEASE}) or a build
 * number ({@code 1.0-1}). A new qualifier is therefore understood as soon as
 * Maven itself understands it.
 *
 * @since 5.2
 */
public final class MdaVersion {

    /**
     * The numeric part with which a version begins, e.g. "2.1.0" of
     * "2.1.0-alpha1". It is the version without its qualifier, which is what
     * the version is compared against.
     */
    private static final Pattern NUMERIC =
        Pattern.compile("^[0-9]+(\\.[0-9]+)*");

    /**
     * The version's name, e.g. "2.1.0-alpha1".
     */
    private final String version;

    /**
     * Ctor.
     *
     * @param name The version's name, e.g. "2.1.0-alpha1".
     */
    public MdaVersion(final String name) {
        this.version = name;
    }

    /**
     * Is the version a prerelease, i.e. an alpha, a beta, a milestone, a
     * release candidate or a snapshot?
     *
     * <p>A version which does not begin with a numeric part at all, e.g.
     * "master-SNAPSHOT", has nothing to be compared against and is not
     * reported as a prerelease: a version which we can not parse must not
     * make the analysis hide an upgrade.
     *
     * @return TRUE - the version is a prerelease. FALSE - the version is a
     *  release.
     */
    public boolean prerelease() {
        final Matcher matcher = MdaVersion.NUMERIC.matcher(this.version);
        final boolean result;
        if (matcher.find() && matcher.end() < this.version.length()) {
            result = new ComparableVersion(this.version)
                .compareTo(new ComparableVersion(matcher.group())) < 0;
        } else {
            result = false;
        }
        return result;
    }
}
