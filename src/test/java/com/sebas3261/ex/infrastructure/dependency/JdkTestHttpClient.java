package com.sebas3261.ex.infrastructure.dependency;

import com.sebas3261.ex.application.errors.DependencyResolverUnavailableException;
import com.sebas3261.ex.application.ports.HttpClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Test-only {@link HttpClient} over the JDK client; production code uses Maven's transport. */
final class JdkTestHttpClient implements HttpClient {

    private final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
    final List<URI> requested = Collections.synchronizedList(new ArrayList<>());

    @Override
    public HttpResponse get(URI uri) {
        requested.add(uri);
        try {
            java.net.http.HttpResponse<String> response =
                    client.send(HttpRequest.newBuilder(uri).GET().build(), BodyHandlers.ofString());
            return new HttpResponse(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new DependencyResolverUnavailableException(e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyResolverUnavailableException("interrupted", e);
        }
    }
}
