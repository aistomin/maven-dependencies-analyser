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

import org.apache.maven.plugin.MojoFailureException;
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
     * Check that Mojo file can be created with default ctor.
     *
     * @throws Exception If something goes wrong.
     */
    @Test
    void testCtr() throws Exception {
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
}
