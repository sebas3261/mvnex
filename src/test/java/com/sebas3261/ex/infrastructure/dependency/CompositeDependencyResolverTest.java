package com.sebas3261.ex.infrastructure.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sebas3261.ex.application.errors.DependencyNotFoundException;
import com.sebas3261.ex.application.errors.DependencyResolutionException;
import com.sebas3261.ex.application.errors.DependencyResolverUnavailableException;
import com.sebas3261.ex.application.errors.LookupNotPossibleException;
import com.sebas3261.ex.application.errors.MultipleDependencyMatchesException;
import com.sebas3261.ex.domain.dependency.ResolvedDependency;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class CompositeDependencyResolverTest {

    /** A provider that answers after a delay; each call is recorded. */
    private static final class Fake implements SearchProvider {
        final String name;
        final long delayMillis;
        final Supplier<List<Candidate>> search;
        final Supplier<Candidate> coordinate;
        final Runnable verify;
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());

        Fake(String name, long delayMillis, Supplier<List<Candidate>> search, Supplier<Candidate> coordinate,
                Runnable verify) {
            this.name = name;
            this.delayMillis = delayMillis;
            this.search = search;
            this.coordinate = coordinate;
            this.verify = verify;
        }

        private void pause() {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DependencyResolverUnavailableException("interrupted");
            }
        }

        @Override
        public List<Candidate> searchCandidates(String term) {
            calls.add("search " + term);
            pause();
            return search.get();
        }

        @Override
        public Candidate coordinate(String groupId, String artifactId) {
            calls.add("coordinate");
            pause();
            return coordinate.get();
        }

        @Override
        public void verifyVersion(String groupId, String artifactId, String version) {
            calls.add("verify " + version);
            pause();
            verify.run();
        }
    }

    private static Supplier<List<Candidate>> candidates(Candidate... candidates) {
        return () -> List.of(candidates);
    }

    private static <T> Supplier<T> failing(RuntimeException error) {
        return () -> {
            throw error;
        };
    }

    private static final Runnable OK = () -> { };

    /** Canonical selection that reports "canonical-<provisional>" to make its use visible. */
    private static CanonicalVersionSelector canonical() {
        RepositoryLookup unavailable = new RepositoryLookup() {
            @Override
            public List<String> listVersions(String groupId, String artifactId) {
                throw new DependencyResolutionException("listing unavailable");
            }

            @Override
            public boolean pomExists(String groupId, String artifactId, String version) {
                return true;
            }
        };
        return new CanonicalVersionSelector(unavailable, (g, a) -> Set.of());
    }

    /** Canonical selection backed by a fixed listing, so every provider ends up with the same answer. */
    private static CanonicalVersionSelector canonicalListing(String... versions) {
        RepositoryLookup listing = new RepositoryLookup() {
            @Override
            public List<String> listVersions(String groupId, String artifactId) {
                return List.of(versions);
            }

            @Override
            public boolean pomExists(String groupId, String artifactId, String version) {
                return true;
            }
        };
        return new CanonicalVersionSelector(listing, (g, a) -> Set.of(versions));
    }

    private static final Candidate LOMBOK = new Candidate("org.projectlombok", "lombok", "1.18.48");
    private static final Candidate VALUYA = new Candidate("io.github.valuya", "lombok", "1.18.46.4");

    @Test
    void fastProviderDecidesTheCandidateSet() {
        Fake slow = new Fake("slow", 400, candidates(LOMBOK, VALUYA, new Candidate("x.y", "lombok", "1")), null, OK);
        Fake fast = new Fake("fast", 0, candidates(VALUYA, LOMBOK), null, OK);
        CompositeDependencyResolver resolver = new CompositeDependencyResolver(List.of(slow, fast), canonical());

        MultipleDependencyMatchesException error = assertThrows(MultipleDependencyMatchesException.class,
                () -> resolver.resolveBySearchTerm("lombok", ""));

        assertEquals(2, error.candidates().size());
        assertEquals("org.projectlombok", error.candidates().get(0).groupId(), "ranked, not provider order");
    }

    @Test
    void singleCandidateUsesTheCanonicalVersion() {
        Fake provider = new Fake("p", 0, candidates(new Candidate("org.springframework", "spring-core", "7.0.0-M3")),
                null, OK);
        ResolvedDependency resolved = new CompositeDependencyResolver(List.of(provider),
                canonicalListing("6.2.10", "7.0.0-M3")).resolveBySearchTerm("spring-core", "");

        assertEquals("6.2.10", resolved.version());
    }

    @Test
    void requestedVersionIsVerifiedByTheWinningProvider() {
        Fake winner = new Fake("winner", 0, candidates(new Candidate("org.postgresql", "postgresql", "42")), null,
                failing(new DependencyNotFoundException("org.postgresql:postgresql:0.0.1"))::get);
        Fake loser = new Fake("loser", 300, candidates(), null, OK);

        DependencyNotFoundException error = assertThrows(DependencyNotFoundException.class,
                () -> new CompositeDependencyResolver(List.of(loser, winner), canonical())
                        .resolveBySearchTerm("postgresql", "0.0.1"));

        assertEquals("Dependency not found: org.postgresql:postgresql:0.0.1", error.getMessage());
        assertTrue(winner.calls.contains("verify 0.0.1"));
    }

    @Test
    void requestedVersionAppliesToEveryCandidateWithoutCanonicalLookups() {
        Fake provider = new Fake("p", 0, candidates(LOMBOK, VALUYA), null, OK);
        MultipleDependencyMatchesException error = assertThrows(MultipleDependencyMatchesException.class,
                () -> new CompositeDependencyResolver(List.of(provider), canonical())
                        .resolveBySearchTerm("lombok", "1.18.32"));

        assertTrue(error.candidates().stream().allMatch(c -> c.version().equals("1.18.32")));
    }

    @Test
    void versionIsIndependentOfTheRaceWinner() {
        Candidate fromSolr = new Candidate("org.x", "y", "2.0.0-M1");
        Candidate fromDepsDev = new Candidate("org.x", "y", "1.9.0");
        for (int run = 0; run < 2; run++) {
            Fake solr = new Fake("solr", run == 0 ? 0 : 200, null, () -> fromSolr, OK);
            Fake depsDev = new Fake("deps.dev", run == 0 ? 200 : 0, null, () -> fromDepsDev, OK);
            ResolvedDependency resolved = new CompositeDependencyResolver(List.of(solr, depsDev),
                    canonicalListing("1.9.0", "1.9.1", "2.0.0-M1")).resolveByCoordinate("org.x", "y", "");
            assertEquals("1.9.1", resolved.version(), "run " + run);
        }
    }

    @Test
    void searchTermAndCoordinateGetTheSameVersion() {
        CanonicalVersionSelector canonical = canonicalListing("1.18.30", "1.18.48", "1.18.50-rc1");
        Fake solr = new Fake("solr", 0, candidates(new Candidate("org.projectlombok", "lombok", "1.18.38")),
                () -> new Candidate("org.projectlombok", "lombok", "1.18.38"), OK);

        String byTerm = new CompositeDependencyResolver(List.of(solr), canonical).resolveBySearchTerm("lombok", "").version();
        String byCoordinate = new CompositeDependencyResolver(List.of(solr), canonical)
                .resolveByCoordinate("org.projectlombok", "lombok", "").version();

        assertEquals("1.18.48", byTerm);
        assertEquals(byTerm, byCoordinate);
    }

    @Test
    void coordinateWithVersionRacesTheExistenceCheck() {
        Fake missing = new Fake("a", 0, null, null,
                failing(new DependencyNotFoundException("org.projectlombok:lombok:1.18.32"))::get);
        Fake present = new Fake("b", 100, null, null, OK);

        ResolvedDependency resolved = new CompositeDependencyResolver(List.of(missing, present), canonical())
                .resolveByCoordinate("org.projectlombok", "lombok", "1.18.32");

        assertEquals("org.projectlombok:lombok:1.18.32", resolved.coordinates());
    }

    @Test
    void notFoundTakesPrecedenceOverUnavailable() {
        Fake unavailable = new Fake("u", 0, failing(new DependencyResolverUnavailableException("timeout")), null, OK);
        Fake notFound = new Fake("n", 50, failing(new DependencyNotFoundException("zzz")), null, OK);
        Fake other = new Fake("o", 100, failing(new DependencyResolutionException("Maven Central request failed.")),
                null, OK);

        DependencyNotFoundException error = assertThrows(DependencyNotFoundException.class,
                () -> new CompositeDependencyResolver(List.of(unavailable, notFound, other), canonical())
                        .resolveBySearchTerm("zzz", ""));
        assertEquals("Dependency not found: zzz", error.getMessage());
    }

    @Test
    void unavailableTakesPrecedenceOverOtherErrors() {
        Fake other = new Fake("o", 0, failing(new DependencyResolutionException("Maven Central request failed.")),
                null, OK);
        Fake unavailable = new Fake("u", 50, failing(new DependencyResolverUnavailableException("timeout")), null, OK);

        DependencyResolverUnavailableException error = assertThrows(DependencyResolverUnavailableException.class,
                () -> new CompositeDependencyResolver(List.of(other, unavailable), canonical())
                        .resolveBySearchTerm("x", ""));
        assertEquals("timeout", error.getMessage());
    }

    @Test
    void otherErrorsSurfaceLast() {
        Fake other = new Fake("o", 0, failing(new DependencyResolutionException("deps.dev request failed.")),
                null, OK);
        DependencyResolutionException error = assertThrows(DependencyResolutionException.class,
                () -> new CompositeDependencyResolver(List.of(other), canonical()).resolveBySearchTerm("x", ""));
        assertEquals("deps.dev request failed.", error.getMessage());
    }

    @Test
    void lookupNotPossibleAbortsEvenIfAnotherProviderSaysNotFound() {
        // deps.dev answers "not found" for terms without touching the network; it must not mask SOCKS/offline.
        Fake depsDev = new Fake("deps.dev", 0, failing(new DependencyNotFoundException("lombok")), null, OK);
        Fake socks = new Fake("solr", 50, failing(new LookupNotPossibleException("SOCKS proxy ...")), null, OK);

        LookupNotPossibleException error = assertThrows(LookupNotPossibleException.class,
                () -> new CompositeDependencyResolver(List.of(depsDev, socks), canonical())
                        .resolveBySearchTerm("lombok", ""));
        assertEquals("SOCKS proxy ...", error.getMessage());
    }

    @Test
    void canonicalLookupsRunConcurrentlyButBounded() {
        List<Candidate> many = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            many.add(new Candidate("g" + i, "lombok", "1.0"));
        }
        int[] inFlight = {0};
        int[] maxInFlight = {0};
        RepositoryLookup slowListing = new RepositoryLookup() {
            @Override
            public List<String> listVersions(String groupId, String artifactId) {
                synchronized (inFlight) {
                    inFlight[0]++;
                    maxInFlight[0] = Math.max(maxInFlight[0], inFlight[0]);
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (inFlight) {
                    inFlight[0]--;
                }
                return List.of("1.0");
            }

            @Override
            public boolean pomExists(String groupId, String artifactId, String version) {
                return true;
            }
        };
        Fake provider = new Fake("p", 0, () -> many, null, OK);

        MultipleDependencyMatchesException error = assertThrows(MultipleDependencyMatchesException.class,
                () -> new CompositeDependencyResolver(List.of(provider),
                        new CanonicalVersionSelector(slowListing, (g, a) -> Set.of("1.0"))).resolveBySearchTerm("lombok", ""));

        assertEquals(13, error.candidates().size());
        assertTrue(maxInFlight[0] > 1 && maxInFlight[0] <= CompositeDependencyResolver.CANONICAL_LOOKUPS_IN_FLIGHT,
                "max in flight was " + maxInFlight[0]);
    }
}
