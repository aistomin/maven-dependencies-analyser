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

import com.github.aistomin.maven.browser.MvnArtifactVersion;
import java.io.IOException;
import java.util.List;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * The interface of a project's build file(e. g. pom.xml).
 *
 * @since 0.1
 */
public interface MdaBuildFile {

    /**
     * Parent artifact.
     *
     * @return Parent.
     * @throws IOException If the file is not found or corrupted.
     * @throws XmlPullParserException If file parsing was not successful.
     */
    MvnArtifactVersion parent() throws IOException, XmlPullParserException;

    /**
     * Extract all the project's dependencies.
     *
     * @return The list of the dependencies.
     * @throws IOException If the file is not found or corrupted.
     * @throws XmlPullParserException If file parsing was not successful.
     */
    List<MvnArtifactVersion> dependencies()
        throws IOException, XmlPullParserException;

    /**
     * Extract all the project's plugins.
     *
     * @return The list of the plugins.
     * @throws IOException If the file is not found or corrupted.
     * @throws XmlPullParserException If file parsing was not successful.
     */
    List<MvnArtifactVersion> plugins()
        throws IOException, XmlPullParserException;
}
