package com.sebas3261.ex.infrastructure.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sebas3261.ex.application.errors.DependencyResolverUnavailableException;
import com.sebas3261.ex.application.errors.LookupNotPossibleException;
import com.sebas3261.ex.application.ports.HttpClient;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertPathBuilderException;
import java.util.Map;
import java.util.Properties;
import javax.net.ssl.SSLHandshakeException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.junit.jupiter.api.Test;

class ResolverHttpClientTest {

    /** Mimics Maven's internal HTTP status exceptions, which expose getStatusCode(). */
    static final class StatusException extends IOException {
        private final int status;

        StatusException(int status) {
            super("status code: " + status);
            this.status = status;
        }

        public int getStatusCode() {
            return status;
        }
    }

    private static final class FakeTransporters implements TransporterProvider {
        byte[] body = new byte[0];
        Exception failure;
        boolean notFound;
        RepositorySystemSession session;
        RemoteRepository repository;
        String location;
        int created;

        @Override
        public Transporter newTransporter(RepositorySystemSession s, RemoteRepository r) {
            created++;
            session = s;
            repository = r;
            return new Transporter() {
                @Override
                public int classify(Throwable error) {
                    return notFound ? ERROR_NOT_FOUND : ERROR_OTHER;
                }

                @Override
                public void peek(PeekTask task) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void get(GetTask task) throws Exception {
                    location = task.getLocation().toString();
                    if (failure != null) {
                        throw failure;
                    }
                    try (OutputStream out = task.newOutputStream()) {
                        out.write(body);
                    }
                }

                @Override
                public void put(PutTask task) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void close() {
                }
            };
        }
    }

    private final FakeTransporters transporters = new FakeTransporters();
    private final DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();

    private ResolverHttpClient client(Map<String, String> variables) {
        return new ResolverHttpClient(transporters, session, new ProxyChooser(null, variables, new Properties()),
                1500, "ex-maven-plugin/test");
    }

    private static final URI SEARCH = URI.create("https://search.maven.org/solrsearch/select?q=a%3Alombok&rows=25&wt=json");

    @Test
    void successReturnsTheBodyAndKeepsTheQueryString() {
        transporters.body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);

        HttpClient.HttpResponse response = client(Map.of()).get(SEARCH);

        assertEquals(200, response.statusCode());
        assertEquals("{\"ok\":true}", response.body());
        assertEquals("https://search.maven.org/", transporters.repository.getUrl());
        assertEquals("solrsearch/select?q=a%3Alombok&rows=25&wt=json", transporters.location);
        assertNull(transporters.repository.getProxy());
    }

    @Test
    void perCallSessionCarriesTimeoutUserAgentAndNoRetries() {
        client(Map.of()).get(SEARCH);

        Map<String, Object> config = transporters.session.getConfigProperties();
        assertEquals(1500, config.get("aether.connector.requestTimeout"));
        assertEquals(1500, config.get("aether.connector.connectTimeout"));
        assertEquals("ex-maven-plugin/test", config.get("aether.connector.userAgent"));
        assertEquals(0, config.get("aether.connector.http.retryHandler.count"));
        assertEquals("", config.get("aether.connector.http.retryHandler.serviceUnavailable"));
        assertTrue(session.getConfigProperties().isEmpty(), "the Maven session itself is not modified");
    }

    @Test
    void notFoundIsA404Response() {
        transporters.failure = new StatusException(404);
        transporters.notFound = true;

        assertEquals(404, client(Map.of()).get(SEARCH).statusCode());
    }

    @Test
    void otherHttpStatusesAreResponsesNotExceptions() {
        transporters.failure = new StatusException(503);

        assertEquals(503, client(Map.of()).get(SEARCH).statusCode());
    }

    @Test
    void transportFailuresAreUnavailable() {
        transporters.failure = new IOException("Connect to localhost:1 failed: Connection refused");

        DependencyResolverUnavailableException error = assertThrows(DependencyResolverUnavailableException.class,
                () -> client(Map.of()).get(SEARCH));
        assertEquals("Connect to localhost:1 failed: Connection refused", error.getMessage());
    }

    @Test
    void untrustedCertificateAddsTheHint() {
        SSLHandshakeException handshake = new SSLHandshakeException("PKIX path building failed");
        handshake.initCause(new CertPathBuilderException("unable to find valid certification path"));
        transporters.failure = handshake;

        DependencyResolverUnavailableException error = assertThrows(DependencyResolverUnavailableException.class,
                () -> client(Map.of()).get(SEARCH));
        assertTrue(error.getMessage().startsWith("PKIX path building failed"));
        assertTrue(error.getMessage().endsWith(ResolverHttpClient.CERTIFICATE_HINT));
    }

    @Test
    void offlineModeFailsWithoutAnyRequest() {
        session.setOffline(true);

        LookupNotPossibleException error = assertThrows(LookupNotPossibleException.class,
                () -> client(Map.of()).get(SEARCH));
        assertEquals("Dependency lookup requires network access; it is not available in offline mode (-o).",
                error.getMessage());
        assertEquals(0, transporters.created);
    }

    @Test
    void chosenProxyIsAppliedToTheRepository() {
        client(Map.of("HTTPS_PROXY", "http://proxy.corp:3128")).get(SEARCH);

        assertEquals("proxy.corp", transporters.repository.getProxy().getHost());
        assertEquals(3128, transporters.repository.getProxy().getPort());
    }

    @Test
    void socksProxyFailsBeforeAnyRequest() {
        assertThrows(LookupNotPossibleException.class,
                () -> client(Map.of("HTTPS_PROXY", "socks5h://127.0.0.1:1080")).get(SEARCH));
        assertEquals(0, transporters.created);
    }
}
