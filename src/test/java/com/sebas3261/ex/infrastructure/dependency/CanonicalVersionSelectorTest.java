package com.sebas3261.ex.infrastructure.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sebas3261.ex.application.errors.DependencyResolutionException;
import com.sebas3261.ex.application.errors.DependencyResolverUnavailableException;
import com.sebas3261.ex.application.errors.LookupNotPossibleException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CanonicalVersionSelectorTest {

    /** Lists fixed versions (in the given order, deliberately unsorted where useful). */
    private record Listing(List<String> versions, RuntimeException failure) implements RepositoryLookup {
        static Listing of(String... versions) {
            return new Listing(List.of(versions), null);
        }

        @Override
        public List<String> listVersions(String groupId, String artifactId) {
            if (failure != null) {
                throw failure;
            }
            return versions;
        }

        @Override
        public boolean pomExists(String groupId, String artifactId, String version) {
            throw new UnsupportedOperationException();
        }
    }

    /** Central's version list for the artifact; records how often it was asked. */
    private static final class Index implements CentralVersions {
        final Set<String> onCentral;
        final RuntimeException failure;
        int calls;

        Index(RuntimeException failure, String... onCentral) {
            this.onCentral = Set.of(onCentral);
            this.failure = failure;
        }

        @Override
        public Set<String> versions(String groupId, String artifactId) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return onCentral;
        }
    }

    private static String select(RepositoryLookup listing, CentralVersions index, String provisional) {
        return new CanonicalVersionSelector(listing, index).select("g", "a", provisional);
    }

    @Test
    void latestMilestoneIsSkipped() {
        assertEquals("6.2.10", select(Listing.of("6.2.9", "6.2.10", "7.0.0-M3"),
                new Index(null, "6.2.9", "6.2.10", "7.0.0-M3"), "7.0.0-M3"));
    }

    @Test
    void mavenOrderingNotDocumentOrder() {
        assertEquals("1.10.0", select(Listing.of("1.8.0", "1.10.0", "1.9.0"),
                new Index(null, "1.8.0", "1.9.0", "1.10.0"), "1.9.0"));
    }

    @Test
    void onlyPreReleasesChoosesTheHighestConfirmed() {
        assertEquals("1.0.0-M2", select(Listing.of("1.0.0-M1", "1.0.0-M2"),
                new Index(null, "1.0.0-M1", "1.0.0-M2"), "1.0.0-M1"));
    }

    @Test
    void mirrorOnlyInternalBuildsAreSkipped() {
        Index index = new Index(null, "1.18.46", "1.18.48");
        assertEquals("1.18.48", select(Listing.of("1.18.46", "1.18.48", "1.18.48-acme.2", "1.18.48-redhat-00001"),
                index, "1.18.48"));
        assertEquals(1, index.calls, "one Central version list per artifact, not a query per version");
    }

    @Test
    void qualifiedPublicVersionsAreKept() {
        assertEquals("33.4.0-jre", select(Listing.of("33.4.0-android", "33.4.0-jre"),
                new Index(null, "33.4.0-android", "33.4.0-jre"), "33.4.0-jre"));
    }

    @Test
    void confirmationFailureFallsBackToHighestStable() {
        assertEquals("1.18.48-redhat-00001", select(Listing.of("1.18.48", "1.18.48-redhat-00001"),
                new Index(new DependencyResolverUnavailableException("timeout")), "x"));
    }

    @Test
    void nothingConfirmedFallsBackToHighestStable() {
        assertEquals("2.0.0", select(Listing.of("2.0.0"), new Index(null), "x"));
    }

    @Test
    void listingFailureKeepsTheProviderVersion() {
        assertEquals("1.18.48", select(new Listing(List.of(), new DependencyResolutionException("down")),
                new Index(null), "1.18.48"));
    }

    @Test
    void emptyListingKeepsTheProviderVersion() {
        assertEquals("1.18.48", select(Listing.of(), new Index(null), "1.18.48"));
    }

    @Test
    void lookupNotPossibleIsNotSwallowed() {
        assertThrows(LookupNotPossibleException.class, () -> select(Listing.of("1.0"),
                new Index(new LookupNotPossibleException("SOCKS")), "1.0"));
        assertThrows(LookupNotPossibleException.class, () -> select(
                new Listing(List.of(), new LookupNotPossibleException("offline")), new Index(null), "1.0"));
    }
}
