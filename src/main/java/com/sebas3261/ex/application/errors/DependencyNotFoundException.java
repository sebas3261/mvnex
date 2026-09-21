package com.sebas3261.ex.application.errors;

/** No artifact matches the query. */
public class DependencyNotFoundException extends DependencyResolutionException {

    private final String query;

    public DependencyNotFoundException(String query) {
        super("Dependency not found: " + query);
        this.query = query;
    }

    public String query() {
        return query;
    }
}
