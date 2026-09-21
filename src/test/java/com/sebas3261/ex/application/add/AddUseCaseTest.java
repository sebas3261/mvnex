package com.sebas3261.ex.application.add;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sebas3261.ex.application.errors.DependencyNotFoundException;
import com.sebas3261.ex.application.errors.MultipleDependencyMatchesException;
import com.sebas3261.ex.application.ports.DependencyResolver;
import com.sebas3261.ex.application.ports.MavenProjectValidator;
import com.sebas3261.ex.application.ports.ProjectDependencyRepository;
import com.sebas3261.ex.domain.dependency.DependencyRequest;
import com.sebas3261.ex.domain.dependency.ResolvedDependency;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AddUseCaseTest {

    private static final ResolvedDependency LOMBOK = new ResolvedDependency("org.projectlombok", "lombok", "1.18.48");
    private static final ResolvedDependency JUPITER =
            new ResolvedDependency("org.junit.jupiter", "junit-jupiter", "6.1.3");

    private final FakeResolver resolver = new FakeResolver();
    private final FakeRepository repository = new FakeRepository();
    private final FakeValidator validator = new FakeValidator();
    private final AddUseCase useCase = new AddUseCase(resolver, repository, validator);

    @Test
    void addsResolvedDependencyAndValidates() {
        resolver.terms.put("lombok", LOMBOK);

        AddUseCase.Result result = useCase.execute(List.of(new DependencyRequest.SearchTerm("lombok", "")));

        assertEquals(List.of(LOMBOK), result.added());
        assertEquals(List.of(List.of(LOMBOK)), repository.writes);
        assertEquals(Optional.of(MavenProjectValidator.Status.PASSED), result.validation());
        assertEquals(1, validator.calls);
    }

    @Test
    void requestedScopeIsAttachedAfterResolution() {
        resolver.terms.put("junit-jupiter", JUPITER.withScope("compile")); // a resolver never supplies a scope; ignored
        AddUseCase.Result result = useCase.execute(List.of(new DependencyRequest.SearchTerm("junit-jupiter", "test")));

        assertEquals("test", result.added().get(0).scope());
    }

    @Test
    void noScopeRequestedMeansNoScope() {
        resolver.terms.put("lombok", LOMBOK);
        AddUseCase.Result result = useCase.execute(List.of(new DependencyRequest.SearchTerm("lombok", "")));

        assertEquals("", result.added().get(0).scope());
    }

    @Test
    void versionsAndCoordinatesArePassedToTheResolver() {
        useCase.execute(List.of(
                new DependencyRequest.SearchTermWithVersion("lombok", "1.18.32", ""),
                new DependencyRequest.Coordinate("org.postgresql", "postgresql", ""),
                new DependencyRequest.CoordinateWithVersion("org.projectlombok", "lombok", "1.18.30", "")));

        assertEquals(List.of("term lombok 1.18.32", "coordinate org.postgresql:postgresql ",
                "coordinate org.projectlombok:lombok 1.18.30"), resolver.calls);
    }

    @Test
    void laterResolutionFailureLeavesThePomUnchanged() {
        resolver.terms.put("lombok", LOMBOK);

        assertThrows(DependencyNotFoundException.class, () -> useCase.execute(List.of(
                new DependencyRequest.SearchTerm("lombok", ""),
                new DependencyRequest.SearchTerm("doesnotexist12345", ""))));

        assertTrue(repository.writes.isEmpty());
        assertEquals(0, validator.calls);
    }

    @Test
    void ambiguityReportsTheIndexOfTheAmbiguousRequest() {
        resolver.terms.put("lombok", LOMBOK);
        resolver.ambiguous.add("guava");

        MultipleDependencyMatchesException error = assertThrows(MultipleDependencyMatchesException.class,
                () -> useCase.execute(List.of(
                        new DependencyRequest.SearchTerm("lombok", ""),
                        new DependencyRequest.SearchTerm("guava", ""))));

        assertEquals(1, error.dependencyIndex());
        assertEquals("guava", error.query());
        assertTrue(repository.writes.isEmpty());
    }

    @Test
    void alreadyDeclaredDependencyIsSkippedWithoutWriting() {
        repository.existing = Set.of("org.projectlombok:lombok");
        resolver.terms.put("lombok", LOMBOK);

        AddUseCase.Result result = useCase.execute(List.of(new DependencyRequest.SearchTerm("lombok", "")));

        assertEquals(List.of(), result.added());
        assertEquals(List.of(LOMBOK), result.skipped());
        assertTrue(repository.writes.isEmpty());
        assertEquals(Optional.empty(), result.validation());
        assertEquals(0, validator.calls);
    }

    @Test
    void duplicateWithinOneInvocationIsSkipped() {
        resolver.terms.put("lombok", LOMBOK);
        resolver.coordinates.put("org.projectlombok:lombok", LOMBOK);

        AddUseCase.Result result = useCase.execute(List.of(
                new DependencyRequest.Coordinate("org.projectlombok", "lombok", ""),
                new DependencyRequest.SearchTerm("lombok", "")));

        assertEquals(List.of(LOMBOK), result.added());
        assertEquals(List.of(LOMBOK), result.skipped());
    }

    @Test
    void versionAndScopeDoNotAffectDuplicateDetection() {
        repository.existing = Set.of("org.projectlombok:lombok");
        resolver.terms.put("lombok", LOMBOK.withVersion("9.9.9"));

        AddUseCase.Result result = useCase.execute(List.of(new DependencyRequest.SearchTerm("lombok", "test")));

        assertEquals(1, result.skipped().size());
    }

    @Test
    void mixedResultAddsInRequestOrderAndValidatesOnce() {
        repository.existing = Set.of("org.projectlombok:lombok");
        resolver.terms.put("lombok", LOMBOK);
        resolver.terms.put("junit-jupiter", JUPITER);
        validator.status = MavenProjectValidator.Status.FAILED;

        AddUseCase.Result result = useCase.execute(List.of(
                new DependencyRequest.SearchTerm("lombok", ""),
                new DependencyRequest.SearchTerm("junit-jupiter", "test")));

        assertEquals(List.of(JUPITER.withScope("test")), result.added());
        assertEquals(List.of(LOMBOK), result.skipped());
        assertEquals(Optional.of(MavenProjectValidator.Status.FAILED), result.validation());
        assertEquals(1, validator.calls);
    }

    private static final class FakeResolver implements DependencyResolver {
        final Map<String, ResolvedDependency> terms = new HashMap<>();
        final Map<String, ResolvedDependency> coordinates = new HashMap<>();
        final List<String> ambiguous = new ArrayList<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public ResolvedDependency resolveBySearchTerm(String term, String version) {
            calls.add("term " + term + " " + version);
            if (ambiguous.contains(term)) {
                throw new MultipleDependencyMatchesException(term, List.of(LOMBOK, JUPITER));
            }
            ResolvedDependency found = terms.get(term);
            if (found == null && version.isEmpty()) {
                throw new DependencyNotFoundException(term);
            }
            return found != null ? found : new ResolvedDependency("g", term, version);
        }

        @Override
        public ResolvedDependency resolveByCoordinate(String groupId, String artifactId, String version) {
            calls.add("coordinate " + groupId + ":" + artifactId + " " + version);
            ResolvedDependency found = coordinates.get(groupId + ":" + artifactId);
            return found != null ? found : new ResolvedDependency(groupId, artifactId, version.isEmpty() ? "1" : version);
        }
    }

    private static final class FakeRepository implements ProjectDependencyRepository {
        Set<String> existing = Set.of();
        final List<List<ResolvedDependency>> writes = new ArrayList<>();

        @Override
        public Set<String> existingDependencyKeys() {
            return existing;
        }

        @Override
        public void addDependencies(List<ResolvedDependency> dependencies) {
            writes.add(List.copyOf(dependencies));
        }
    }

    private static final class FakeValidator implements MavenProjectValidator {
        Status status = Status.PASSED;
        int calls;

        @Override
        public Status validate() {
            calls++;
            return status;
        }
    }
}
