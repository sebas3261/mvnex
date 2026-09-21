package com.sebas3261.ex.infrastructure.dependency;

import com.sebas3261.ex.application.errors.DependencyNotFoundException;
import com.sebas3261.ex.application.errors.DependencyResolutionException;
import com.sebas3261.ex.application.errors.DependencyResolverUnavailableException;
import com.sebas3261.ex.application.errors.LookupNotPossibleException;
import com.sebas3261.ex.application.errors.MultipleDependencyMatchesException;
import com.sebas3261.ex.application.ports.DependencyResolver;
import com.sebas3261.ex.domain.dependency.ResolvedDependency;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Queries every provider concurrently; the first decisive answer decides which artifact(s) match
 * (design D8). Versions are then chosen canonically, independent of the race winner, and search
 * candidates are ranked by the chosen versions.
 */
public final class CompositeDependencyResolver implements DependencyResolver {

    static final int CANONICAL_LOOKUPS_IN_FLIGHT = 4;

    private final List<SearchProvider> providers;
    private final CanonicalVersionSelector canonical;

    public CompositeDependencyResolver(List<SearchProvider> providers, CanonicalVersionSelector canonical) {
        this.providers = List.copyOf(providers);
        this.canonical = canonical;
    }

    @Override
    public ResolvedDependency resolveBySearchTerm(String term, String version) {
        Win<List<Candidate>> win = race(provider -> provider.searchCandidates(term));

        List<ResolvedDependency> candidates = version.isEmpty()
                ? canonicalVersions(win.value())
                : win.value().stream()
                        .map(c -> new ResolvedDependency(c.groupId(), c.artifactId(), version)).toList();

        List<ResolvedDependency> ranked = CandidateRanking.rank(term, candidates);
        if (ranked.size() > 1) {
            throw new MultipleDependencyMatchesException(term, ranked);
        }

        ResolvedDependency single = ranked.get(0);
        if (!version.isEmpty()) {
            win.provider().verifyVersion(single.groupId(), single.artifactId(), version);
        }
        return single;
    }

    @Override
    public ResolvedDependency resolveByCoordinate(String groupId, String artifactId, String version) {
        if (!version.isEmpty()) {
            race(provider -> {
                provider.verifyVersion(groupId, artifactId, version);
                return Boolean.TRUE;
            });
            return new ResolvedDependency(groupId, artifactId, version);
        }
        Candidate found = race(provider -> provider.coordinate(groupId, artifactId)).value();
        return new ResolvedDependency(groupId, artifactId,
                canonical.select(groupId, artifactId, found.provisionalVersion()));
    }

    private List<ResolvedDependency> canonicalVersions(List<Candidate> candidates) {
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(CANONICAL_LOOKUPS_IN_FLIGHT, candidates.size()), CompositeDependencyResolver::daemon);
        try {
            List<Future<ResolvedDependency>> futures = new ArrayList<>();
            for (Candidate candidate : candidates) {
                futures.add(pool.submit(() -> new ResolvedDependency(candidate.groupId(), candidate.artifactId(),
                        canonical.select(candidate.groupId(), candidate.artifactId(),
                                candidate.provisionalVersion()))));
            }
            List<ResolvedDependency> resolved = new ArrayList<>();
            for (Future<ResolvedDependency> future : futures) {
                resolved.add(await(future));
            }
            return resolved;
        } finally {
            pool.shutdownNow();
        }
    }

    private record Win<T>(SearchProvider provider, T value) {
    }

    private record Attempt<T>(SearchProvider provider, T value, RuntimeException failure) {
    }

    private <T> Win<T> race(Function<SearchProvider, T> call) {
        ExecutorService pool = Executors.newFixedThreadPool(providers.size(), CompositeDependencyResolver::daemon);
        CompletionService<Attempt<T>> completions = new ExecutorCompletionService<>(pool);
        try {
            for (SearchProvider provider : providers) {
                completions.submit(() -> {
                    try {
                        return new Attempt<>(provider, call.apply(provider), null);
                    } catch (RuntimeException e) {
                        return new Attempt<>(provider, null, e);
                    }
                });
            }

            RuntimeException notFound = null;
            RuntimeException unavailable = null;
            RuntimeException other = null;
            for (int i = 0; i < providers.size(); i++) {
                Attempt<T> attempt = await(completions.take());
                if (attempt.failure() == null) {
                    return new Win<>(attempt.provider(), attempt.value());
                }
                RuntimeException failure = attempt.failure();
                if (failure instanceof LookupNotPossibleException) {
                    throw failure;
                } else if (failure instanceof DependencyNotFoundException) {
                    notFound = failure;
                } else if (failure instanceof DependencyResolverUnavailableException) {
                    unavailable = failure;
                } else {
                    other = failure;
                }
            }

            if (notFound != null) {
                throw notFound;
            }
            if (unavailable != null) {
                throw unavailable;
            }
            if (other instanceof DependencyResolutionException) {
                throw other;
            }
            if (other != null) {
                throw new DependencyResolutionException(other.getMessage(), other);
            }
            throw new DependencyResolutionException("Dependency resolver failed.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyResolverUnavailableException("Dependency lookup was interrupted.");
        } finally {
            pool.shutdownNow();
        }
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyResolverUnavailableException("Dependency lookup was interrupted.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new DependencyResolutionException(String.valueOf(e.getCause()), e.getCause());
        }
    }

    private static Thread daemon(Runnable task) {
        Thread thread = new Thread(task, "ex-dependency-lookup");
        thread.setDaemon(true);
        return thread;
    }
}
