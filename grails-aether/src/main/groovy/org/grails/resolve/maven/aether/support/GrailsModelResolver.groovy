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

package org.grails.resolve.maven.aether.support

import groovy.transform.CompileStatic
import org.apache.maven.model.Repository
import org.apache.maven.model.building.FileModelSource
import org.apache.maven.model.building.ModelSource
import org.apache.maven.model.resolution.ModelResolver
import org.apache.maven.model.resolution.UnresolvableModelException
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.ArtifactResolutionException
import org.eclipse.aether.artifact.DefaultArtifact

/**
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class GrailsModelResolver implements ModelResolver{
    private RepositorySystem  system;
    private RepositorySystemSession session;
    private List<RemoteRepository> repositories = []

    GrailsModelResolver(RepositorySystem  system, RepositorySystemSession session, List<RemoteRepository> repositories) {
        this.system = system
        this.session = session
        this.repositories = repositories
    }

    @Override
    ModelSource resolveModel(String groupId, String artifactId, String version) {
        def pomArtifact = new DefaultArtifact(groupId, artifactId, "", "pom", version)
        try {
            ArtifactRequest request = new ArtifactRequest(pomArtifact, repositories, null)
            pomArtifact = system.resolveArtifact(session, request).getArtifact()

        } catch (ArtifactResolutionException e) {
            throw new UnresolvableModelException("Failed to resolve POM for " + groupId + ":" + artifactId + ":" + version
                + " due to " + e.getMessage(), groupId, artifactId, version, e);
        }

        File pomFile = pomArtifact.file

        return new FileModelSource(pomFile);
    }

    @Override
    void addRepository(Repository repository) {
        repositories << new RemoteRepository.Builder(repository.id, "default",repository.url).build()
    }

    @Override
    ModelResolver newCopy() {
        return new GrailsModelResolver(system, session,repositories)
    }
}
