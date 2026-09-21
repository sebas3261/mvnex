package com.sebas3261.ex.application.add;

import com.sebas3261.ex.application.errors.MultipleDependencyMatchesException;
import com.sebas3261.ex.application.ports.DependencyResolver;
import com.sebas3261.ex.application.ports.MavenProjectValidator;
import com.sebas3261.ex.application.ports.ProjectDependencyRepository;
import com.sebas3261.ex.domain.dependency.DependencyRequest;
import com.sebas3261.ex.domain.dependency.ResolvedDependency;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves every requested dependency, skips ones already declared or repeated in the same
 * request, writes the rest, and validates the project when something was written.
 */
public final class AddUseCase {

    private final DependencyResolver resolver;
    private final ProjectDependencyRepository repository;
    private final MavenProjectValidator validator;

    public AddUseCase(DependencyResolver resolver, ProjectDependencyRepository repository,
            MavenProjectValidator validator) {
        this.resolver = resolver;
        this.repository = repository;
        this.validator = validator;
    }

    public Result execute(List<DependencyRequest> requests) {
        List<ResolvedDependency> resolved = new ArrayList<>();

        for (int index = 0; index < requests.size(); index++) {
            DependencyRequest request = requests.get(index);
            try {
                resolved.add(resolve(request).withScope(request.scope()));
            } catch (MultipleDependencyMatchesException e) {
                throw e.withDependencyIndex(index);
            }
        }

        Set<String> existing = repository.existingDependencyKeys();
        Set<String> seen = new HashSet<>();
        List<ResolvedDependency> toAdd = new ArrayList<>();
        List<ResolvedDependency> skipped = new ArrayList<>();

        for (ResolvedDependency dependency : resolved) {
            if (existing.contains(dependency.key()) || !seen.add(dependency.key())) {
                skipped.add(dependency);
            } else {
                toAdd.add(dependency);
            }
        }

        if (toAdd.isEmpty()) {
            return new Result(toAdd, skipped, Optional.empty());
        }

        repository.addDependencies(toAdd);
        return new Result(toAdd, skipped, Optional.of(validator.validate()));
    }

    private ResolvedDependency resolve(DependencyRequest request) {
        if (request instanceof DependencyRequest.SearchTerm term) {
            return resolver.resolveBySearchTerm(term.query(), "");
        }
        if (request instanceof DependencyRequest.SearchTermWithVersion term) {
            return resolver.resolveBySearchTerm(term.query(), term.version());
        }
        if (request instanceof DependencyRequest.Coordinate coordinate) {
            return resolver.resolveByCoordinate(coordinate.groupId(), coordinate.artifactId(), "");
        }
        DependencyRequest.CoordinateWithVersion coordinate = (DependencyRequest.CoordinateWithVersion) request;
        return resolver.resolveByCoordinate(coordinate.groupId(), coordinate.artifactId(), coordinate.version());
    }

    /**
     * @param added      dependencies written to the POM, in request order
     * @param skipped    dependencies already declared or repeated in this request
     * @param validation present only when something was written
     */
    public record Result(List<ResolvedDependency> added, List<ResolvedDependency> skipped,
            Optional<MavenProjectValidator.Status> validation) {
    }
}
