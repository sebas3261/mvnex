package com.sebas3261.ex.infrastructure.transport;

import com.sebas3261.ex.application.errors.DependencyResolverUnavailableException;
import com.sebas3261.ex.application.errors.LookupNotPossibleException;
import com.sebas3261.ex.application.ports.HttpClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;

/**
 * GETs arbitrary URLs through Maven's own resolver transport (design D6), so lookups share
 * Maven's proxy authentication and TLS configuration. Each call uses a copy of the session with
 * this client's timeout and User-Agent, and with the transport's 503/429 retries disabled
 * (the C++ tool never retried; Maven's defaults stretch a 503 to about 30 s).
 */
public final class ResolverHttpClient implements HttpClient {

    public static final String OFFLINE_MESSAGE =
            "Dependency lookup requires network access; it is not available in offline mode (-o).";

    public static final String CERTIFICATE_HINT = "The server certificate is not trusted by Java. If your network "
            + "inspects HTTPS traffic, import your organization's root certificate into the JDK truststore, or use "
            + "the operating system trust store by adding -Djavax.net.ssl.trustStoreType=KeychainStore (macOS) or "
            + "-Djavax.net.ssl.trustStoreType=Windows-ROOT (Windows) to .mvn/jvm.config or MAVEN_OPTS.";

    private final TransporterProvider transporters;
    private final RepositorySystemSession baseSession;
    private final ProxyChooser proxies;
    private final int timeoutMillis;
    private final String userAgent;

    public ResolverHttpClient(TransporterProvider transporters, RepositorySystemSession baseSession,
            ProxyChooser proxies, int timeoutMillis, String userAgent) {
        this.transporters = transporters;
        this.baseSession = baseSession;
        this.proxies = proxies;
        this.timeoutMillis = timeoutMillis;
        this.userAgent = userAgent;
    }

    @Override
    public HttpResponse get(URI uri) {
        if (baseSession.isOffline()) {
            throw new LookupNotPossibleException(OFFLINE_MESSAGE);
        }

        String base = uri.getScheme() + "://" + uri.getRawAuthority() + "/";
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String location = (path.startsWith("/") ? path.substring(1) : path)
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());

        RemoteRepository.Builder repository = new RemoteRepository.Builder(uri.getHost(), "default", base);
        proxies.proxyFor(uri).ifPresent(repository::setProxy);

        try (Transporter transporter = transporters.newTransporter(callSession(), repository.build())) {
            GetTask task = new GetTask(URI.create(location));
            try {
                transporter.get(task);
                return new HttpResponse(200, new String(task.getDataBytes(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                if (transporter.classify(e) == Transporter.ERROR_NOT_FOUND) {
                    return new HttpResponse(404, "");
                }
                Integer status = statusCode(e);
                if (status != null) {
                    return new HttpResponse(status, "");
                }
                throw unavailable(e);
            }
        } catch (DependencyResolverUnavailableException e) {
            throw e;
        } catch (Exception e) {
            // No transporter for the URL, or failure while closing it.
            throw unavailable(e);
        }
    }

    private DefaultRepositorySystemSession callSession() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(baseSession);
        session.setConfigProperty("aether.connector.connectTimeout", timeoutMillis);
        session.setConfigProperty("aether.connector.requestTimeout", timeoutMillis);
        session.setConfigProperty("aether.connector.userAgent", userAgent);
        session.setConfigProperty("aether.connector.http.retryHandler.count", 0);
        session.setConfigProperty("aether.connector.http.retryHandler.serviceUnavailable", "");
        return session;
    }

    /**
     * HTTP status of an error response, read reflectively because the exception types are
     * Maven-internal ({@code HttpResponseException} in Resolver 1.9, {@code HttpTransporterException}
     * in 2.x). {@code null} means a transport failure rather than an HTTP response.
     */
    static Integer statusCode(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            try {
                Object code = t.getClass().getMethod("getStatusCode").invoke(t);
                if (code instanceof Integer status) {
                    return status;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // not an HTTP status exception
            }
        }
        return null;
    }

    static DependencyResolverUnavailableException unavailable(Throwable error) {
        String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        if (isUntrustedCertificate(error)) {
            message = message + System.lineSeparator() + CERTIFICATE_HINT;
        }
        return new DependencyResolverUnavailableException(message, error);
    }

    static boolean isUntrustedCertificate(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof CertPathBuilderException || t instanceof CertPathValidatorException
                    || t instanceof CertificateException
                    || (t.getMessage() != null && t.getMessage().contains("PKIX path building failed"))) {
                return true;
            }
        }
        return false;
    }
}
