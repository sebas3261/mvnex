package com.sebas3261.ex.application.ports;

import java.net.URI;

/**
 * Minimal HTTP GET. Transport failures (DNS, connect, timeout, TLS) throw
 * {@link com.sebas3261.ex.application.errors.DependencyResolverUnavailableException};
 * HTTP error statuses are returned, not thrown.
 */
public interface HttpClient {

    HttpResponse get(URI uri);

    record HttpResponse(int statusCode, String body) {

        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
