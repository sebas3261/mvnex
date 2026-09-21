package com.sebas3261.ex.infrastructure.dependency;

import com.sebas3261.ex.application.errors.DependencyResolutionException;
import com.sebas3261.ex.infrastructure.transport.ProxyChooser;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.version.Version;

/**
 * Repository lookups against Maven Central after Maven applies the user's mirror, proxy and
 * authentication settings, exactly as a normal download would.
 */
public final class ResolverRepositoryLookup implements RepositoryLookup {

    public static final String CENTRAL_URL = "https://repo.maven.apache.org/maven2";

    private final RepositorySystem system;
    private final RepositorySystemSession session;
    private final ProxyChooser proxies;
    private final String centralUrl;

    public ResolverRepositoryLookup(RepositorySystem system, RepositorySystemSession baseSession, ProxyChooser proxies) {
        this(system, baseSession, proxies, CENTRAL_URL);
    }

    /** @param centralUrl URL of the {@code central} repository; only integration tests change it */
    public ResolverRepositoryLookup(RepositorySystem system, RepositorySystemSession baseSession, ProxyChooser proxies,
            String centralUrl) {
        this.system = system;
        this.proxies = proxies;
        this.centralUrl = centralUrl;
        DefaultRepositorySystemSession fresh = new DefaultRepositorySystemSession(baseSession);
        // Never answer from a stale cached maven-metadata.xml.
        fresh.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        this.session = fresh;
    }

    @Override
    public List<String> listVersions(String groupId, String artifactId) {
        VersionRangeRequest request = new VersionRangeRequest(
                new DefaultArtifact(groupId, artifactId, "pom", "[0,)"), repositories(), null);
        try {
            List<String> versions = new ArrayList<>();
            for (Version version : system.resolveVersionRange(session, request).getVersions()) {
                versions.add(version.toString());
            }
            return versions;
        } catch (VersionRangeResolutionException e) {
            throw new DependencyResolutionException("Maven Central metadata request failed.", e);
        }
    }

    @Override
    public boolean pomExists(String groupId, String artifactId, String version) {
        ArtifactRequest request = new ArtifactRequest(
                new DefaultArtifact(groupId, artifactId, "pom", version), repositories(), null);
        try {
            system.resolveArtifact(session, request);
            return true;
        } catch (ArtifactResolutionException e) {
            if (onlyNotFound(e)) {
                return false;
            }
            throw new DependencyResolutionException("Maven Central artifact request failed.", e);
        }
    }

    private static boolean onlyNotFound(ArtifactResolutionException e) {
        for (ArtifactResult result : e.getResults()) {
            for (Exception cause : result.getExceptions()) {
                if (!(cause instanceof ArtifactNotFoundException)) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<RemoteRepository> repositories() {
        RemoteRepository central = new RemoteRepository.Builder("central", "default", centralUrl).build();
        List<RemoteRepository> resolved = new ArrayList<>();
        for (RemoteRepository repository : system.newResolutionRepositories(session, List.of(central))) {
            if (repository.getProxy() != null) {
                resolved.add(repository);
                continue;
            }
            // Maven applied no settings proxy: fall back to environment variables / JVM properties.
            resolved.add(proxies.proxyFor(URI.create(repository.getUrl()))
                    .map(proxy -> new RemoteRepository.Builder(repository).setProxy(proxy).build())
                    .orElse(repository));
        }
        return resolved;
    }
}
