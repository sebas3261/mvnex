package com.sebas3261.ex.application.errors;

import com.sebas3261.ex.domain.dependency.ResolvedDependency;
import java.util.List;

/** A search term matched several artifacts; candidates are in ranked order. */
public class MultipleDependencyMatchesException extends DependencyResolutionException {

    /** Index used before the use case knows which requested dependency was ambiguous. */
    public static final int UNKNOWN_INDEX = -1;

    private final String query;
    private final List<ResolvedDependency> candidates;
    private final int dependencyIndex;

    public MultipleDependencyMatchesException(String query, List<ResolvedDependency> candidates) {
        this(query, candidates, UNKNOWN_INDEX);
    }

    public MultipleDependencyMatchesException(String query, List<ResolvedDependency> candidates, int dependencyIndex) {
        super("Multiple dependency matches found: " + query);
        this.query = query;
        this.candidates = List.copyOf(candidates);
        this.dependencyIndex = dependencyIndex;
    }

    public MultipleDependencyMatchesException withDependencyIndex(int index) {
        return new MultipleDependencyMatchesException(query, candidates, index);
    }

    public String query() {
        return query;
    }

    public List<ResolvedDependency> candidates() {
        return candidates;
    }

    public int dependencyIndex() {
        return dependencyIndex;
    }
}
