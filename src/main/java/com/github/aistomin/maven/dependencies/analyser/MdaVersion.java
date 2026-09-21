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
 * <p>The name is read apart once, when the instance is created: the instance
 * holds its parsed form and its answer instead of deriving them again on
 * every use.
 *
 * @since 5.2
 */
public final class MdaVersion implements Comparable<MdaVersion> {

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
     * The version's name parsed by Maven's own ordering rules. It is what
     * every comparison of the version is made with.
     */
    private final ComparableVersion parsed;

    /**
     * Is the version a prerelease? Decided in the constructor, for the same
     * reason the name is parsed there.
     */
    private final boolean prerelease;

    /**
     * Ctor.
     *
     * @param name The version's name, e.g. "2.1.0-alpha1".
     */
    public MdaVersion(final String name) {
        this.version = name;
        this.parsed = new ComparableVersion(name);
        this.prerelease = MdaVersion.isPrerelease(name, this.parsed);
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
        return this.prerelease;
    }

    /**
     * Compare the version to another one by Maven's own ordering rules, the
     * same rules by which the repository decides which versions are newer
     * than the declared one.
     *
     * <p>That is why the newest of the found versions has to be picked with
     * this comparison instead of with the order in which the repository
     * returned them: the repository answers in the order in which the
     * versions were published, so a maintenance release of an older branch,
     * published after a newer major, would come first and be reported as the
     * latest one.
     *
     * @param other The version we compare to.
     * @return A negative number, zero or a positive number if the version is
     *  respectively older than, equal to or newer than the other one.
     */
    @Override
    public int compareTo(final MdaVersion other) {
        return this.parsed.compareTo(other.parsed);
    }

    /**
     * The equality of two versions is the equality of their names.
     *
     * <p>It is deliberately not the equality of
     * {@link MdaVersion#compareTo(MdaVersion)}: Maven ranks {@code 1.0} and
     * {@code 1.0.0} the same, but they are two different versions to declare
     * in a pom.xml file, and it is the declared one which the analysis
     * reports.
     *
     * @param obj The object we compare to.
     * @return TRUE - the object is a version with the same name. FALSE - it
     *  is not.
     */
    @Override
    public boolean equals(final Object obj) {
        final boolean result;
        if (this == obj) {
            result = true;
        } else if (obj instanceof MdaVersion other) {
            result = this.version.equals(other.version);
        } else {
            result = false;
        }
        return result;
    }

    /**
     * The hash code of the version's name, consistent with
     * {@link MdaVersion#equals(Object)}.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        return this.version.hashCode();
    }

    /**
     * The version's name, the way a pom.xml file declares it.
     *
     * @return The version's name, e.g. "2.1.0-alpha1".
     */
    @Override
    public String toString() {
        return this.version;
    }

    /**
     * Is a version a prerelease? The version is compared against its own
     * numeric part, and it is a prerelease when Maven ranks it below that
     * part.
     *
     * @param name The version's name, e.g. "2.1.0-alpha1".
     * @param comparable The name parsed by Maven's own ordering rules.
     * @return TRUE - the version is a prerelease. FALSE - the version is a
     *  release.
     */
    private static boolean isPrerelease(
        final String name, final ComparableVersion comparable
    ) {
        final Matcher matcher = MdaVersion.NUMERIC.matcher(name);
        final boolean result;
        if (matcher.find() && matcher.end() < name.length()) {
            result = comparable.compareTo(
                new ComparableVersion(matcher.group())
            ) < 0;
        } else {
            result = false;
        }
        return result;
    }
}
