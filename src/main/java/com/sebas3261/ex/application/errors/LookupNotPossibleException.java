package com.sebas3261.ex.application.errors;

/**
 * No lookup can succeed in the current environment (offline mode, unsupported proxy
 * configuration). Unlike a single provider being unavailable, this aborts resolution at once:
 * letting other providers "win" with a not-found answer would hide the real cause.
 */
public class LookupNotPossibleException extends DependencyResolverUnavailableException {

    public LookupNotPossibleException(String message) {
        super(message);
    }
}
