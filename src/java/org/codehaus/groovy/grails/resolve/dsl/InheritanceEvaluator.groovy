/* Copyright 2011 the original author or authors.
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
package org.codehaus.groovy.grails.resolve.dsl

import org.codehaus.groovy.grails.resolve.IvyDependencyManager

import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule

import org.apache.ivy.plugins.matcher.PatternMatcher
import org.apache.ivy.plugins.matcher.ExactPatternMatcher

class InheritanceEvaluator extends AbstractEvaluator {

    InheritanceEvaluator(IvyDependencyManager dependencyManager, String currentPluginBeingConfigured = null, boolean inherited = false) {
        super(dependencyManager, currentPluginBeingConfigured, inherited)
    }

    void excludes(Map exclude) {
        def anyExpression = PatternMatcher.ANY_EXPRESSION
        def mid = ModuleId.newInstance(exclude.group ?: anyExpression, exclude.name.toString())
        def aid = new ArtifactId(mid, anyExpression, anyExpression, anyExpression)

        def excludeRule = new DefaultExcludeRule(aid, ExactPatternMatcher.INSTANCE, null)

        for (String conf in dependencyManager.configurationNames) {
            excludeRule.addConfiguration conf
        }

        dependencyManager.moduleDescriptor.addExcludeRule(excludeRule)
    }

    void excludes(String[] excludeList) {
        for (exclude in excludeList) {
            excludes(name: exclude)
        }
    }

}
