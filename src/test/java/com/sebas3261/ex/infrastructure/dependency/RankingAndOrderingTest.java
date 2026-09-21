package com.sebas3261.ex.infrastructure.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sebas3261.ex.domain.dependency.ResolvedDependency;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RankingAndOrderingTest {

    private static ResolvedDependency dep(String groupId, String artifactId, String version) {
        return new ResolvedDependency(groupId, artifactId, version);
    }

    // ---- pre-release markers ----

    @ParameterizedTest
    @ValueSource(strings = {"6.0.0-M1", "2.0-rc1", "1.0.0-SNAPSHOT", "3.1-beta", "1.0-alpha-2", "7.0.0-m3"})
    void preReleases(String version) {
        assertTrue(PreRelease.isPreRelease(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.18.48", "33.4.0-jre", "33.4.0-android", "1.0.Final", "2.0.RC1"})
    void notPreReleases(String version) {
        // "2.0.RC1" has no hyphen before the marker, so the preserved C++ substring rule doesn't match it.
        assertFalse(PreRelease.isPreRelease(version));
    }

    @Test
    void preservedQuirkDashMMatchesAnywhere() {
        assertTrue(PreRelease.isPreRelease("1.0-mysql"));
    }

    // ---- ranking ----

    @Test
    void knownGroupFirst() {
        List<ResolvedDependency> ranked = CandidateRanking.rank("lombok", List.of(
                dep("io.github.valuya", "lombok", "1.18.46.4"),
                dep("org.projectlombok", "lombok", "1.18.48")));
        assertEquals("org.projectlombok", ranked.get(0).groupId());
    }

    @ParameterizedTest
    @CsvSource({
            "lombok, org.projectlombok, 1.0, 0",
            "spring-core, org.springframework, 1.0, 10",
            "spring-boot-starter, org.springframework.boot, 1.0, 10",
            "junit-jupiter, org.junit.jupiter, 1.0, 30",
            "guava, com.google.guava, 1.0, 90",
            "lombok, io.github.valuya, 1.0, 10100",
            "lombok, com.github.x, 1.0-rc1, 10150",
            "lombok, org.other, 1.0-M1, 10050"})
    void scores(String term, String groupId, String version, int expected) {
        assertEquals(expected, CandidateRanking.score(term, dep(groupId, term, version)));
    }

    @Test
    void tiesKeepProviderOrder() {
        List<ResolvedDependency> input = List.of(dep("a.one", "x", "1"), dep("b.two", "x", "1"), dep("c.three", "x", "1"));
        assertEquals(input, CandidateRanking.rank("x", input));
    }

    @Test
    void preReleasePenaltyAppliesToTheChosenVersion() {
        List<ResolvedDependency> ranked = CandidateRanking.rank("x", List.of(
                dep("a.one", "x", "2.0-rc1"), dep("b.two", "x", "1.0")));
        assertEquals("b.two", ranked.get(0).groupId());
    }

    // ---- version ordering ----

    @Test
    void ordersByMavenRulesNotDocumentOrder() {
        assertEquals(List.of("1.8.0", "1.9.0", "1.10.0"), VersionOrdering.ascending(List.of("1.8.0", "1.10.0", "1.9.0")));
    }

    @Test
    void highestPreferringStable() {
        assertEquals("33.4.0-jre", VersionOrdering.highestPreferringStable(List.of("33.4.0-android", "33.4.0-jre")));
        assertEquals("1.18.32", VersionOrdering.highestPreferringStable(
                List.of("0.10.0", "1.18.30", "1.18.32", "1.18.34-rc1")));
        assertEquals("1.0.0-M2", VersionOrdering.highestPreferringStable(List.of("1.0.0-M1", "1.0.0-M2")));
    }

    @Test
    void internalBuildsRankAboveTheRelease() {
        // Why canonical selection must confirm versions on Central (design D8).
        assertEquals("1.18.48-redhat-00001", VersionOrdering.highestPreferringStable(
                List.of("1.18.48", "1.18.48-acme.2", "1.18.48-redhat-00001")));
    }
}
