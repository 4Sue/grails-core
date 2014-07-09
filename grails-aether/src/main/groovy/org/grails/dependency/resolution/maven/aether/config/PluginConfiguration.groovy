/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.dependency.resolution.maven.aether.config

import groovy.transform.CompileStatic

import org.grails.dependency.resolution.maven.aether.AetherDependencyManager
import org.eclipse.aether.graph.Dependency

/**
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class PluginConfiguration extends DependenciesConfiguration {

    PluginConfiguration(AetherDependencyManager dependencyManager) {
        super(dependencyManager)
    }

    @Override
    void addDependency(Dependency dependency, @DelegatesTo(DependencyConfiguration) Closure customizer) {
        super.addDependency(dependency, customizer)
    }

    @Override
    void addBuildDependency(Dependency dependency, @DelegatesTo(DependencyConfiguration) Closure customizer) {
        super.addBuildDependency(dependency, customizer)
    }

    @Override
    void build(String pattern, @DelegatesTo(DependencyConfiguration) Closure customizer) {
        super.build(extractDependencyProperties(pattern), customizer)
    }

    @Override
    void compile(String pattern, @DelegatesTo(DependencyConfiguration) Closure customizer) {
        super.compile(extractDependencyProperties(pattern), customizer)
    }

    @Override
    void runtime(String pattern, @DelegatesTo(DependencyConfiguration) Closure customizer) {
        super.runtime(extractDependencyProperties(pattern), customizer)
    }

    @Override
    void provided(String pattern, @DelegatesTo(DependencyConfiguration) Closure customizer) {
        super.provided(extractDependencyProperties(pattern), customizer)
    }

    @Override
    void optional(String pattern, @DelegatesTo(DependencyConfiguration) Closure customizer) {
        super.optional(extractDependencyProperties(pattern), customizer)
    }

    @Override
    void test(String pattern, @DelegatesTo(DependencyConfiguration) Closure customizer) {
        super.test(extractDependencyProperties(pattern), customizer)
    }

    @Override
    protected String getDefaultExtension() {
        'zip'
    }

    protected String getDefaultGroup() {
        'org.grails.plugins'
    }
}
