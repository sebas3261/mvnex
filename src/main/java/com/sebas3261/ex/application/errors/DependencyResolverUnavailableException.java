package com.sebas3261.ex.application.errors;

/** A lookup failed at the transport level (DNS, connect, timeout, TLS, offline mode). */
public class DependencyResolverUnavailableException extends DependencyResolutionException {

    public DependencyResolverUnavailableException(String message) {
        super(message);
    }

    public DependencyResolverUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
