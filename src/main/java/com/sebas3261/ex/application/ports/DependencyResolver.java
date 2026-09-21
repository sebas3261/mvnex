package com.sebas3261.ex.application.ports;

import com.sebas3261.ex.domain.dependency.ResolvedDependency;

/**
 * Turns a search term or coordinate into an exact dependency. Implementations throw the
 * {@code com.sebas3261.ex.application.errors} exceptions; an empty {@code version} means
 * "choose a version".
 */
public interface DependencyResolver {

    ResolvedDependency resolveBySearchTerm(String term, String version);

    ResolvedDependency resolveByCoordinate(String groupId, String artifactId, String version);
}
