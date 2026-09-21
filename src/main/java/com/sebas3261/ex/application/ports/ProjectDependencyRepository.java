package com.sebas3261.ex.application.ports;

import com.sebas3261.ex.domain.dependency.ResolvedDependency;
import java.util.List;
import java.util.Set;

/** The dependencies declared by the target project's POM. */
public interface ProjectDependencyRepository {

    /** {@code groupId:artifactId} keys already declared at project level. */
    Set<String> existingDependencyKeys();

    void addDependencies(List<ResolvedDependency> dependencies);
}
