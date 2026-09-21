package com.sebas3261.ex.infrastructure.dependency;

import com.sebas3261.ex.domain.dependency.ResolvedDependency;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Candidate ordering carried over from the C++ resolver. */
public final class CandidateRanking {

    private record KnownGroup(String termPrefix, String groupPrefix) {
    }

    private static final List<KnownGroup> KNOWN_GROUPS = List.of(
            new KnownGroup("lombok", "org.projectlombok"),
            new KnownGroup("spring-", "org.springframework"),
            new KnownGroup("spring-boot-", "org.springframework.boot"),
            new KnownGroup("junit", "org.junit.jupiter"),
            new KnownGroup("slf4j-", "org.slf4j"),
            new KnownGroup("logback-", "ch.qos.logback"),
            new KnownGroup("jackson-", "com.fasterxml.jackson"),
            new KnownGroup("postgresql", "org.postgresql"),
            new KnownGroup("mysql", "com.mysql"),
            new KnownGroup("guava", "com.google.guava"));

    private CandidateRanking() {
    }

    /** Stable sort by ascending score; ties keep the provider's order. */
    public static List<ResolvedDependency> rank(String term, List<ResolvedDependency> candidates) {
        List<ResolvedDependency> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingInt(candidate -> score(term, candidate)));
        return ranked;
    }

    public static int score(String term, ResolvedDependency candidate) {
        int score = knownGroupRank(term, candidate.groupId()) * 10;
        if (candidate.groupId().startsWith("io.github.") || candidate.groupId().startsWith("com.github.")) {
            score += 100;
        }
        if (PreRelease.isPreRelease(candidate.version())) {
            score += 50;
        }
        return score;
    }

    private static int knownGroupRank(String term, String groupId) {
        for (int i = 0; i < KNOWN_GROUPS.size(); i++) {
            KnownGroup known = KNOWN_GROUPS.get(i);
            if (term.startsWith(known.termPrefix()) && groupId.startsWith(known.groupPrefix())) {
                return i;
            }
        }
        return 1000;
    }
}
