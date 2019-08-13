/*
 * Copyright (c) 2019, Istomin Andrei
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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

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
     * Level of the validation.
     */
    @Parameter(property = "level", defaultValue = "WARNING")
    private String level;

    /**
     * The path to the pom.xml file.
     */
    @Parameter(property = "path", defaultValue = "pom.xml")
    private String pom;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final String format = "***** %s *****";
        if ("ERROR".equals(this.level)) {
            getLog().error(String.format(format, this.pom));
        } else if ("WARNING".equals(this.level)) {
            getLog().warn(String.format(format, this.pom));
        } else {
            getLog().info(String.format(format, this.pom));
        }
    }
}
