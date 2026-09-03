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

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;

/**
 * A test resource, such as one of the fixture pom files, addressed by the
 * file system path where the class loader put it.
 *
 * <p>The path is resolved through {@link java.net.URL#toURI()} rather than
 * through {@code getPath()} or {@code getFile()}. Those two return the path
 * of the URL still percent-encoded, so a checkout whose directory contains a
 * space yields "my%20projects" and every test which opens the file fails with
 * a "file not found" that has nothing to do with what is being tested.
 *
 * @since 5.2
 */
final class MdaResource {

    /**
     * The name of the resource on the classpath.
     */
    private final String name;

    /**
     * Ctor.
     *
     * @param resource The name of the resource on the classpath.
     */
    MdaResource(final String resource) {
        this.name = resource;
    }

    /**
     * The resource as a file.
     *
     * @return The file.
     * @throws URISyntaxException If the resource's URL is not a valid URI.
     */
    File file() throws URISyntaxException {
        return Paths.get(
            Thread.currentThread()
                .getContextClassLoader()
                .getResource(this.name)
                .toURI()
        ).toFile();
    }

    /**
     * The path of the resource in the file system.
     *
     * @return The absolute path.
     * @throws URISyntaxException If the resource's URL is not a valid URI.
     */
    String path() throws URISyntaxException {
        return this.file().toString();
    }
}
